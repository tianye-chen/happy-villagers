package dev.tianye.happyvillagers;

import dev.tianye.happyvillagers.happiness.HappinessManager;
import dev.tianye.happyvillagers.trade.TradeSession;
import dev.tianye.happyvillagers.trade.TradeSessionHolder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/** Awards the mod's advancements; each has a single {@code minecraft:impossible} criterion named "trigger". */
public final class ModAdvancements {
    public static final String CUSTOMER_SERVICE = "customer_service";
    public static final String MENDING_FINALLY = "mending_finally";
    public static final String LABOUR_STRIKE = "labour_strike";
    private static final String CRITERION = "trigger";

    private ModAdvancements() {}

    public static void award(ServerPlayer player, String id) {
        AdvancementHolder advancement = player.server.getAdvancements().get(HappyVillagers.id(id));
        if (advancement != null) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getAbstractVillager() instanceof Villager villager)) {
            return;
        }
        if (HappinessManager.get(villager).happiness() >= 10.0) {
            award(player, CUSTOMER_SERVICE);
        }
        MerchantOffer offer = event.getMerchantOffer();
        TradeSession session = ((TradeSessionHolder) villager).happyvillagers$getSession();
        if (session != null && session.bonusOffers().contains(offer) && storesMending(player, offer)) {
            award(player, MENDING_FINALLY);
        }
    }

    private static boolean storesMending(ServerPlayer player, MerchantOffer offer) {
        ItemEnchantments stored = offer.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        return player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.MENDING)
                .map(mending -> stored.getLevel(mending) > 0)
                .orElse(false);
    }
}
