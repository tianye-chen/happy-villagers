package dev.tianye.happyvillagers.client;

import dev.tianye.happyvillagers.HappyVillagers;
import dev.tianye.happyvillagers.happiness.HappinessFactor;
import dev.tianye.happyvillagers.happiness.HappinessLevel;
import dev.tianye.happyvillagers.network.HappinessPayload;
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
        graphics.pose().popPose();

        int mouseX = event.getMouseX(), mouseY = event.getMouseY();
        if (mouseX >= x - 1 && mouseX < x + width + 1 && mouseY >= y - 2 && mouseY < y + HEART_SIZE + 1) {
            graphics.renderComponentTooltip(font, tooltip(info, level), mouseX, mouseY);
        }
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
