package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.mixin.VillagerAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.block.Block;
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
            int freeResidents = HappyConfig.FREE_RESIDENTS.get();
            if (HappyConfig.CROWDING_ENABLED.get() && home.residents() > freeResidents) {
                int shared = home.volume() * freeResidents / home.residents();
                factors.add(HappinessFactor.of("crowded", areaScore(shared) - areaScore(home.volume()), home.residents()));
            }
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
            addTastes(level, villager, home, factors);
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

        addSafety(level, villager, data, factors);
        addMoodEvents(level, data, factors);

        double target = HappyConfig.BASE_HAPPINESS.get();
        for (HappinessFactor factor : factors) {
            target += factor.value();
        }
        data.applyEvaluation(target, !home.enclosed(), factors);
    }

    /** The block tag of things a profession likes to have at home: {@code #happyvillagers:tastes/<profession>}. */
    public static TagKey<Block> tastesTag(VillagerProfession profession) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        String path = key.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) ? key.getPath() : key.getNamespace() + "/" + key.getPath();
        return TagKey.create(Registries.BLOCK, HappyVillagers.id("tastes/" + path));
    }

    private static void addTastes(ServerLevel level, Villager villager, HomeScanner.Result home, List<HappinessFactor> factors) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
            return;
        }
        TagKey<Block> tag = tastesTag(profession);
        if (BuiltInRegistries.BLOCK.getTag(tag).map(set -> set.size() == 0).orElse(true)) {
            return; // nothing defined for this profession
        }
        int liked = HomeScanner.countTagged(level, home, tag);
        if (liked > 0) {
            factors.add(HappinessFactor.of("tastes", Math.min(HappyConfig.TASTES_MAX.get(), liked * HappyConfig.TASTES_BONUS.get()), liked));
        } else {
            factors.add(HappinessFactor.missing("no_tastes"));
        }
    }

    /** Iron golems reassure villagers; raids frighten them, and surviving one is a relief. */
    private static void addSafety(ServerLevel level, Villager villager, HappinessData data, List<HappinessFactor> factors) {
        int golemRadius = HappyConfig.GOLEM_RADIUS.get();
        if (!level.getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(golemRadius), IronGolem::isAlive).isEmpty()) {
            factors.add(HappinessFactor.of("golem", HappyConfig.GOLEM_BONUS.get(), 0));
        }
        Raid raid = level.getRaidAt(villager.blockPosition());
        if (raid == null) {
            return;
        }
        if (raid.isVictory()) {
            if (raid.getId() != data.lastRaidId()) {
                data.setLastRaidId(raid.getId());
                data.addEvent(MoodEventHandler.RAID_SAVED, HappyConfig.RAID_SAVED.get(), level.getGameTime(), HappyConfig.RAID_SAVED_TICKS.get());
            }
        } else if (raid.isStarted() && !raid.isOver()) {
            factors.add(HappinessFactor.of("raid_ongoing", HappyConfig.RAID_ONGOING.get(), 0));
        }
    }

    private static void addMoodEvents(ServerLevel level, HappinessData data, List<HappinessFactor> factors) {
        long now = level.getGameTime();
        data.pruneEvents(now);
        for (MoodEvent event : data.events()) {
            int minutesLeft = (int) Math.ceil(event.remaining(now) / 1200.0);
            factors.add(HappinessFactor.of(event.id(), event.valueAt(now), minutesLeft));
        }
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
