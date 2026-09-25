# Happy Villagers

A NeoForge mod for Minecraft **1.21.1** (NeoForge 21.1.251) that gives every villager a **Happiness** stat (0–10, one decimal) which drives its trades.

## Gameplay

- **Trade screen**: a heart and the villager's happiness appear on the right of the "Inventory" row. Hover over them to see:
  - the mood name
  - whether happiness is rising or falling, and toward what target
  - every factor (lime = positive, red = negative or missing)
  - the current price change, withheld trades and bonus trades
- **Jade**: looking at a villager shows `♥ 7.3 Happy` (toggle: *Villager Happiness* in Jade's plugin settings).
- Happiness moves toward its target at **0.2 per minute**. Surroundings are re-evaluated every 10 s.

| Happiness | Mood | Trading |
|---|---|---|
| 0 | Severely Depressed | Quits their job (profession, level and trades are lost); won't take a new job until happiness reaches 1.0 |
| 0.1–0.9 | Depressed | Up to +100% prices; most trades withheld |
| 1–3.9 | Miserable, Very Unhappy, Unhappy | Higher prices; below 4.0 the highest-tier trades are withheld proportionally |
| 4–4.9 | Grumpy | Slightly higher prices |
| 5–5.9 | Neutral | **Exactly vanilla** |
| 6–9.9 | Content, Happy, Very Happy, Potential Man | Up to 30% cheaper; bonus trades unlock (e.g. enchanted diamond gear at 9) |
| 10 | Ecstatic | 30% cheaper; librarians offer **Mending** |

### Happiness factors

Target = `baseHappiness` (0) + the sum of the factors below, clamped to 0–10.

**Home.** A flood fill from the villager's feet measures its home. The fill:
- doesn't pass doors or gaps under 2 blocks tall;
- goes up to 5 blocks high from the villager's feet;
- stops at a 500-block cap. A villager whose fill hits the cap is **homeless**, and none of the home factors apply.

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

A lit 5×5 house with a bed, door, windows, a plant, food and neighbours lands around 7–8; a 7×7 house pushes it past 9. A homeless villager with food and neighbours sits around 4 (Grumpy); without them it sits near 1. A 1×1 trading-hall cell drops to 0.

## Config

`config/happyvillagers-common.toml` is created on first launch. Every number above can be changed there, and so can the bonus trade list. Bonus trades use `/give` item syntax:

```
profession | minHappiness | minLevel | costA | costB or - | result | maxUses | xp
minecraft:librarian | 10.0 | 1 | 24 minecraft:emerald | 1 minecraft:book | minecraft:enchanted_book[stored_enchantments={levels:{'minecraft:mending':1}}] | 3 | 30
```

Mood names are in `assets/happyvillagers/lang/en_us.json`.

## How trades are changed

When a player opens the trade screen, the villager's offer list is temporarily swapped for a happiness-adjusted view: prices change, low-happiness trades are withheld and bonus trades are added. The real list is restored when trading stops. If the villager is saved mid-trade, the real list is saved, so nothing is ever lost permanently. Bonus trades keep their own use counts and restock with the villager.

## Building

Requires **JDK 21** (for example `brew install --cask temurin@21`).

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew build              # -> build/libs/happyvillagers-1.0.0.jar
./gradlew runClient          # dev client with Jade
./gradlew runGameTestServer  # 10 in-world tests (home detection, trading, quitting, config parsing)
```

Jade is optional at runtime. It is compiled against and loaded in dev runs only.
