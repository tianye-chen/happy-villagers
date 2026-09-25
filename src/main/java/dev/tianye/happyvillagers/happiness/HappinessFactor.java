package dev.tianye.happyvillagers.happiness;

import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One line of the happiness breakdown.
 *
 * @param id       translation suffix, see {@code happyvillagers.factor.<id>} in the lang file
 * @param value    contribution to the target happiness (0 for "missing" entries)
 * @param detail   number shown in the description (volume, light level, window count...)
 * @param positive whether the line is shown as a positive (lime) or negative (red) factor
 */
public record HappinessFactor(String id, double value, int detail, boolean positive) {
    public static final StreamCodec<ByteBuf, HappinessFactor> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HappinessFactor::id,
            ByteBufCodecs.DOUBLE, HappinessFactor::value,
            ByteBufCodecs.VAR_INT, HappinessFactor::detail,
            ByteBufCodecs.BOOL, HappinessFactor::positive,
            HappinessFactor::new);

    public static HappinessFactor of(String id, double value, int detail) {
        return new HappinessFactor(id, value, detail, value >= 0);
    }

    /** A bonus the villager is missing out on. */
    public static HappinessFactor missing(String id) {
        return new HappinessFactor(id, 0, 0, false);
    }

    public Component describe() {
        MutableComponent line = Component.empty();
        if (value != 0) {
            line.append(formatValue(value)).append(" ");
        } else if (!positive) {
            line.append("✘ ");
        }
        line.append(Component.translatable("happyvillagers.factor." + id, detail));
        return line.withStyle(positive ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    public static String formatValue(double value) {
        // Area bonuses move in steps of 0.005-0.01, so keep a second decimal for small values.
        String pattern = Math.abs(value) < 0.1 ? "%+.2f" : "%+.1f";
        return String.format(Locale.ROOT, pattern, value);
    }
}
