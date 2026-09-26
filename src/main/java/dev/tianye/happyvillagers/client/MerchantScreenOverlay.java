package dev.tianye.happyvillagers.client;

import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.happiness.HappinessFactor;
import dev.tianye.happyvillagers.happiness.HappinessLevel;
import dev.tianye.happyvillagers.network.HappinessPayload;
import dev.tianye.happyvillagers.trade.ReputationInfo;
import dev.tianye.happyvillagers.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Draws the heart + happiness number on the villager trade screen, with a breakdown tooltip on hover. */
@EventBusSubscriber(modid = HappyVillagers.MOD_ID, value = Dist.CLIENT)
public final class MerchantScreenOverlay {
    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final int HEART_SIZE = 9;
    private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);
    /** Space between the reputation indicator and the happiness indicator. */
    private static final int INDICATOR_GAP = 8;
    /** Right-aligned on the "Inventory" label row, which is otherwise empty on the right-hand side. */
    private static final int RIGHT_MARGIN = 8;
    private static final int ROW_Y = 72;

    private MerchantScreenOverlay() {}

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen screen)) {
            return;
        }
        HappinessPayload info = ClientHappiness.forContainer(screen.getMenu().containerId);
        if (info == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = screen.getMinecraft().font;
        HappinessLevel level = HappinessLevel.of(info.happiness());

        String number = String.format(Locale.ROOT, "%.1f", info.happiness());
        int textWidth = font.width(number);
        int width = HEART_SIZE + 2 + textWidth;
        int x = screen.getGuiLeft() + screen.getXSize() - RIGHT_MARGIN - width;
        int y = screen.getGuiTop() + ROW_Y;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300); // above the screen's items and labels
        graphics.blitSprite(HEART_CONTAINER, x, y - 1, HEART_SIZE, HEART_SIZE);
        if (info.happiness() > 0) {
            graphics.blitSprite(info.happiness() >= 5.0 ? HEART_FULL : HEART_HALF, x, y - 1, HEART_SIZE, HEART_SIZE);
        }
        int color = level.color().getColor() != null ? level.color().getColor() : 0xFFFFFF;
        graphics.drawString(font, number, x + HEART_SIZE + 2, y, color, true);

        // Reputation: the player's vanilla standing with this villager, to the left of the heart.
        ReputationInfo reputation = info.reputation();
        String repText = String.format(Locale.ROOT, reputation.reputation() > 0 ? "+%d" : "%d", reputation.reputation());
        int repWidth = HEART_SIZE + 2 + font.width(repText);
        int repX = x - INDICATOR_GAP - repWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(repX, y - 1, 0);
        graphics.pose().scale(HEART_SIZE / 16f, HEART_SIZE / 16f, 1f);
        graphics.renderItem(EMERALD, 0, 0);
        graphics.pose().popPose();
        int repColor = reputation.reputation() > 0 ? 0x55FF55 : reputation.reputation() < 0 ? 0xFF5555 : 0xFFFFFF;
        graphics.drawString(font, repText, repX + HEART_SIZE + 2, y, repColor, true);
        graphics.pose().popPose();

        int mouseX = event.getMouseX(), mouseY = event.getMouseY();
        boolean inRow = mouseY >= y - 2 && mouseY < y + HEART_SIZE + 1;
        if (inRow && mouseX >= x - 1 && mouseX < x + width + 1) {
            graphics.renderComponentTooltip(font, tooltip(info, level), mouseX, mouseY);
        } else if (inRow && mouseX >= repX - 1 && mouseX < repX + repWidth + 1) {
            graphics.renderComponentTooltip(font, reputationTooltip(reputation), mouseX, mouseY);
        }
    }

    private static List<Component> reputationTooltip(ReputationInfo reputation) {
        List<Component> lines = new ArrayList<>();
        int rep = reputation.reputation();
        ChatFormatting repColor = rep > 0 ? ChatFormatting.GREEN : rep < 0 ? ChatFormatting.RED : ChatFormatting.WHITE;
        lines.add(Component.translatable("happyvillagers.reputation.title",
                Component.literal(String.format(Locale.ROOT, rep > 0 ? "+%d" : "%d", rep)).withStyle(repColor)).withStyle(ChatFormatting.WHITE));
        for (ReputationInfo.Share share : reputation.breakdown()) {
            lines.add(Component.literal(String.format(Locale.ROOT, "%+d ", share.value()))
                    .append(Component.translatable("happyvillagers.reputation.gossip." + share.gossipType()))
                    .withStyle(share.value() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        lines.add(Component.empty());
        int min = reputation.minPriceDelta(), max = reputation.maxPriceDelta();
        if (min == 0 && max == 0) {
            lines.add(Component.translatable("happyvillagers.reputation.price_none").withStyle(ChatFormatting.GRAY));
        } else if (max > 0) {
            lines.add((min == max
                    ? Component.translatable("happyvillagers.reputation.price_up", max)
                    : Component.translatable("happyvillagers.reputation.price_up_range", Math.max(min, 0), max)).withStyle(ChatFormatting.RED));
        } else {
            lines.add((min == max
                    ? Component.translatable("happyvillagers.reputation.price_down", -min)
                    : Component.translatable("happyvillagers.reputation.price_down_range", -max, -min)).withStyle(ChatFormatting.GREEN));
        }
        if (reputation.hero()) {
            lines.add(Component.translatable("happyvillagers.reputation.hero").withStyle(ChatFormatting.GREEN));
        }
        lines.add(Component.translatable("happyvillagers.reputation.note").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static List<Component> tooltip(HappinessPayload info, HappinessLevel level) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("happyvillagers.tooltip.title",
                String.format(Locale.ROOT, "%.1f", info.happiness())).withStyle(ChatFormatting.WHITE));
        lines.add(level.displayName().copy().withStyle(ChatFormatting.ITALIC));
        if (!info.traits().isEmpty()) {
            lines.add(Trait.traitsLine(info.traits()));
        }

        if (info.target() != info.happiness()) {
            String key = info.target() > info.happiness() ? "happyvillagers.tooltip.rising" : "happyvillagers.tooltip.falling";
            lines.add(Component.translatable(key, String.format(Locale.ROOT, "%.1f", info.target())).withStyle(ChatFormatting.GRAY));
        }

        lines.add(Component.empty());
        for (HappinessFactor factor : info.factors()) {
            lines.add(factor.describe());
        }

        lines.add(Component.empty());
        if (info.priceModifier() == 0) {
            lines.add(Component.translatable("happyvillagers.tooltip.prices_normal").withStyle(ChatFormatting.GRAY));
        } else {
            String percent = String.format(Locale.ROOT, "%+d%%", Math.round(info.priceModifier() * 100));
            lines.add(Component.translatable("happyvillagers.tooltip.prices", percent)
                    .withStyle(info.priceModifier() < 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (info.lockedTrades() > 0) {
            lines.add(Component.translatable("happyvillagers.tooltip.locked", info.lockedTrades()).withStyle(ChatFormatting.RED));
        }
        if (info.bonusTrades() > 0) {
            lines.add(Component.translatable("happyvillagers.tooltip.bonus", info.bonusTrades()).withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }
}
