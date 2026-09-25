package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Flood fills the air around a villager to find its enclosed living space ("Home").
 * <p>
 * Rules: the fill moves through blocks without (significant) collision, never through doors, may only step
 * sideways into a spot that is part of an opening at least 2 blocks tall, never goes below the villager's feet
 * and never higher than {@code maxHeight} blocks. If it reaches {@code maxVolume} the space is considered open.
 * <p>
 * Positions are handled as packed longs, block reads go through a per-evaluation chunk cache ({@link Context}),
 * and passability is cached per block state.
 */
public final class HomeScanner {
    public static final TagKey<Block> GREENERY = TagKey.create(Registries.BLOCK, HappyVillagers.id("greenery"));

    /** {@link Direction#values()} copies its array on every call; the fill runs it for every block. */
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int ROOF_SEARCH_ABOVE_CAP = 8;
    private static final int DOOR_SKY_SEARCH_LIMIT = 96;

    private static final byte PASSABLE = 1;
    private static final byte SOLID = 2;
    /** Passability of block states whose collision shape doesn't depend on position (almost all of them). */
    private static final Reference2ByteOpenHashMap<BlockState> STATE_PASSABILITY = new Reference2ByteOpenHashMap<>();

    private HomeScanner() {}

    /**
     * @param interior  the air blocks the fill reached (the home when enclosed; a partial open area otherwise)
     * @param boundary  the solid blocks around them
     * @param residents villagers living here, including the scanned one (at least 1)
     * @param floorY    the Y level the fill started from; it bounds the fill vertically
     * @param states    reads block states through the scan's chunk cache, for follow-up counting
     */
    public record Result(boolean enclosed, int volume, boolean roof, boolean bed, int doors, int windows,
                         int greenery, boolean skyAccess, int residents, LongSet interior, LongSet boundary,
                         int floorY, Long2ObjectFunction<BlockState> states) {

        /** Whether a fill started at {@code feet} would explore this same (open) space. */
        boolean covers(BlockPos feet) {
            return feet.getY() == floorY && (interior.contains(feet.asLong()) || interior.contains(feet.above().asLong()));
        }
    }

