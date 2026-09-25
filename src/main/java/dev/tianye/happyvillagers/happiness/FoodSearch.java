package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Finds food in storage near a villager. Each container's answer is cached per level for one evaluation interval
 * and shared by every villager, so a storage room next to a village is read once rather than once per villager.
 */
final class FoodSearch {
    /** Per level: container position -> (game time checked << 1 | has food). Server thread only. */
    private static final Map<ServerLevel, Long2LongOpenHashMap> CACHE = new WeakHashMap<>();
    private static final int PRUNE_THRESHOLD = 4096;

    private FoodSearch() {}

    static boolean hasFood(ServerLevel level, Villager villager) {
        if (containsFood(villager.getInventory())) {
            return true;
        }
        int radius = HappyConfig.FOOD_SEARCH_RADIUS.get();
        if (radius <= 0) {
            return false;
        }
        BlockPos center = villager.blockPosition();
        List<BlockEntity> containers = nearbyContainers(level, center, radius);
        containers.sort(Comparator.comparingDouble(be -> be.getBlockPos().distSqr(center)));

        long now = level.getGameTime();
        long ttl = HappyConfig.EVALUATION_INTERVAL.get();
        Long2LongOpenHashMap cache = CACHE.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        if (cache.size() > PRUNE_THRESHOLD) {
            cache.values().removeIf(entry -> now - (entry >>> 1) >= ttl);
        }

        int limit = Math.min(containers.size(), HappyConfig.FOOD_MAX_CONTAINERS.get());
        for (int i = 0; i < limit; i++) {
            BlockEntity be = containers.get(i);
            long key = be.getBlockPos().asLong();
            long entry = cache.getOrDefault(key, -1L);
            boolean food;
            if (entry >= 0 && now - (entry >>> 1) < ttl && now >= (entry >>> 1)) {
                food = (entry & 1L) == 1L;
            } else {
                food = containsFood((Container) be);
                cache.put(key, (now << 1) | (food ? 1L : 0L));
            }
            if (food) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockEntity> nearbyContainers(ServerLevel level, BlockPos center, int radius) {
        List<BlockEntity> found = new ArrayList<>();
        long radiusSq = (long) radius * radius;
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - radius); cx <= SectionPos.blockToSectionCoord(center.getX() + radius); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - radius); cz <= SectionPos.blockToSectionCoord(center.getZ() + radius); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof Container && be.getBlockPos().distSqr(center) <= radiusSq
                            // Don't roll unopened loot chests just by looking at them.
                            && !(be instanceof RandomizableContainer loot && loot.getLootTable() != null)) {
                        found.add(be);
                    }
                }
            }
        }
        return found;
    }

    static boolean containsFood(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && (stack.has(DataComponents.FOOD) || Villager.FOOD_POINTS.containsKey(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }
}
