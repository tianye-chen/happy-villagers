package dev.tianye.happyvillagers.happiness;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-villager happiness state, stored as a NeoForge data attachment. */
public class HappinessData {
    public static final Codec<HappinessData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("happiness").forGetter(d -> d.happiness),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(d -> d.initialized),
            Codec.BOOL.optionalFieldOf("quit", false).forGetter(d -> d.quit),
            Codec.LONG.optionalFieldOf("last_social", 0L).forGetter(d -> d.lastSocialTime),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bonus_uses", Map.of()).forGetter(d -> d.bonusUses)
    ).apply(i, HappinessData::new));

    // Persisted
    private double happiness;
    private boolean initialized;
    private boolean quit;
    private long lastSocialTime;
    private final Map<String, Integer> bonusUses;

    // Transient, recomputed by HappinessCalculator
    private double target;
    private boolean evaluated;
    private boolean homeless;
    private List<HappinessFactor> factors = List.of();

    public HappinessData() {
        this(5.0, false, false, 0L, Map.of());
    }

    private HappinessData(double happiness, boolean initialized, boolean quit, long lastSocialTime, Map<String, Integer> bonusUses) {
        this.happiness = happiness;
        this.target = happiness;
        this.initialized = initialized;
        this.quit = quit;
        this.lastSocialTime = lastSocialTime;
        this.bonusUses = new HashMap<>(bonusUses);
    }

    /** Raw happiness with full precision; drift works on this value. */
    public double rawHappiness() {
        return happiness;
    }

    /** Happiness rounded to one decimal, which is what players see and what all game logic uses. */
    public double happiness() {
        return round(happiness);
    }

    public void setHappiness(double value) {
        this.happiness = clamp(value);
    }

    public double target() {
        return round(target);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }

    public boolean hasQuit() {
        return quit;
    }

    public void setQuit(boolean quit) {
        this.quit = quit;
    }

    public long lastSocialTime() {
        return lastSocialTime;
    }

    public void setLastSocialTime(long time) {
        this.lastSocialTime = time;
    }

    public Map<String, Integer> bonusUses() {
        return bonusUses;
    }

    public boolean isEvaluated() {
        return evaluated;
    }

    public boolean isHomeless() {
        return homeless;
    }

    public List<HappinessFactor> factors() {
        return factors;
    }

    public void applyEvaluation(double target, boolean homeless, List<HappinessFactor> factors) {
        this.target = clamp(target);
        this.homeless = homeless;
        this.factors = List.copyOf(new ArrayList<>(factors));
        this.evaluated = true;
    }

    public static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(10.0, value));
    }
}
