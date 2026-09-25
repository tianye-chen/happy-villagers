package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.ModAdvancements;
import dev.tianye.happyvillagers.ModAttachments;
import dev.tianye.happyvillagers.network.HappinessPayload;
import dev.tianye.happyvillagers.trade.HappyTrading;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-side driver: evaluates surroundings, drifts happiness toward its target and applies consequences. */
public final class HappinessManager {
    private static final int DRIFT_INTERVAL = 20;
    /** Villager entity event that spawns angry-villager particles. */
    private static final byte ANGRY_PARTICLES = 13;
    private static final double STRIKE_WITNESS_RADIUS = 32.0;

    private HappinessManager() {}

    public static HappinessData get(Villager villager) {
        return villager.getData(ModAttachments.HAPPINESS);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        HappinessData data = get(villager);
        if (!data.isInitialized()) {
            data.setHappiness(HappyConfig.INITIAL_HAPPINESS.get());
            data.markInitialized();
        }

        boolean changed = false;
        int interval = HappyConfig.EVALUATION_INTERVAL.get();
        // Stagger villagers by entity id so a village doesn't flood fill on the same tick.
        if (!data.isEvaluated() || (villager.tickCount + villager.getId()) % interval == 0) {
            HappinessCalculator.evaluate(level, villager, data);
            changed = true;
        }

        if (villager.tickCount % DRIFT_INTERVAL == 0) {
            double before = data.happiness();
            drift(data);
            changed |= data.happiness() != before;
            handleEmployment(level, villager, data);
        }

        if (changed) {
            Player trader = villager.getTradingPlayer();
            if (trader instanceof ServerPlayer player) {
                HappinessPayload.sendTo(player, villager, data);
            }
        }
    }

    private static void drift(HappinessData data) {
        double step = HappyConfig.CHANGE_PER_MINUTE.get() / (60.0 * 20.0 / DRIFT_INTERVAL);
        double current = data.rawHappiness();
        double target = data.target();
        if (current < target) {
            data.setHappiness(Math.min(target, current + step));
        } else if (current > target) {
            data.setHappiness(Math.max(target, current - step));
        }
    }

    /** At rock bottom the villager quits; it won't take a new job until it has recovered a little. */
    private static void handleEmployment(ServerLevel level, Villager villager, HappinessData data) {
        double happiness = data.happiness();
        if (!data.hasQuit()) {
            if (happiness <= HappyConfig.QUIT_AT.get() && hasJob(villager)) {
                quitJob(level, villager, data);
            }
        } else if (happiness >= HappyConfig.REHIRE_AT.get()) {
            data.setQuit(false);
        } else if (hasJob(villager)) {
            // Picked up a job site while still too unhappy to work: drop it again.
            makeUnemployed(level, villager);
        }
    }

    private static boolean hasJob(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    private static void quitJob(ServerLevel level, Villager villager, HappinessData data) {
        if (villager.getTradingPlayer() instanceof ServerPlayer player) {
            player.closeContainer();
        }
        makeUnemployed(level, villager);
        villager.setVillagerXp(0);
        data.bonusUses().clear();
        data.setQuit(true);
        level.broadcastEntityEvent(villager, ANGRY_PARTICLES);
        villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.8F);
        for (ServerPlayer witness : level.getEntitiesOfClass(ServerPlayer.class, villager.getBoundingBox().inflate(STRIKE_WITNESS_RADIUS))) {
            ModAdvancements.award(witness, ModAdvancements.LABOR_STRIKE);
        }
    }

    private static void makeUnemployed(ServerLevel level, Villager villager) {
        HappyTrading.endSession(villager);
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        VillagerData villagerData = villager.getVillagerData();
        // Changing profession also wipes the villager's offers (see Villager#setVillagerData).
        villager.setVillagerData(villagerData.setProfession(VillagerProfession.NONE).setLevel(1));
        villager.refreshBrain(level);
    }
}