    /**
     * Finds the villager's home even while it is out and about. Tries, in order:
     * <ol>
     *   <li>its claimed bed (vanilla {@link MemoryModuleType#HOME} memory),</li>
     *   <li>where it is standing, if that is enclosed (remembered as its home),</li>
     *   <li>the last enclosed spot it was seen in, forgotten once that space no longer closes.</li>
     * </ol>
     * Only when all of these fail is the villager homeless. A start point that lies inside an open space an earlier
     * fill already explored is not filled again: it is part of that same open space.
     */
    public static Result findHome(ServerLevel level, Villager villager, HappinessData data) {
        Context ctx = new Context(level);
        Result fromBed = null;
        Optional<BlockPos> bed = claimedBed(level, villager);
        if (bed.isPresent()) {
            fromBed = scan(ctx, villager, bed.get());
            if (fromBed.enclosed()) {
                return fromBed;
            }
        }

        BlockPos here = villager.blockPosition();
        Result fromHere = fromBed != null && fromBed.covers(here) ? fromBed : scan(ctx, villager, here);
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
            if (!fromHere.covers(anchor) && (fromBed == null || !fromBed.covers(anchor))) {
                Result fromAnchor = scan(ctx, villager, anchor);
                if (fromAnchor.enclosed()) {
                    return fromAnchor;
                }
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
        return scan(new Context(level), villager, feet);
    }

    private static Result scan(Context ctx, Villager villager, BlockPos feet) {
        int maxVolume = HappyConfig.MAX_VOLUME.get();
        int minY = feet.getY();
        int maxY = minY + HappyConfig.MAX_HEIGHT.get() - 1;

        long start = feet.asLong();
        // A sleeping villager's feet (or a bed anchor) are inside the bed; start from the air above instead.
        long above = BlockPos.offset(start, Direction.UP);
        if (!ctx.passable(start) && ctx.passable(above)) {
            start = above;
        }

        // Pre-sized so the sets don't rehash while a fill grows toward the volume cap.
        LongOpenHashSet interior = new LongOpenHashSet(maxVolume + 1);
        LongOpenHashSet boundary = new LongOpenHashSet(maxVolume + 1);
        LongOpenHashSet doors = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        interior.add(start);
        queue.enqueue(start);
        boolean open = false;

        fill:
        while (!queue.isEmpty()) {
            long pos = queue.dequeueLong();
            for (Direction dir : DIRECTIONS) {
                long next = BlockPos.offset(pos, dir);
                int y = BlockPos.getY(next);
                if (y < minY || y > maxY || interior.contains(next) || boundary.contains(next)) {
                    continue;
                }
                if (!ctx.isLoaded(next)) {
                    // Unknown territory: assume the space keeps going.
                    open = true;
                    break fill;
                }
                BlockState state = ctx.state(next);
                if (state.getBlock() instanceof DoorBlock) {
                    boundary.add(next);
                    doors.add(doorBase(next, state));
                    continue;
                }
                if (!ctx.passable(next)) {
                    boundary.add(next);
                    continue;
                }
                // Gaps less than 2 blocks tall do not let the fill through sideways.
                if (dir.getAxis().isHorizontal()
                        && !ctx.passable(BlockPos.offset(next, Direction.UP)) && !ctx.passable(BlockPos.offset(next, Direction.DOWN))) {
                    continue;
                }
                if (interior.size() >= maxVolume) {
                    open = true;
                    break fill;
                }
                interior.add(next);
                queue.enqueue(next);
            }
        }

        Long2IntOpenHashMap columnTops = columnTops(interior);
        boolean skyAccess = seesSky(ctx, columnTops) || anyDoorLeadsToSky(ctx, doors, interior);
        Long2ObjectFunction<BlockState> states = ctx::state;
        if (open) {
            return new Result(false, interior.size(), false, false, 0, 0, 0, skyAccess, 1,
                    LongSets.unmodifiable(interior), LongSets.unmodifiable(boundary), minY, states);
        }

        return new Result(true, interior.size(),
                hasRoof(ctx, columnTops, maxY),
                bedInside(ctx.level, villager, interior),
                doors.size(),
                countWindows(ctx, boundary),
                countGreenery(ctx, interior, boundary, minY, maxY),
                skyAccess,
                countResidents(ctx.level, villager, interior),
                LongSets.unmodifiable(interior),
                LongSets.unmodifiable(boundary),
                minY,
                states);
    }

    /** Blocks of {@code tag} anywhere in the home: in its air or part of its shell. */
    public static int countTagged(ServerLevel level, Result home, TagKey<Block> tag) {
        int count = 0;
        for (LongSet set : new LongSet[] {home.interior(), home.boundary()}) {
            for (long packed : set) {
                if (home.states().get(packed).is(tag)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The villager itself plus every other villager standing in the home or whose claimed bed is in it. */
    private static int countResidents(ServerLevel level, Villager self, LongSet interior) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long packed : interior) {
            int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        AABB bounds = new AABB(minX, minY - 1, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(1.0);
        int residents = 1;
        for (Villager other : level.getEntitiesOfClass(Villager.class, bounds)) {
            if (other == self || !other.isAlive()) {
                continue;
            }
            BlockPos pos = other.blockPosition();
            boolean inside = interior.contains(pos.asLong()) || interior.contains(pos.above().asLong());
            Optional<BlockPos> bed = claimedBed(level, other);
            if (inside || (bed.isPresent() && bedTouches(level, interior, bed.get()))) {
                residents++;
            }
        }
        return residents;
    }

    // ------------------------------------------------------------------ passability

    /**
     * Per-evaluation chunk cache. Block reads go straight to the cached chunk (an array lookup), which is cheaper
     * than caching states per position; passability is cached per block state instead. Unloaded positions read as
     * void air and are never loaded; the fill itself checks {@link #isLoaded} first and treats unloaded space as open.
     */
    private static final class Context {
        final ServerLevel level;
        private final Long2ObjectOpenHashMap<LevelChunk> chunks = new Long2ObjectOpenHashMap<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private long lastChunkKey = Long.MIN_VALUE;
        @Nullable
        private LevelChunk lastChunk;

        Context(ServerLevel level) {
            this.level = level;
        }

        @Nullable
        LevelChunk chunk(int x, int z) {
            int cx = x >> 4, cz = z >> 4;
            long key = ChunkPos.asLong(cx, cz);
            if (key == lastChunkKey) {
                return lastChunk;
            }
            LevelChunk chunk = chunks.get(key);
            if (chunk == null && !chunks.containsKey(key)) {
                chunk = level.getChunkSource().getChunkNow(cx, cz);
                chunks.put(key, chunk);
            }
            lastChunkKey = key;
            lastChunk = chunk;
            return chunk;
        }

        boolean isLoaded(long pos) {
            return chunk(BlockPos.getX(pos), BlockPos.getZ(pos)) != null;
        }

        BlockState state(long pos) {
            LevelChunk chunk = chunk(BlockPos.getX(pos), BlockPos.getZ(pos));
            return chunk == null ? Blocks.VOID_AIR.defaultBlockState() : chunk.getBlockState(cursor.set(pos));
        }

        /** Air-like: no collision, or only a low sliver (carpet, bottom slab, snow layer...). Doors never count. */
        boolean passable(long pos) {
            return computePassability(pos, state(pos)) == PASSABLE;
        }

        private byte computePassability(long pos, BlockState state) {
            if (state.getBlock() instanceof DoorBlock) {
                return SOLID;
            }
            boolean positionIndependent = !state.getBlock().hasDynamicShape();
            if (positionIndependent) {
                byte known = STATE_PASSABILITY.getByte(state);
                if (known != 0) {
                    return known;
                }
            }
            VoxelShape shape = state.getCollisionShape(level, cursor.set(pos));
            byte result = shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.5 ? PASSABLE : SOLID;
            if (positionIndependent) {
                STATE_PASSABILITY.put(state, result);
            }
            return result;
        }

        /** Nothing solid overhead (leaves don't count, glass does). Same answer as {@code Level#getHeight}. */
        boolean underOpenSky(int x, int y, int z) {
            LevelChunk chunk = chunk(x, z);
            return chunk != null && y >= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        }

        /** A 2-tall air space. */
        boolean walkable(long pos) {
            return passable(pos) && passable(BlockPos.offset(pos, Direction.UP));
        }
    }

    private static long doorBase(long pos, BlockState state) {
        return state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? BlockPos.offset(pos, Direction.DOWN) : pos;
    }

    // ------------------------------------------------------------------ home attributes

    /** Highest interior Y per (x, z) column, keyed by {@code BlockPos.asLong(x, 0, z)}. */
    private static Long2IntOpenHashMap columnTops(LongSet interior) {
        Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
        tops.defaultReturnValue(Integer.MIN_VALUE);
        for (long packed : interior) {
            long column = BlockPos.asLong(BlockPos.getX(packed), 0, BlockPos.getZ(packed));
            int y = BlockPos.getY(packed);
            if (tops.get(column) < y) {
                tops.put(column, y);
            }
        }
        return tops;
    }

    /** Roofed when (almost) every column of the home has a solid block somewhere above it. */
    private static boolean hasRoof(Context ctx, Long2IntOpenHashMap columnTops, int maxY) {
        int covered = 0;
        for (var entry : columnTops.long2IntEntrySet()) {
            int x = BlockPos.getX(entry.getLongKey()), z = BlockPos.getZ(entry.getLongKey());
            int top = entry.getIntValue();
            int limit = top < maxY ? top + 1 : maxY + ROOF_SEARCH_ABOVE_CAP;
            for (int y = top + 1; y <= limit; y++) {
                if (!ctx.passable(BlockPos.asLong(x, y, z))) {
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
        return bed.isPresent() && bedTouches(level, interior, bed.get());
    }

    private static boolean bedTouches(ServerLevel level, LongSet interior, BlockPos bedPos) {
        BlockPos otherHalf = bedPos.relative(BedBlock.getConnectedDirection(level.getBlockState(bedPos)));
        return touches(interior, bedPos.asLong()) || touches(interior, otherHalf.asLong());
    }

    private static boolean touches(LongSet interior, long pos) {
        if (interior.contains(pos)) {
            return true;
        }
        for (Direction dir : DIRECTIONS) {
            if (interior.contains(BlockPos.offset(pos, dir))) {
                return true;
            }
        }
        return false;
    }

    private static int countWindows(Context ctx, LongSet boundary) {
        int windows = 0;
        for (long packed : boundary) {
            BlockState state = ctx.state(packed);
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
    private static int countGreenery(Context ctx, LongSet interior, LongSet boundary, int minY, int maxY) {
        int count = 0;
        for (long packed : interior) {
            if (isGreenery(ctx.state(packed))) {
                count++;
            }
        }
        for (long packed : boundary) {
            int y = BlockPos.getY(packed);
            if (y < minY || y > maxY || !isGreenery(ctx.state(packed))) {
                continue;
            }
            if (interior.contains(BlockPos.offset(packed, Direction.DOWN))) {
                continue; // hangs from above: part of the ceiling
            }
            if (!touchesOutside(ctx, packed, interior)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isGreenery(BlockState state) {
        return state.is(GREENERY) && !state.is(Blocks.FLOWER_POT);
    }

    private static boolean touchesOutside(Context ctx, long pos, LongSet interior) {
        for (Direction dir : HORIZONTAL) {
            long side = BlockPos.offset(pos, dir);
            if (!interior.contains(side) && ctx.passable(side)) {
                return true;
            }
        }
        long up = BlockPos.offset(pos, Direction.UP);
        return !interior.contains(up) && ctx.passable(up);
    }

    // ------------------------------------------------------------------ sky access

    /** Any interior block under open sky; checking each column's top block is enough. */
    private static boolean seesSky(Context ctx, Long2IntOpenHashMap columnTops) {
        for (var entry : columnTops.long2IntEntrySet()) {
            long column = entry.getLongKey();
            if (ctx.underOpenSky(BlockPos.getX(column), entry.getIntValue(), BlockPos.getZ(column))) {
                return true;
            }
        }
        return false;
    }

    /** Walks out of each door (2-tall openings only) looking for a spot under the open sky. */
    private static boolean anyDoorLeadsToSky(Context ctx, LongSet doors, LongSet interior) {
        for (long door : doors) {
            LongOpenHashSet visited = new LongOpenHashSet();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            for (Direction dir : HORIZONTAL) {
                long outside = BlockPos.offset(door, dir);
                if (!interior.contains(outside) && ctx.isLoaded(outside) && ctx.walkable(outside)) {
                    visited.add(outside);
                    queue.enqueue(outside);
                }
            }
            while (!queue.isEmpty() && visited.size() < DOOR_SKY_SEARCH_LIMIT) {
                long pos = queue.dequeueLong();
                if (ctx.underOpenSky(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos))) {
                    return true;
                }
                for (Direction dir : HORIZONTAL) {
                    long side = BlockPos.offset(pos, dir);
                    for (int dy = -1; dy <= 1; dy++) {
                        long next = BlockPos.offset(side, 0, dy, 0);
                        if (!interior.contains(next) && ctx.isLoaded(next) && ctx.walkable(next) && visited.add(next)) {
                            queue.enqueue(next);
                        }
                    }
                }
            }
        }
        return false;
    }
}
