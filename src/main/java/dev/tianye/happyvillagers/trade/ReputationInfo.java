package dev.tianye.happyvillagers.trade;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A player's vanilla reputation with a villager, and what it does to that villager's prices.
 *
 * @param reputation    vanilla's weighted gossip total for this player
 * @param breakdown     each gossip type's weighted share of that total (non-zero ones only)
 * @param minPriceDelta smallest change vanilla's reputation pricing makes to one of the villager's own trades
 * @param maxPriceDelta largest such change (equal to {@code minPriceDelta} when every trade shifts the same)
 * @param hero          whether the player has Hero of the Village, which vanilla discounts on top
 */
public record ReputationInfo(int reputation, List<Share> breakdown, int minPriceDelta, int maxPriceDelta, boolean hero) {
    public static final ReputationInfo NONE = new ReputationInfo(0, List.of(), 0, 0, false);

    /** One gossip type's weighted contribution, e.g. {@code minor_negative -> -200}. */
    public record Share(String gossipType, int value) {
        static final StreamCodec<ByteBuf, Share> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Share::gossipType,
                ByteBufCodecs.VAR_INT, Share::value,
                Share::new);
    }

    public static final StreamCodec<ByteBuf, ReputationInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReputationInfo::reputation,
            Share.STREAM_CODEC.apply(ByteBufCodecs.list()), ReputationInfo::breakdown,
            ByteBufCodecs.VAR_INT, ReputationInfo::minPriceDelta,
            ByteBufCodecs.VAR_INT, ReputationInfo::maxPriceDelta,
            ByteBufCodecs.BOOL, ReputationInfo::hero,
            ReputationInfo::new);

    /**
     * Mirrors {@code Villager#updateSpecialPrices}: each of the villager's own trades shifts by
     * {@code -floor(reputation * priceMultiplier)}. Bonus and special trades are added after vanilla's step, so
     * reputation never touches them and they are left out here.
     */
    public static ReputationInfo of(Villager villager, Player player) {
        int reputation = villager.getPlayerReputation(player);
        List<Share> breakdown = new ArrayList<>();
        for (GossipType type : GossipType.values()) {
            int value = villager.getGossips().getReputation(player.getUUID(), t -> t == type);
            if (value != 0) {
                breakdown.add(new Share(type.id, value));
            }
        }

        TradeSession session = ((TradeSessionHolder) villager).happyvillagers$getSession();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (MerchantOffer offer : villager.getOffers()) {
            if (session != null && session.bonusOffers().contains(offer)) {
                continue;
            }
            int delta = reputation == 0 ? 0 : -Mth.floor(reputation * offer.getPriceMultiplier());
            min = Math.min(min, delta);
            max = Math.max(max, delta);
        }
        if (min > max) {
            min = max = 0;
        }
        return new ReputationInfo(reputation, List.copyOf(breakdown), min, max, player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE));
    }
}
