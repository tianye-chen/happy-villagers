package dev.tianye.happyvillagers.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.tianye.happyvillagers.trade.HappyTrading;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
    /** If the villager is saved mid-trade, write its real offers rather than the temporary happiness view. */
    @ModifyExpressionValue(
            method = "addAdditionalSaveData",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/AbstractVillager;getOffers()Lnet/minecraft/world/item/trading/MerchantOffers;"))
    private MerchantOffers happyvillagers$saveRealOffers(MerchantOffers offers) {
        if ((Object) this instanceof Villager villager) {
            return HappyTrading.offersToSave(villager, offers);
        }
        return offers;
    }
}
