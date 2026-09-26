package dev.tianye.happyvillagers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public static final ModConfigSpec.IntValue MAX_EVALUATIONS_PER_TICK;

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
    public static final ModConfigSpec.IntValue FOOD_MAX_CONTAINERS;
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

    // ---------------------------------------------------------------- mood events
    public static final ModConfigSpec.DoubleValue HURT_BY_PLAYER;
    public static final ModConfigSpec.IntValue HURT_BY_PLAYER_TICKS;
    public static final ModConfigSpec.DoubleValue GRIEF;
    public static final ModConfigSpec.IntValue GRIEF_TICKS;
    public static final ModConfigSpec.IntValue GRIEF_RADIUS;
    public static final ModConfigSpec.DoubleValue RAID_SAVED;
    public static final ModConfigSpec.IntValue RAID_SAVED_TICKS;
    public static final ModConfigSpec.DoubleValue RAID_ONGOING;
    public static final ModConfigSpec.DoubleValue GOLEM_BONUS;
    public static final ModConfigSpec.IntValue GOLEM_RADIUS;

    // ---------------------------------------------------------------- tastes & crowding
    public static final ModConfigSpec.DoubleValue TASTES_BONUS;
    public static final ModConfigSpec.DoubleValue TASTES_MAX;
    public static final ModConfigSpec.BooleanValue CROWDING_ENABLED;
    public static final ModConfigSpec.IntValue FREE_RESIDENTS;

    // ---------------------------------------------------------------- jade
    public static final ModConfigSpec.IntValue JADE_MAX_FACTORS;

    // ---------------------------------------------------------------- traits
    public static final ModConfigSpec.BooleanValue TRAITS_ENABLED;
    public static final ModConfigSpec.IntValue MAX_TRAITS;

    // ---------------------------------------------------------------- special trades
    public static final ModConfigSpec.BooleanValue SPECIAL_TRADES_ENABLED;
    public static final ModConfigSpec.DoubleValue SPECIAL_TRADES_MIN_HAPPINESS;
    public static final ModConfigSpec.IntValue SPECIAL_TRADES_MIN_LEVEL;
    /** Profession path (vanilla professions) -> its list of special trade entries. */
    public static final Map<String, ModConfigSpec.ConfigValue<List<? extends String>>> SPECIAL_TRADES = new LinkedHashMap<>();

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
        MAX_EVALUATIONS_PER_TICK = BUILDER
                .comment("Most villager evaluations run in a single server tick. Villagers over the limit wait for the next tick,",
                        "so loading a large village spreads its work out instead of causing a lag spike.")
                .defineInRange("maxEvaluationsPerTick", 8, 1, 10000);
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
        FOOD_MAX_CONTAINERS = BUILDER.comment("Most containers checked for food, nearest first. Results are cached for evaluationIntervalTicks",
                        "and shared by all villagers.")
                .defineInRange("foodMaxContainers", 32, 1, 1024);
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
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC;

    private static void specialTrades(String profession, String... defaults) {
        SPECIAL_TRADES.put(profession, BUILDER.defineListAllowEmpty(profession, List.of(defaults), () -> "",
                o -> o instanceof String entry && (entry.isBlank() || entry.split("\\|").length == 5)));
    }

    static {
        BUILDER.comment("Short-lived mood changes. Each fades linearly to 0 over its duration (ticks; 24000 = 1 day).",
                "A repeat of the same event refreshes it instead of stacking.").push("moodEvents");
        HURT_BY_PLAYER = BUILDER.comment("Happiness change when a player hurts the villager.")
                .defineInRange("hurtByPlayer", -1.5, -10.0, 10.0);
        HURT_BY_PLAYER_TICKS = BUILDER.defineInRange("hurtByPlayerTicks", 24000, 20, Integer.MAX_VALUE);
        GRIEF = BUILDER.comment("Happiness change when the villager sees another villager die.")
                .defineInRange("grief", -2.0, -10.0, 10.0);
        GRIEF_TICKS = BUILDER.defineInRange("griefTicks", 48000, 20, Integer.MAX_VALUE);
        GRIEF_RADIUS = BUILDER.comment("How far away (blocks) a death can be seen.").defineInRange("griefRadius", 16, 1, 64);
        RAID_SAVED = BUILDER.comment("Happiness change after a raid on the village is defeated.")
                .defineInRange("raidSaved", 1.5, -10.0, 10.0);
        RAID_SAVED_TICKS = BUILDER.defineInRange("raidSavedTicks", 48000, 20, Integer.MAX_VALUE);
        RAID_ONGOING = BUILDER.comment("Happiness change while a raid is in progress around the villager.")
                .defineInRange("raidOngoing", -1.0, -10.0, 10.0);
        GOLEM_BONUS = BUILDER.comment("Bonus while an iron golem is nearby to protect the villager.")
                .defineInRange("golemBonus", 0.5, -10.0, 10.0);
        GOLEM_RADIUS = BUILDER.defineInRange("golemRadius", 16, 1, 64);
        BUILDER.pop();

        BUILDER.comment("Villagers like blocks that suit their profession in their home,",
                "listed in the block tag #happyvillagers:tastes/<profession>, e.g. #happyvillagers:tastes/librarian.").push("tastes");
        TASTES_BONUS = BUILDER.comment("Bonus per liked block in the home.").defineInRange("tastesBonus", 0.1, 0.0, 10.0);
        TASTES_MAX = BUILDER.comment("Maximum total tastes bonus.").defineInRange("tastesMax", 0.5, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.comment("Villagers living in the same home share its space. Residents are villagers standing in the home",
                "or whose claimed bed is in it.").push("crowding");
        CROWDING_ENABLED = BUILDER.define("enabled", true);
        FREE_RESIDENTS = BUILDER.comment("Residents that can share a home without penalty. Above this, each villager's living space",
                        "counts as volume * freeResidents / residents.")
                .defineInRange("freeResidents", 2, 1, 20);
        BUILDER.pop();

        BUILDER.push("jade");
        JADE_MAX_FACTORS = BUILDER.comment("How many factors Jade lists while its details key (Shift) is held.")
                .defineInRange("jadeMaxFactors", 5, 0, 32);
        BUILDER.pop();

        BUILDER.comment("Each villager gets personality traits (datapack files in data/<namespace>/happyvillagers/traits/)",
                "that change how much each happiness factor matters to it. Babies can inherit their parents' traits.").push("traits");
        TRAITS_ENABLED = BUILDER.define("enabled", true);
        MAX_TRAITS = BUILDER.comment("Traits per villager: 1 to this many.").defineInRange("maxTraits", 2, 1, 5);
        BUILDER.pop();

        BUILDER.comment("Powerful trades each profession offers only when it is at its very happiest.",
                "Each profession takes a list of entries (empty list = none). Entry format, fields separated by '|':",
                "  costA | costB or - | result | maxUses | xp",
                "Items use /give syntax with an optional count in front, e.g. '24 minecraft:emerald' or",
                "  minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:mending':1}}]").push("specialTrades");
        SPECIAL_TRADES_ENABLED = BUILDER.define("enabled", true);
        SPECIAL_TRADES_MIN_HAPPINESS = BUILDER.comment("Happiness needed to offer special trades.")
                .defineInRange("minHappiness", 10.0, 0.0, 10.0);
        SPECIAL_TRADES_MIN_LEVEL = BUILDER.comment("Villager level needed to offer special trades (1 = Novice, 5 = Master).")
                .defineInRange("minLevel", 1, 1, 5);
        specialTrades("armorer", "40 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:protection':5}}] | 1 | 30");
        specialTrades("butcher", "40 minecraft:emerald | 1 minecraft:gold_block | 1 minecraft:enchanted_golden_apple | 1 | 30");
        specialTrades("cartographer", "32 minecraft:emerald | 1 minecraft:compass | 1 minecraft:heart_of_the_sea | 1 | 30");
        specialTrades("cleric", "40 minecraft:emerald | - | 1 minecraft:totem_of_undying | 1 | 30");
        specialTrades("farmer", "24 minecraft:emerald | - | 64 minecraft:golden_carrot | 2 | 20");
        specialTrades("fisherman", "40 minecraft:emerald | 1 minecraft:prismarine_shard | 1 minecraft:trident | 1 | 30");
        specialTrades("fletcher", "24 minecraft:emerald | - | 16 minecraft:tipped_arrow[potion_contents={potion:'minecraft:strong_harming'}] | 2 | 20");
        specialTrades("leatherworker", "64 minecraft:emerald | 8 minecraft:phantom_membrane | 1 minecraft:elytra | 1 | 30");
        specialTrades("librarian", "24 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:mending':1}}] | 2 | 30");
        specialTrades("mason", "40 minecraft:emerald | - | 2 minecraft:ancient_debris | 1 | 30");
        specialTrades("shepherd", "32 minecraft:emerald | - | 2 minecraft:shulker_shell | 1 | 30");
        specialTrades("toolsmith", "28 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:fortune':3}}] | 1 | 30");
        specialTrades("weaponsmith", "32 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:sharpness':5}}] | 1 | 30");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private HappyConfig() {}
}
