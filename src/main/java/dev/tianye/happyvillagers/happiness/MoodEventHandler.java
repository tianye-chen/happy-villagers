package dev.tianye.happyvillagers.happiness;

import dev.tianye.happyvillagers.HappyConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Turns things that happen to (or around) villagers into mood events. */
public final class MoodEventHandler {
    public static final String HURT_BY_PLAYER = "hurt_by_player";
    public static final String GRIEF = "grief";
    public static final String RAID_SAVED = "raid_saved";

    private MoodEventHandler() {}

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level
                && event.getSource().getEntity() instanceof Player && event.getNewDamage() > 0) {
            HappinessManager.get(villager).addEvent(HURT_BY_PLAYER, HappyConfig.HURT_BY_PLAYER.get(),
                    level.getGameTime(), HappyConfig.HURT_BY_PLAYER_TICKS.get());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager dead) || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        int radius = HappyConfig.GRIEF_RADIUS.get();
        for (Villager witness : level.getEntitiesOfClass(Villager.class, dead.getBoundingBox().inflate(radius))) {
            if (witness != dead && witness.isAlive() && witness.hasLineOfSight(dead)) {
                HappinessManager.get(witness).addEvent(GRIEF, HappyConfig.GRIEF.get(),
                        level.getGameTime(), HappyConfig.GRIEF_TICKS.get());
            }
        }
    }
}
