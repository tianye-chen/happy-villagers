package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

/**
 * Flood fills the air around a villager to find its enclosed living space ("Home").
 * <p>
 * Rules: the fill moves through blocks without (significant) collision, never through doors, may only step
 * sideways into a spot that is part of an opening at least 2 blocks tall, never goes below the villager's feet
 * and never higher than {@code maxHeight} blocks. If it reaches {@code maxVolume} the space is considered open.
 */
public final class HomeScanner {
    public static final TagKey<Block> GREENERY = TagKey.create(Registries.BLOCK, HappyVillagers.id("greenery"));

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int ROOF_SEARCH_ABOVE_CAP = 8;
    private static final int DOOR_SKY_SEARCH_LIMIT = 96;

    private HomeScanner() {}

    public record Result(boolean enclosed, int volume, boolean roof, boolean bed, int doors, int windows,
                         int greenery, boolean skyAccess) {}

    /**
     * Finds the villager's home even while it is out and about. Tries, in order:
     * <ol>
     *   <li>its claimed bed (vanilla {@link MemoryModuleType#HOME} memory),</li>
     *   <li>where it is standing, if that is enclosed (remembered as its home),</li>
     *   <li>the last enclosed spot it was seen in, forgotten once that space no longer closes.</li>
     * </ol>
     * Only when all of these fail is the villager homeless.
     */
    public static Result findHome(ServerLevel level, Villager villager, HappinessData data) {
        Optional<BlockPos> bed = claimedBed(level, villager);
        if (bed.isPresent()) {
            Result fromBed = scan(level, villager, bed.get());
            if (fromBed.enclosed()) {
                return fromBed;
            }
        }

        BlockPos here = villager.blockPosition();
        Result fromHere = scan(level, villager, here);
        if (fromHere.enclosed()) {
            data.setHomeAnchor(GlobalPos.of(level.dimension(), here));
            return fromHere;
        }

        Optional<GlobalPos> remembered = data.homeAnchor();
        if (remembered.isPresent() && remembered.get().dimension() == level.dimension()) {
            BlockPos anchor = remembered.get().pos();
            if (!level.isLoaded(anchor)) {
                return fromHere; // can't check right now; keep the memory
            }
            Result fromAnchor = scan(level, villager, anchor);
            if (fromAnchor.enclosed()) {
                return fromAnchor;
            }
            data.setHomeAnchor(null);
        }
        return fromHere;
    }

    /** Flood fills from the villager's current position. */
    public static Result scan(ServerLevel level, Villager villager) {
        return scan(level, villager, villager.blockPosition());
    }

