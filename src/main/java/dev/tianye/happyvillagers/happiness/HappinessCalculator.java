package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.mixin.VillagerAccessor;
import java.util.ArrayList;
import java.util.List;
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

/** Works out a villager's target happiness and the factors behind it. */
public final class HappinessCalculator {
    private HappinessCalculator() {}

    public static void evaluate(ServerLevel level, Villager villager, HappinessData data) {
        List<HappinessFactor> factors = new ArrayList<>();
        HomeScanner.Result home = HomeScanner.findHome(level, villager, data);

        if (home.enclosed()) {
            factors.add(HappinessFactor.of("area", areaScore(home.volume()), home.volume()));
            addFlag(factors, home.roof(), "roof", "no_roof", HappyConfig.ROOF_BONUS.get());
            addFlag(factors, home.bed(), "bed", "no_bed", HappyConfig.BED_BONUS.get());
            addFlag(factors, home.doors() > 0, "door", "no_door", HappyConfig.DOOR_BONUS.get());
            if (home.windows() > 0) {
                double windows = Math.min(HappyConfig.WINDOW_MAX.get(), home.windows() * HappyConfig.WINDOW_BONUS.get());
                factors.add(HappinessFactor.of("windows", windows, home.windows()));
            } else {
                factors.add(HappinessFactor.missing("no_windows"));
            }
            if (home.greenery() > 0) {
                double greenery = Math.min(HappyConfig.GREENERY_MAX.get(), home.greenery() * HappyConfig.GREENERY_BONUS.get());
                factors.add(HappinessFactor.of("greenery", greenery, home.greenery()));
            } else {
                factors.add(HappinessFactor.missing("no_greenery"));
            }
        } else {
            factors.add(HappinessFactor.missing("homeless"));
        }

        addFlag(factors, hasSocialized(level, villager, data), "social", "lonely", HappyConfig.SOCIAL_BONUS.get());
        addFlag(factors, hasFood(level, villager), "food", "hungry", HappyConfig.FOOD_BONUS.get());

        int light = level.getMaxLocalRawBrightness(villager.blockPosition());
        double lightScore = (light - HappyConfig.LIGHT_NEUTRAL.get()) * HappyConfig.LIGHT_PER_LEVEL.get();
        factors.add(new HappinessFactor(lightScore < 0 ? "dark" : "light", lightScore, light, lightScore >= 0));

        if (home.skyAccess()) {
            factors.add(HappinessFactor.of("sky", HappyConfig.SKY_ACCESS_BONUS.get(), 0));
        } else {
            double penalty = HappyConfig.NO_SKY_ACCESS_PENALTY.get();
            factors.add(penalty > 0 ? HappinessFactor.of("no_sky", -penalty, 0) : HappinessFactor.missing("no_sky"));
        }

        double target = HappyConfig.BASE_HAPPINESS.get();
        for (HappinessFactor factor : factors) {
            target += factor.value();
        }
        data.applyEvaluation(target, !home.enclosed(), factors);
    }

    /** 27 blocks is neutral; -0.3 per block below, +0.01 per block up to 245, +0.005 per block beyond. */
    public static double areaScore(int volume) {
        int neutral = HappyConfig.NEUTRAL_VOLUME.get();
        int large = Math.max(neutral, HappyConfig.LARGE_VOLUME.get());
        if (volume < neutral) {
            return -(neutral - volume) * HappyConfig.PENALTY_PER_BLOCK.get();
        }
        double score = (Math.min(volume, large) - neutral) * HappyConfig.BONUS_PER_BLOCK.get();
        if (volume > large) {
            score += (volume - large) * HappyConfig.BONUS_PER_BLOCK_LARGE.get();
        }
        return score;
    }

    private static void addFlag(List<HappinessFactor> factors, boolean present, String id, String missingId, double bonus) {
        factors.add(present ? HappinessFactor.of(id, bonus, 0) : HappinessFactor.missing(missingId));
    }

    /** Vanilla gossip: {@code Villager#gossip} stamps lastGossipTime whenever two villagers exchange gossip. */
    private static boolean hasSocialized(ServerLevel level, Villager villager, HappinessData data) {
        long lastGossip = ((VillagerAccessor) villager).happyvillagers$getLastGossipTime();
        if (lastGossip > data.lastSocialTime()) {
            data.setLastSocialTime(lastGossip); // vanilla does not save lastGossipTime, so remember it ourselves
        }
        long last = data.lastSocialTime();
        long now = level.getGameTime();
        return last > 0 && now >= last && now - last <= HappyConfig.SOCIAL_MEMORY_TICKS.get();
    }

    private static boolean hasFood(ServerLevel level, Villager villager) {
        if (containsFood(villager.getInventory())) {
            return true;
        }
        int radius = HappyConfig.FOOD_SEARCH_RADIUS.get();
        if (radius <= 0) {
            return false;
        }
        BlockPos center = villager.blockPosition();
        long radiusSq = (long) radius * radius;
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - radius); cx <= SectionPos.blockToSectionCoord(center.getX() + radius); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - radius); cz <= SectionPos.blockToSectionCoord(center.getZ() + radius); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Container container) || be.getBlockPos().distSqr(center) > radiusSq) {
                        continue;
                    }
                    // Don't roll unopened loot chests just by looking at them.
                    if (be instanceof RandomizableContainer loot && loot.getLootTable() != null) {
                        continue;
                    }
                    if (containsFood(container)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsFood(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && (stack.has(DataComponents.FOOD) || Villager.FOOD_POINTS.containsKey(stack.getItem()))) {
                return true;
            }
        }
        return false;
    }
}
