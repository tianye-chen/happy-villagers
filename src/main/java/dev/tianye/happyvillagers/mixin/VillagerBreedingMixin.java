package dev.tianye.happyvillagers.mixin;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.happiness.HappinessManager;
import dev.tianye.happyvillagers.trait.TraitManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public abstract class VillagerBreedingMixin {
    /** Villagers breed through VillagerMakeLove -> getBreedOffspring, which fires no NeoForge baby event. */
    @Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/Villager;",
            at = @At("RETURN"))
    private void happyvillagers$inheritTraits(ServerLevel level, AgeableMob otherParent, CallbackInfoReturnable<Villager> cir) {
        Villager child = cir.getReturnValue();
        if (child == null || !(otherParent instanceof Villager partner) || !HappyConfig.TRAITS_ENABLED.get()
                || TraitManager.all().isEmpty()) {
            return;
        }
        Villager self = (Villager) (Object) this;
        HappinessManager.get(child).setTraits(TraitManager.inherit(
                HappinessManager.get(self).traits(), HappinessManager.get(partner).traits(), child.getRandom()));
    }
}
