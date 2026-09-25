package dev.tianye.happyvillagers.happiness;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A short-lived mood change that fades linearly from {@code value} to 0 over {@code duration} ticks. */
public record MoodEvent(String id, double value, long start, long duration) {
    public static final Codec<MoodEvent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(MoodEvent::id),
            Codec.DOUBLE.fieldOf("value").forGetter(MoodEvent::value),
            Codec.LONG.fieldOf("start").forGetter(MoodEvent::start),
            Codec.LONG.fieldOf("duration").forGetter(MoodEvent::duration)
    ).apply(i, MoodEvent::new));

    public long remaining(long now) {
        return Math.max(0L, start + duration - now);
    }

    public boolean isExpired(long now) {
        return remaining(now) == 0L || now < start;
    }

    public double valueAt(long now) {
        return isExpired(now) ? 0.0 : value * remaining(now) / (double) duration;
    }
}
