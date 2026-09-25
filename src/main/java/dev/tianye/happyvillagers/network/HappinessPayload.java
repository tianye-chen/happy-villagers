package dev.tianye.happyvillagers.network;

import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.happiness.HappinessData;
import dev.tianye.happyvillagers.happiness.HappinessFactor;
import dev.tianye.happyvillagers.trade.HappyTrading;
import dev.tianye.happyvillagers.trade.TradeSession;
import dev.tianye.happyvillagers.trade.TradeSessionHolder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Happiness details for the trade screen the player currently has open. */
public record HappinessPayload(int containerId, double happiness, double target, double priceModifier,
                               int lockedTrades, int bonusTrades, List<HappinessFactor> factors)
        implements CustomPacketPayload {
    public static final Type<HappinessPayload> TYPE = new Type<>(HappyVillagers.id("happiness"));

    public static final StreamCodec<ByteBuf, HappinessPayload> STREAM_CODEC = NeoForgeStreamCodecs.composite(
            ByteBufCodecs.VAR_INT, HappinessPayload::containerId,
            ByteBufCodecs.DOUBLE, HappinessPayload::happiness,
            ByteBufCodecs.DOUBLE, HappinessPayload::target,
            ByteBufCodecs.DOUBLE, HappinessPayload::priceModifier,
            ByteBufCodecs.VAR_INT, HappinessPayload::lockedTrades,
            ByteBufCodecs.VAR_INT, HappinessPayload::bonusTrades,
            HappinessFactor.STREAM_CODEC.apply(ByteBufCodecs.list()), HappinessPayload::factors,
            HappinessPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendTo(ServerPlayer player, Villager villager, HappinessData data) {
        if (!(player.containerMenu instanceof MerchantMenu menu) || !player.connection.hasChannel(TYPE)) {
            return;
        }
        TradeSession session = ((TradeSessionHolder) villager).happyvillagers$getSession();
        double modifier = session != null ? session.priceModifier() : HappyTrading.priceModifier(data.happiness());
        PacketDistributor.sendToPlayer(player, new HappinessPayload(menu.containerId, data.happiness(), data.target(),
                modifier, session != null ? session.locked() : 0, session != null ? session.bonusOffers().size() : 0,
                data.factors()));
    }

    public static void handle(HappinessPayload payload, IPayloadContext context) {
        // Only ever invoked on the client; the class reference is resolved lazily.
        context.enqueueWork(() -> dev.tianye.happyvillagers.client.ClientHappiness.set(payload));
    }
}
