package dev.tianye.happyvillagers.mixin;

import dev.tianye.happyvillagers.happiness.HappinessManager;
import dev.tianye.happyvillagers.network.HappinessPayload;
import dev.tianye.happyvillagers.trade.HappyTrading;
import dev.tianye.happyvillagers.trade.TradeSession;
import dev.tianye.happyvillagers.trade.TradeSessionHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin implements TradeSessionHolder {
    @Unique
    @Nullable
    private TradeSession happyvillagers$session;

    @Override
    public @Nullable TradeSession happyvillagers$getSession() {
        return happyvillagers$session;
    }

    @Override
    public void happyvillagers$setSession(@Nullable TradeSession session) {
        this.happyvillagers$session = session;
    }

    /** Runs only from startTrading, after vanilla's reputation discounts and before the screen opens. */
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void happyvillagers$applyHappiness(Player player, CallbackInfo ci) {
        HappyTrading.beginSession((Villager) (Object) this);
    }

    @Inject(method = "startTrading", at = @At("TAIL"))
    private void happyvillagers$sendHappiness(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            Villager self = (Villager) (Object) this;
            HappinessPayload.sendTo(serverPlayer, self, HappinessManager.get(self));
        }
    }

    /** HEAD so vanilla's resetSpecialPrices afterwards runs over the restored list. */
    @Inject(method = "stopTrading", at = @At("HEAD"))
    private void happyvillagers$restoreOffers(CallbackInfo ci) {
        HappyTrading.endSession((Villager) (Object) this);
    }

    @Inject(method = "restock", at = @At("TAIL"))
    private void happyvillagers$restockBonusTrades(CallbackInfo ci) {
        HappyTrading.onRestock((Villager) (Object) this);
    }
}
