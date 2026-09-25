package dev.tianye.happyvillagers.trade;

import dev.tianye.happyvillagers.HappyConfig;
import dev.tianye.happyvillagers.happiness.HappinessCalculator;
import dev.tianye.happyvillagers.happiness.HappinessData;
import dev.tianye.happyvillagers.happiness.HappinessManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Applies happiness to trading. When a player opens the trade screen the villager's offer list is swapped for an
 * adjusted view (withheld high-tier trades, extra bonus trades, happiness price changes); when trading stops, the
 * original list is restored. Saving mid-session always writes the original list (see AbstractVillagerMixin).
 */
public final class HappyTrading {
    private HappyTrading() {}

    /** Price change as a fraction of the base cost: negative is a discount, 0 inside the neutral band. */
    public static double priceModifier(double happiness) {
        double neutralMin = HappyConfig.NEUTRAL_MIN.get();
        double neutralMax = Math.max(neutralMin, HappyConfig.NEUTRAL_MAX.get());
        if (happiness > neutralMax && neutralMax < 10.0) {
            return -HappyConfig.MAX_DISCOUNT.get() * (happiness - neutralMax) / (10.0 - neutralMax);
        }
        if (happiness < neutralMin && neutralMin > 0.0) {
            return HappyConfig.MAX_MARKUP.get() * (neutralMin - happiness) / neutralMin;
        }
        return 0.0;
    }

    /** How many of {@code total} offers an unhappy villager is still willing to make. */
    public static int visibleOffers(int total, double happiness) {
        double lockBelow = HappyConfig.LOCK_TRADES_BELOW.get();
        if (total == 0 || happiness >= lockBelow) {
            return total;
        }
        int visible = (int) Math.ceil(total * Math.max(0.0, happiness) / lockBelow);
        return Math.max(1, Math.min(total, visible));
    }

    /** Called right after vanilla applied its reputation / Hero of the Village price changes. */
    public static void beginSession(Villager villager) {
        endSession(villager);
        HappinessData data = HappinessManager.get(villager);
        if (!data.isEvaluated() && villager.level() instanceof ServerLevel level) {
            // Opened before its first (staggered) evaluation: do it now so the screen shows real factors.
            HappinessCalculator.evaluate(level, villager, data);
        }
        double happiness = data.happiness();
        MerchantOffers offers = villager.getOffers();
        List<MerchantOffer> base = new ArrayList<>(offers);

        int visible = visibleOffers(base.size(), happiness);
        if (visible < base.size()) {
            offers.subList(visible, offers.size()).clear();
        }

        List<MerchantOffer> bonusOffers = new ArrayList<>();
        List<String> bonusKeys = new ArrayList<>();
        VillagerData villagerData = villager.getVillagerData();
        for (BonusTrade trade : BonusTradeManager.trades()) {
            if (trade.appliesTo(villagerData.getProfession(), villagerData.getLevel(), happiness)) {
                MerchantOffer offer = trade.createOffer(data.bonusUses().getOrDefault(trade.key().toString(), 0));
                offers.add(offer);
                bonusOffers.add(offer);
                bonusKeys.add(trade.key().toString());
            }
        }

        double modifier = priceModifier(happiness);
        if (modifier != 0.0) {
            for (MerchantOffer offer : offers) {
                int diff = (int) Math.round(offer.getBaseCostA().getCount() * modifier);
                if (diff != 0) {
                    offer.addToSpecialPriceDiff(diff);
                }
            }
        }

        ((TradeSessionHolder) villager).happyvillagers$setSession(
                new TradeSession(base, bonusOffers, bonusKeys, base.size() - visible, modifier));
    }

    /** Restores the villager's real offers and remembers how often each bonus trade was used. */
    public static void endSession(Villager villager) {
        TradeSessionHolder holder = (TradeSessionHolder) villager;
        TradeSession session = holder.happyvillagers$getSession();
        if (session == null) {
            return;
        }
        holder.happyvillagers$setSession(null);
        HappinessData data = HappinessManager.get(villager);
        for (int i = 0; i < session.bonusOffers().size(); i++) {
            data.bonusUses().put(session.bonusKeys().get(i), session.bonusOffers().get(i).getUses());
        }
        MerchantOffers offers = villager.getOffers();
        offers.clear();
        offers.addAll(session.baseOffers());
    }

    /** The list that should be saved: never the temporary session view. */
    public static MerchantOffers offersToSave(Villager villager, MerchantOffers live) {
        TradeSession session = ((TradeSessionHolder) villager).happyvillagers$getSession();
        if (session == null) {
            return live;
        }
        MerchantOffers saved = new MerchantOffers();
        for (MerchantOffer offer : session.baseOffers()) {
            MerchantOffer copy = offer.copy();
            copy.resetSpecialPriceDiff();
            saved.add(copy);
        }
        return saved;
    }

    /** Vanilla restocks reset every offer; bonus trades restock with them. */
    public static void onRestock(Villager villager) {
        HappinessManager.get(villager).bonusUses().clear();
    }
}
