package dev.tianye.happyvillagers.happiness;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Decorative mood names. Ranges are [lower, upper) except the two exact end points 0 and 10. */
public enum HappinessLevel {
    SEVERELY_DEPRESSED("severely_depressed", ChatFormatting.DARK_RED),
    DEPRESSED("depressed", ChatFormatting.DARK_RED),
    MISERABLE("miserable", ChatFormatting.RED),
    VERY_UNHAPPY("very_unhappy", ChatFormatting.RED),
    UNHAPPY("unhappy", ChatFormatting.GOLD),
    GRUMPY("grumpy", ChatFormatting.GOLD),
    NEUTRAL("neutral", ChatFormatting.YELLOW),
    CONTENT("content", ChatFormatting.YELLOW),
    HAPPY("happy", ChatFormatting.GREEN),
    VERY_HAPPY("very_happy", ChatFormatting.GREEN),
    POTENTIAL_MAN("potential_man", ChatFormatting.AQUA),
    ECSTATIC("ecstatic", ChatFormatting.LIGHT_PURPLE);

    private final String key;
    private final ChatFormatting color;

    HappinessLevel(String key, ChatFormatting color) {
        this.key = key;
        this.color = color;
    }

    public static HappinessLevel of(double happiness) {
        double h = HappinessData.round(happiness);
        if (h <= 0.0) return SEVERELY_DEPRESSED;
        if (h >= 10.0) return ECSTATIC;
        if (h < 1.0) return DEPRESSED;
        // 1.0-1.9 -> MISERABLE (index 2) ... 9.0-9.9 -> POTENTIAL_MAN (index 10)
        return values()[(int) Math.floor(h) + 1];
    }

    public ChatFormatting color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("happyvillagers.level." + key).withStyle(color);
    }
}
