# Happy Villagers

A NeoForge mod for Minecraft **1.21.1** (NeoForge 21.1.251) that gives every villager a **Happiness** stat (0–10, one decimal) which drives its trades.

## Gameplay

- **Trade screen**: a heart and the villager's happiness appear on the right of the "Inventory" row. Hover over them to see:
  - the mood name
  - whether happiness is rising or falling, and toward what target
  - every factor (lime = positive, red = negative or missing)
  - the current price change, withheld trades and bonus trades
- **Jade**: looking at a villager shows `♥ 7.3 Happy`. Hold Shift (Jade's details key) to also see the trend and its biggest factors. Both have toggles in Jade's plugin settings.
- Happiness moves toward its target at **0.2 per minute**. Surroundings are re-evaluated every 10 s.

| Happiness | Mood | Trading |
|---|---|---|
| 0 | Severely Depressed | Quits their job (profession, level and trades are lost); won't take a new job until happiness reaches 1.0 |
| 0.1–0.9 | Depressed | Up to +100% prices; most trades withheld |
| 1–3.9 | Miserable, Very Unhappy, Unhappy | Higher prices; below 4.0 the highest-tier trades are withheld proportionally |
| 4–4.9 | Grumpy | Slightly higher prices |
| 5–5.9 | Neutral | **Exactly vanilla** |
| 6–9.9 | Content, Happy, Very Happy, Potential Man | Up to 30% cheaper; bonus trades unlock (e.g. diamond gear at 9) |
| 10 | Ecstatic | 30% cheaper; every profession offers a powerful **special trade** (see below) |

### Personality traits

Every villager has 1–2 traits that change how much each factor matters to it. The trade tooltip lists them, and factor lines a trait changed are tagged with its name. Babies have a 50% chance to inherit each parent trait; free slots are rolled randomly, and conflicting traits are never combined.

| Trait | Effect |
|---|---|
| Introvert | Crowding ×2, gossip bonus ×0.5 |
| Social Butterfly | Gossip bonus ×2; −1.0 when lonely |
| Night Owl | Light factor inverted: dim is pleasant, bright is not |
| Nature Lover | Greenery ×2; −0.5 without greenery |
| Claustrophobic | Living space score ×1.5; −1.0 without a way outside |
| Homebody | Roof, bed and door ×2; sky access ×0.5 |
| Free Spirit | Sky access ×2; +1.0 when homeless |
| Glutton | Food ×2; −1.0 without food |
| Brave | Unbothered by raids; grief ×0.5 |
| Sensitive | Being hurt ×2; grief ×1.5 |
| Devoted | Profession tastes ×2 |
| Cheerful | +0.5 baseline |

Traits are datapack JSON files in `data/<namespace>/happyvillagers/traits/`. Each has a `weight` (rarity), `conflicts` (other trait ids) and `modifiers` (keys are factor ids, `{ "multiply": 2.0 }` and/or `{ "add": -1.0 }`), plus an optional `base`. Datapacks can add traits or override these.

### Happiness factors

Target = `baseHappiness` (0) + the sum of the factors below, clamped to 0–10.

**Home.** A flood fill from the villager's feet measures its home. The fill:
- doesn't pass doors or gaps under 2 blocks tall;
- goes up to 5 blocks high from the villager's feet;
- stops at a 500-block cap. A villager whose fill hits the cap is **homeless**, and none of the home factors apply.

The fill is anchored to the villager's home, not to wherever it is standing, so working or wandering outside doesn't make it homeless. It starts from, in order:
1. its claimed bed;
2. its current spot, if that is enclosed (this spot is remembered);
3. the last enclosed spot it remembers.

A remembered home is forgotten once it no longer closes, e.g. a wall is knocked out.

| Factor | Value |
|---|---|
| Living space | 27 blocks = 0; −0.3 per block below; +0.01 per block up to 245; +0.005 per block beyond |
| Roof over the home | +0.5 |
| Claimed bed (vanilla bed claim) inside the home | +1 |
| Door on the home | +0.5 |
| Windows (glass blocks or panes in the shell) | +0.2 each, max +1 |
| Greenery that isn't part of the floor, walls or ceiling (`#happyvillagers:greenery`) | +0.1 each, max +1 |
| Gossiped with another villager in the last day (vanilla gossip) | +1.5 |
| Food in the villager's inventory or a container within 8 blocks | +1.5 |
| Light level at the villager | (light − 7) × 0.1 |
| Can reach open sky through a door or 2+ tall opening | +0.5 |
| Home contains blocks its profession likes (`#happyvillagers:tastes/<profession>`, e.g. bookshelves for librarians) | +0.1 each, max +0.5 |
| Crowding: more than 2 villagers live in the home (standing in it or with their bed there) | living space counts as `volume × 2 / residents` |
| Iron golem within 16 blocks | +0.5 |
| Raid in progress | −1.0 |

**Mood events** are short-lived: each fades linearly to nothing, and a repeat refreshes it rather than stacking.

| Event | Value | Lasts |
|---|---|---|
| Hurt by a player | −1.5 | 1 day |
| Saw a villager die (within 16 blocks, line of sight) | −2.0 | 2 days |
| Village saved from a raid | +1.5 | 2 days |

A lit 5×5 house with a bed, door, windows, a plant, food and neighbors lands around 7–8; a 7×7 house pushes it past 9. A homeless villager with food and neighbors sits around 4 (Grumpy); without them it sits near 1. A 1×1 trading-hall cell drops to 0.

## Special trades (happiness 10)

At happiness 10, each profession offers a powerful trade. Prices are before the 30% happiness discount.

| Profession | Special trade |
|---|---|
| Armorer | 40 emeralds + book → Protection V book |
| Butcher | 40 emeralds + gold block → Enchanted Golden Apple |
| Cartographer | 32 emeralds + compass → Heart of the Sea |
| Cleric | 40 emeralds → Totem of Undying |
| Farmer | 24 emeralds → 64 Golden Carrots |
| Fisherman | 40 emeralds + prismarine shard → Trident |
| Fletcher | 24 emeralds → 16 Arrows of Harming II |
| Leatherworker | 64 emeralds + 8 phantom membranes → Elytra |
| Librarian | 24 emeralds + book → Mending book |
| Mason | 40 emeralds → 2 Ancient Debris |
| Shepherd | 32 emeralds → 2 Shulker Shells |
| Toolsmith | 28 emeralds + book → Fortune III book |
| Weaponsmith | 32 emeralds + book → Sharpness V book |

They're set in the `[specialTrades]` config section: one list per profession (an empty list turns it off), plus `minHappiness` (10.0) and `minLevel` (1). Entries use `/give` item syntax:

```
costA | costB or - | result | maxUses | xp
24 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:mending':1}}] | 2 | 30
```

Enchantment levels above the vanilla maximum, like Protection V, carry over on the anvil. Combining two of them never goes higher: V + V stays V.

## Config

`config/happyvillagers-common.toml` is created on first launch. Every number above can be changed there.

### Performance

- Each villager re-checks its surroundings every `evaluationIntervalTicks` (default 200, i.e. 10 s). Villagers are staggered, and the first check after a chunk loads is spread over 2 s.
- `maxEvaluationsPerTick` (default 8) caps checks per server tick. Loading a big village spreads its work over a few ticks instead of causing a lag spike. A villager whose trade screen is opened is always checked immediately.
- The food search reads at most `foodMaxContainers` (default 32) of the nearest containers. Each container's result is cached for one interval and shared by all villagers.

Mood names are in `assets/happyvillagers/lang/en_us.json`. Profession tastes are the block tags `data/happyvillagers/tags/block/tastes/<profession>.json`; modded professions use `tastes/<namespace>/<profession>`.

### Bonus trades (datapack)

Bonus trades are JSON files in `data/<namespace>/happyvillagers/bonus_trades/`. The file id (e.g. `happyvillagers:librarian_mending`) is the trade's key. A datapack can add new files, or override the mod's by using the same path. `/reload` applies changes.

```json
{
  "profession": "minecraft:librarian",
  "min_happiness": 10.0,
  "min_level": 1,
  "cost_a": { "id": "minecraft:emerald", "count": 24 },
  "cost_b": { "id": "minecraft:book", "count": 1 },
  "result": {
    "id": "minecraft:enchanted_book",
    "components": { "minecraft:stored_enchantments": { "levels": { "minecraft:mending": 1 } } }
  },
  "max_uses": 3,
  "xp": 30
}
```

`cost_b`, `min_level` (default 1) and `xp` (default 0) are optional. The default datapack trades are the lower-tier ones: librarian (Unbreaking III book), armorer, toolsmith and weaponsmith (diamond gear), fletcher (spectral arrows), fisherman (sponges) and farmer (golden apple). The happiness-10 trades live in the config (see above).

## Advancements

Under the Adventure tab, after *What a Deal!*:
- **Customer Service**: trade with an Ecstatic villager.
- **Mending, Finally**: buy the happiness-10 Mending book.
- **Labor Strike**: be nearby when a villager quits its job.

## How trades are changed

When a player opens the trade screen, the villager's offer list is temporarily swapped for a happiness-adjusted view: prices change, low-happiness trades are withheld and bonus trades are added. The real list is restored when trading stops. If the villager is saved mid-trade, the real list is saved, so nothing is ever lost permanently. Bonus trades keep their own use counts and restock with the villager.

## Building

Requires **JDK 21** (for example `brew install --cask temurin@21`).

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew build              # -> build/libs/happyvillagers-1.2.1.jar
./gradlew runClient          # dev client with Jade
./gradlew runGameTestServer  # 23 in-world tests (homes, trading, special trades, anvil, traits, mood events, crowding, advancements, performance safeguards)
```

Jade is optional at runtime. It is compiled against and loaded in dev runs only.
