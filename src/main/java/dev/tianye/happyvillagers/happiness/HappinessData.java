package dev.tianye.happyvillagers.happiness;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Per-villager happiness state, stored as a NeoForge data attachment. */
public class HappinessData {
    public static final Codec<HappinessData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("happiness").forGetter(d -> d.happiness),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(d -> d.initialized),
            Codec.BOOL.optionalFieldOf("quit", false).forGetter(d -> d.quit),
            Codec.LONG.optionalFieldOf("last_social", 0L).forGetter(d -> d.lastSocialTime),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bonus_uses", Map.of()).forGetter(d -> d.bonusUses),
            GlobalPos.CODEC.optionalFieldOf("home").forGetter(d -> d.homeAnchor),
            MoodEvent.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(d -> d.events),
            Codec.INT.optionalFieldOf("last_raid", 0).forGetter(d -> d.lastRaidId),
            ResourceLocation.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(d -> d.traits),
            Codec.BOOL.optionalFieldOf("traits_rolled", false).forGetter(d -> d.traitsRolled)
    ).apply(i, HappinessData::new));

    // Persisted
    private double happiness;
    private boolean initialized;
    private boolean quit;
    private long lastSocialTime;
    private final Map<String, Integer> bonusUses;
    /** Last spot the villager was seen in an enclosed space, used to find its home while it is outside. */
    private Optional<GlobalPos> homeAnchor;
    private final List<MoodEvent> events;
    /** Id of the last raid this villager celebrated surviving, so a victory only counts once. */
    private int lastRaidId;
    private List<ResourceLocation> traits;
    private boolean traitsRolled;

    // Transient, recomputed by HappinessCalculator
    private double target;
    private boolean evaluated;
    private boolean evaluationDue;
    private boolean homeless;
    private List<HappinessFactor> factors = List.of();

    public HappinessData() {
        this(5.0, false, false, 0L, Map.of(), Optional.empty(), List.of(), 0, List.of(), false);
    }

    private HappinessData(double happiness, boolean initialized, boolean quit, long lastSocialTime, Map<String, Integer> bonusUses,
                          Optional<GlobalPos> homeAnchor, List<MoodEvent> events, int lastRaidId,
                          List<ResourceLocation> traits, boolean traitsRolled) {
        this.happiness = happiness;
        this.target = happiness;
        this.initialized = initialized;
        this.quit = quit;
        this.lastSocialTime = lastSocialTime;
        this.bonusUses = new HashMap<>(bonusUses);
        this.homeAnchor = homeAnchor;
        this.events = new ArrayList<>(events);
        this.lastRaidId = lastRaidId;
        this.traits = List.copyOf(traits);
        this.traitsRolled = traitsRolled;
    }

    public List<ResourceLocation> traits() {
        return traits;
    }

    /** Whether this villager has been given its personality (existing villagers get one on first load). */
    public boolean traitsRolled() {
        return traitsRolled;
    }

    public void setTraits(List<ResourceLocation> traits) {
        this.traits = List.copyOf(traits);
        this.traitsRolled = true;
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

    public Optional<GlobalPos> homeAnchor() {
        return homeAnchor;
    }

    public void setHomeAnchor(@Nullable GlobalPos anchor) {
        this.homeAnchor = Optional.ofNullable(anchor);
    }

    /** Active mood events; call {@link #pruneEvents} first to drop expired ones. */
    public List<MoodEvent> events() {
        return events;
    }

    /** Adds a mood event, replacing (refreshing) any earlier event with the same id. */
    public void addEvent(String id, double value, long now, long duration) {
        events.removeIf(e -> e.id().equals(id));
        events.add(new MoodEvent(id, value, now, duration));
    }

    public void pruneEvents(long now) {
        events.removeIf(e -> e.isExpired(now));
    }

    public int lastRaidId() {
        return lastRaidId;
    }

    public void setLastRaidId(int raidId) {
        this.lastRaidId = raidId;
    }

    /** An evaluation is scheduled but hasn't run yet (e.g. the per-tick budget was used up). */
    public boolean isEvaluationDue() {
        return evaluationDue;
    }

    public void markEvaluationDue() {
        this.evaluationDue = true;
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
        this.evaluationDue = false;
    }

    public static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(10.0, value));
    }
}
