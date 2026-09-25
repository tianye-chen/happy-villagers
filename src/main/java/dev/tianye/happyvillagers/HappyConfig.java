package dev.tianye.happyvillagers;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config, written to {@code config/happyvillagers-common.toml}.
 * Values are read live, so edits made while the game is running apply on the next evaluation.
 */
public final class HappyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- general
    public static final ModConfigSpec.DoubleValue BASE_HAPPINESS;
    public static final ModConfigSpec.DoubleValue INITIAL_HAPPINESS;
    public static final ModConfigSpec.DoubleValue CHANGE_PER_MINUTE;
    public static final ModConfigSpec.IntValue EVALUATION_INTERVAL;

    // ---------------------------------------------------------------- living area
    public static final ModConfigSpec.IntValue NEUTRAL_VOLUME;
    public static final ModConfigSpec.IntValue LARGE_VOLUME;
    public static final ModConfigSpec.DoubleValue BONUS_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue BONUS_PER_BLOCK_LARGE;
    public static final ModConfigSpec.DoubleValue PENALTY_PER_BLOCK;
    public static final ModConfigSpec.IntValue MAX_VOLUME;
    public static final ModConfigSpec.IntValue MAX_HEIGHT;

    // ---------------------------------------------------------------- home
    public static final ModConfigSpec.DoubleValue ROOF_BONUS;
    public static final ModConfigSpec.DoubleValue ROOF_COVERAGE;
    public static final ModConfigSpec.DoubleValue BED_BONUS;
    public static final ModConfigSpec.DoubleValue DOOR_BONUS;
    public static final ModConfigSpec.DoubleValue WINDOW_BONUS;
    public static final ModConfigSpec.DoubleValue WINDOW_MAX;
    public static final ModConfigSpec.DoubleValue GREENERY_BONUS;
    public static final ModConfigSpec.DoubleValue GREENERY_MAX;

    // ---------------------------------------------------------------- needs
    public static final ModConfigSpec.DoubleValue SOCIAL_BONUS;
    public static final ModConfigSpec.IntValue SOCIAL_MEMORY_TICKS;
    public static final ModConfigSpec.DoubleValue FOOD_BONUS;
    public static final ModConfigSpec.IntValue FOOD_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue LIGHT_NEUTRAL;
    public static final ModConfigSpec.DoubleValue LIGHT_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue SKY_ACCESS_BONUS;
    public static final ModConfigSpec.DoubleValue NO_SKY_ACCESS_PENALTY;

    // ---------------------------------------------------------------- trading
    public static final ModConfigSpec.DoubleValue NEUTRAL_MIN;
    public static final ModConfigSpec.DoubleValue NEUTRAL_MAX;
    public static final ModConfigSpec.DoubleValue MAX_DISCOUNT;
    public static final ModConfigSpec.DoubleValue MAX_MARKUP;
    public static final ModConfigSpec.DoubleValue LOCK_TRADES_BELOW;
    public static final ModConfigSpec.DoubleValue QUIT_AT;
    public static final ModConfigSpec.DoubleValue REHIRE_AT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BONUS_TRADES;

    static {
        BUILDER.push("general");
        BASE_HAPPINESS = BUILDER
                .comment("Happiness a villager has before any factor is applied. Target = base + sum of factors, clamped to 0-10.")
                .defineInRange("baseHappiness", 0.0, -10.0, 10.0);
        INITIAL_HAPPINESS = BUILDER
                .comment("Happiness given to a villager the first time the mod sees it (5.0 = Neutral = vanilla trades).")
                .defineInRange("initialHappiness", 5.0, 0.0, 10.0);
        CHANGE_PER_MINUTE = BUILDER
                .comment("How fast current happiness moves toward its target, in points per minute (20 min = 1 Minecraft day).")
                .defineInRange("changePerMinute", 0.2, 0.0, 10.0);
        EVALUATION_INTERVAL = BUILDER
                .comment("Ticks between re-evaluations of a villager's surroundings (flood fill, food, light...). 20 ticks = 1 second.")
                .defineInRange("evaluationIntervalTicks", 200, 20, 72000);
        BUILDER.pop();

        BUILDER.comment("Living area is measured by flood filling the air around the villager.",
                "The fill cannot pass through doors or openings less than 2 blocks tall, and cannot go more than",
                "maxHeight blocks above the villager's feet. If it reaches maxVolume without closing, the villager is homeless",
                "and none of the home factors (area, roof, bed, door, windows, greenery) apply.").push("livingArea");
        NEUTRAL_VOLUME = BUILDER.comment("Volume (in blocks) that counts as neutral (0).")
                .defineInRange("neutralVolume", 27, 1, 100000);
        LARGE_VOLUME = BUILDER.comment("Volume after which each extra block gives bonusPerBlockLarge instead of bonusPerBlock (7x7x5 = 245).")
                .defineInRange("largeVolume", 245, 1, 100000);
        BONUS_PER_BLOCK = BUILDER.comment("Happiness per block above neutralVolume, up to largeVolume.")
                .defineInRange("bonusPerBlock", 0.01, 0.0, 10.0);
        BONUS_PER_BLOCK_LARGE = BUILDER.comment("Happiness per block above largeVolume.")
                .defineInRange("bonusPerBlockLarge", 0.005, 0.0, 10.0);
        PENALTY_PER_BLOCK = BUILDER.comment("Happiness lost per block below neutralVolume.")
                .defineInRange("penaltyPerBlock", 0.3, 0.0, 10.0);
        MAX_VOLUME = BUILDER.comment("Flood fill volume cap. Reaching it means the space is not closed (homeless).")
                .defineInRange("maxVolume", 500, 8, 20000);
        MAX_HEIGHT = BUILDER.comment("How many blocks tall the fill may go, counting the villager's own block.")
                .defineInRange("maxHeight", 5, 2, 32);
        BUILDER.pop();

        BUILDER.push("home");
        ROOF_BONUS = BUILDER.comment("Bonus for a roof over the home.").defineInRange("roofBonus", 0.5, -10.0, 10.0);
        ROOF_COVERAGE = BUILDER.comment("Fraction of the home's floor columns that must be covered for it to count as roofed.")
                .defineInRange("roofCoverage", 0.9, 0.0, 1.0);
        BED_BONUS = BUILDER.comment("Bonus when the bed the villager has claimed (vanilla 'home' memory) is inside its home.")
                .defineInRange("bedBonus", 1.0, -10.0, 10.0);
        DOOR_BONUS = BUILDER.comment("Bonus for at least one door on the home's boundary.").defineInRange("doorBonus", 0.5, -10.0, 10.0);
        WINDOW_BONUS = BUILDER.comment("Bonus per glass block / pane on the home's boundary.").defineInRange("windowBonus", 0.2, 0.0, 10.0);
        WINDOW_MAX = BUILDER.comment("Maximum total window bonus.").defineInRange("windowMax", 1.0, 0.0, 10.0);
        GREENERY_BONUS = BUILDER.comment("Bonus per greenery block (#happyvillagers:greenery) inside the home that isn't part of its floor, walls or ceiling.")
                .defineInRange("greeneryBonus", 0.1, 0.0, 10.0);
        GREENERY_MAX = BUILDER.comment("Maximum total greenery bonus.").defineInRange("greeneryMax", 1.0, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("needs");
        SOCIAL_BONUS = BUILDER.comment("Bonus when the villager has gossiped with another villager recently (vanilla gossip).")
                .defineInRange("socialBonus", 1.5, -10.0, 10.0);
        SOCIAL_MEMORY_TICKS = BUILDER.comment("How long (ticks) a gossip session keeps counting. 24000 = 1 Minecraft day.")
                .defineInRange("socialMemoryTicks", 24000, 20, Integer.MAX_VALUE);
        FOOD_BONUS = BUILDER.comment("Bonus when the villager has food in its inventory or in a storage container nearby.")
                .defineInRange("foodBonus", 1.5, -10.0, 10.0);
        FOOD_SEARCH_RADIUS = BUILDER.comment("Radius (blocks) searched for storage containers holding food.")
                .defineInRange("foodSearchRadius", 8, 0, 32);
        LIGHT_NEUTRAL = BUILDER.comment("Light level at the villager that counts as neutral. Factor = (light - lightNeutral) * lightPerLevel.")
                .defineInRange("lightNeutral", 7, 0, 15);
        LIGHT_PER_LEVEL = BUILDER.comment("Happiness per light level above/below lightNeutral.")
                .defineInRange("lightPerLevel", 0.1, 0.0, 10.0);
        SKY_ACCESS_BONUS = BUILDER.comment("Bonus when the villager can reach the open sky through a door or a 2+ block tall opening.")
                .defineInRange("skyAccessBonus", 0.5, 0.0, 10.0);
        NO_SKY_ACCESS_PENALTY = BUILDER.comment("Penalty when the villager is shut in with no way to reach the sky (0 = off).")
                .defineInRange("noSkyAccessPenalty", 0.0, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.push("trading");
        NEUTRAL_MIN = BUILDER.comment("Lower bound of the neutral band. Within [neutralMin, neutralMax] trades are exactly vanilla.")
                .defineInRange("neutralMin", 5.0, 0.0, 10.0);
        NEUTRAL_MAX = BUILDER.comment("Upper bound of the neutral band.").defineInRange("neutralMax", 6.0, 0.0, 10.0);
        MAX_DISCOUNT = BUILDER.comment("Price reduction at happiness 10 (0.3 = 30% cheaper). Scales linearly from neutralMax.")
                .defineInRange("maxDiscount", 0.3, 0.0, 1.0);
        MAX_MARKUP = BUILDER.comment("Price increase at happiness 0 (1.0 = twice the price). Scales linearly from neutralMin.")
                .defineInRange("maxMarkup", 1.0, 0.0, 10.0);
        LOCK_TRADES_BELOW = BUILDER.comment("Below this happiness the villager's highest-tier trades are withheld, proportionally",
                        "(e.g. at half this value only half of the trades are offered; at least one is always offered).")
                .defineInRange("lockTradesBelow", 4.0, 0.0, 10.0);
        QUIT_AT = BUILDER.comment("At or below this happiness the villager quits its job: it loses its profession, level and trades.")
                .defineInRange("quitAt", 0.0, -1.0, 10.0);
        REHIRE_AT = BUILDER.comment("A villager that quit refuses to take a new job until its happiness reaches this value.")
                .defineInRange("rehireAt", 1.0, 0.0, 10.0);
        BONUS_TRADES = BUILDER.comment(
                        "Extra trades unlocked by happy villagers. Format (fields separated by '|'):",
                        "  profession | minHappiness | minLevel | costA | costB or - | result | maxUses | xp",
                        "Items use /give syntax with an optional count in front, e.g. '24 minecraft:emerald' or",
                        "  minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:mending':1}}]")
                .defineListAllowEmpty("bonusTrades", List.of(
                        "minecraft:librarian | 10.0 | 1 | 24 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:mending':1}}] | 3 | 30",
                        "minecraft:librarian | 9.0 | 3 | 20 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:unbreaking':3}}] | 3 | 20",
                        "minecraft:armorer | 9.0 | 3 | 28 minecraft:emerald | - | minecraft:diamond_chestplate[enchantments={levels:{'minecraft:protection':3}}] | 2 | 30",
                        "minecraft:toolsmith | 9.0 | 3 | 24 minecraft:emerald | - | minecraft:diamond_pickaxe[enchantments={levels:{'minecraft:efficiency':4,'minecraft:unbreaking':3}}] | 2 | 30",
                        "minecraft:weaponsmith | 9.0 | 3 | 24 minecraft:emerald | - | minecraft:diamond_sword[enchantments={levels:{'minecraft:sharpness':4}}] | 2 | 30",
                        "minecraft:fletcher | 9.0 | 3 | 20 minecraft:emerald | - | minecraft:crossbow[enchantments={levels:{'minecraft:multishot':1,'minecraft:quick_charge':3}}] | 2 | 30",
                        "minecraft:fisherman | 9.0 | 3 | 20 minecraft:emerald | - | minecraft:fishing_rod[enchantments={levels:{'minecraft:luck_of_the_sea':3,'minecraft:lure':3}}] | 2 | 30",
                        "minecraft:farmer | 8.0 | 2 | 10 minecraft:emerald | 1 minecraft:gold_ingot | 1 minecraft:golden_apple | 4 | 15",
                        "minecraft:cleric | 10.0 | 5 | 40 minecraft:emerald | - | 1 minecraft:totem_of_undying | 1 | 30"),
                        () -> "minecraft:none | 10.0 | 1 | 1 minecraft:emerald | - | 1 minecraft:stick | 1 | 1",
                        o -> o instanceof String s && s.split("\\|").length == 8);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private HappyConfig() {}
}