    /** Flood fills from {@code feet}, which sets the floor level and the height cap. */
    public static Result scan(ServerLevel level, Villager villager, BlockPos feet) {
        int maxVolume = HappyConfig.MAX_VOLUME.get();
        int minY = feet.getY();
        int maxY = minY + HappyConfig.MAX_HEIGHT.get() - 1;

        BlockPos start = feet;
        // A sleeping villager's feet (or a bed anchor) are inside the bed; start from the air above instead.
        if (!isPassable(level, start) && isPassable(level, start.above())) {
            start = start.above();
        }

        LongOpenHashSet interior = new LongOpenHashSet();
        LongOpenHashSet boundary = new LongOpenHashSet();
        LongOpenHashSet doors = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        interior.add(start.asLong());
        queue.add(start);
        boolean open = false;

        fill:
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (next.getY() < minY || next.getY() > maxY) {
                    continue;
                }
                long key = next.asLong();
                if (interior.contains(key) || boundary.contains(key)) {
                    continue;
                }
                if (!level.isLoaded(next)) {
                    // Unknown territory: assume the space keeps going.
                    open = true;
                    break fill;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof DoorBlock) {
                    boundary.add(key);
                    doors.add(doorBase(next, state).asLong());
                    continue;
                }
                if (!isPassable(level, next, state)) {
                    boundary.add(key);
                    continue;
                }
                // Gaps less than 2 blocks tall do not let the fill through sideways.
                if (dir.getAxis().isHorizontal() && !isPassable(level, next.above()) && !isPassable(level, next.below())) {
                    continue;
                }
                if (interior.size() >= maxVolume) {
                    open = true;
                    break fill;
                }
                interior.add(key);
                queue.add(next);
            }
        }

        boolean skyAccess = seesSky(level, interior) || anyDoorLeadsToSky(level, doors, interior);
        if (open) {
            return new Result(false, interior.size(), false, false, 0, 0, 0, skyAccess);
        }

        return new Result(true, interior.size(),
                hasRoof(level, interior, maxY),
                bedInside(level, villager, interior),
                doors.size(),
                countWindows(level, boundary),
                countGreenery(level, interior, boundary, minY, maxY),
                skyAccess);
    }

    // ------------------------------------------------------------------ passability

    static boolean isPassable(ServerLevel level, BlockPos pos) {
        return isPassable(level, pos, level.getBlockState(pos));
    }

    /** Air-like: no collision, or only a low sliver (carpet, bottom slab, snow layer...). Doors never count. */
    static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5;
    }

    private static BlockPos doorBase(BlockPos pos, BlockState state) {
        return state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    // ------------------------------------------------------------------ home attributes

    /** Roofed when (almost) every column of the home has a solid block somewhere above it. */
    private static boolean hasRoof(ServerLevel level, LongSet interior, int maxY) {
        Long2IntOpenHashMap columnTops = new Long2IntOpenHashMap();
        for (long packed : interior) {
            int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
            long column = BlockPos.asLong(x, 0, z);
            if (!columnTops.containsKey(column) || columnTops.get(column) < y) {
                columnTops.put(column, y);
            }
        }
        int covered = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (var entry : columnTops.long2IntEntrySet()) {
            int x = BlockPos.getX(entry.getLongKey()), z = BlockPos.getZ(entry.getLongKey());
            int top = entry.getIntValue();
            int limit = top < maxY ? top + 1 : maxY + ROOF_SEARCH_ABOVE_CAP;
            for (int y = top + 1; y <= limit; y++) {
                if (!isPassable(level, cursor.set(x, y, z))) {
                    covered++;
                    break;
                }
            }
        }
        return !columnTops.isEmpty() && covered >= columnTops.size() * HappyConfig.ROOF_COVERAGE.get();
    }

    /** The bed this villager has claimed through the vanilla {@link MemoryModuleType#HOME} memory, if it still exists. */
    private static Optional<BlockPos> claimedBed(ServerLevel level, Villager villager) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty() || home.get().dimension() != level.dimension() || !level.isLoaded(home.get().pos())) {
            return Optional.empty();
        }
        BlockPos bedPos = home.get().pos();
        return level.getBlockState(bedPos).getBlock() instanceof BedBlock ? Optional.of(bedPos) : Optional.empty();
    }

    /** The claimed bed must touch the home's air. */
    private static boolean bedInside(ServerLevel level, Villager villager, LongSet interior) {
        Optional<BlockPos> bed = claimedBed(level, villager);
        if (bed.isEmpty()) {
            return false;
        }
        BlockPos bedPos = bed.get();
        BlockPos otherHalf = bedPos.relative(BedBlock.getConnectedDirection(level.getBlockState(bedPos)));
        return touches(interior, bedPos) || touches(interior, otherHalf);
    }

    private static boolean touches(LongSet interior, BlockPos pos) {
        if (interior.contains(pos.asLong())) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (interior.contains(pos.relative(dir).asLong())) {
                return true;
            }
        }
        return false;
    }

    private static int countWindows(ServerLevel level, LongSet boundary) {
        int windows = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (long packed : boundary) {
            BlockState state = level.getBlockState(cursor.set(packed));
            if (state.is(Tags.Blocks.GLASS_BLOCKS) || state.is(Tags.Blocks.GLASS_PANES)) {
                windows++;
            }
        }
        return windows;
    }

    /**
     * Greenery counts when it decorates the home rather than forming its shell: plants standing in the home's air,
     * or solid greenery (potted plants, azaleas, leaves...) that rests on something, doesn't touch the outside and
     * isn't part of the floor or ceiling.
     */
    private static int countGreenery(ServerLevel level, LongSet interior, LongSet boundary, int minY, int maxY) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (long packed : interior) {
            if (isGreenery(level.getBlockState(cursor.set(packed)))) {
                count++;
            }
        }
        for (long packed : boundary) {
            cursor.set(packed);
            if (cursor.getY() < minY || cursor.getY() > maxY || !isGreenery(level.getBlockState(cursor))) {
                continue;
            }
            BlockPos pos = cursor.immutable();
            if (interior.contains(pos.below().asLong())) {
                continue; // hangs from above: part of the ceiling
            }
            if (!touchesOutside(level, pos, interior)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isGreenery(BlockState state) {
        return state.is(GREENERY) && !state.is(Blocks.FLOWER_POT);
    }

    private static boolean touchesOutside(ServerLevel level, BlockPos pos, LongSet interior) {
        for (Direction dir : HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (!interior.contains(side.asLong()) && isPassable(level, side)) {
                return true;
            }
        }
        BlockPos up = pos.above();
        return !interior.contains(up.asLong()) && isPassable(level, up);
    }

    // ------------------------------------------------------------------ sky access

    private static boolean seesSky(ServerLevel level, LongSet interior) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (long packed : interior) {
            if (isUnderOpenSky(level, cursor.set(packed))) {
                return true;
            }
        }
        return false;
    }

    /** Walks out of each door (2-tall openings only) looking for a spot under the open sky. */
    private static boolean anyDoorLeadsToSky(ServerLevel level, LongSet doors, LongSet interior) {
        for (long packed : doors) {
            BlockPos door = BlockPos.of(packed);
            LongOpenHashSet visited = new LongOpenHashSet();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            for (Direction dir : HORIZONTAL) {
                BlockPos outside = door.relative(dir);
                if (!interior.contains(outside.asLong()) && isWalkable(level, outside)) {
                    visited.add(outside.asLong());
                    queue.add(outside);
                }
            }
            while (!queue.isEmpty() && visited.size() < DOOR_SKY_SEARCH_LIMIT) {
                BlockPos pos = queue.poll();
                if (isUnderOpenSky(level, pos)) {
                    return true;
                }
                for (Direction dir : HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos next = pos.relative(dir).above(dy);
                        if (!interior.contains(next.asLong()) && level.isLoaded(next) && isWalkable(level, next)
                                && visited.add(next.asLong())) {
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Nothing solid overhead (leaves don't count, glass does). Uses the heightmap rather than sky light so the
     * answer is exact even right after blocks change.
     */
    private static boolean isUnderOpenSky(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    /** A 2-tall air space. */
    private static boolean isWalkable(ServerLevel level, BlockPos pos) {
        return isPassable(level, pos) && isPassable(level, pos.above());
    }
}
