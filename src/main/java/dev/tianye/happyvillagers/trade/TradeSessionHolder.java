package dev.tianye.happyvillagers.trade;

import org.jetbrains.annotations.Nullable;

/** Implemented on {@link net.minecraft.world.entity.npc.Villager} by a mixin. */
public interface TradeSessionHolder {
    @Nullable
    TradeSession happyvillagers$getSession();

    void happyvillagers$setSession(@Nullable TradeSession session);
}
