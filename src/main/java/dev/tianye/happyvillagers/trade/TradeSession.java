package dev.tianye.happyvillagers.trade;

import java.util.List;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The villager's real offers while a player is trading, plus the bonus offers temporarily shown on top.
 * The live offer list only holds the happiness-adjusted view for the duration of the session.
 */
public record TradeSession(List<MerchantOffer> baseOffers, List<MerchantOffer> bonusOffers, List<String> bonusKeys,
                           int locked, double priceModifier) {
}
