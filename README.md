# Shopkeeper

A single-player `/shop` for **Minecraft 26.3** (Fabric). Earn coins by selling what you farm, mine and cook, then spend them on tools, materials and rare items. It is a plain vanilla chest menu, so there is nothing to install on the client beyond the mod itself, and it works with controllers.

## Commands

| Command | What it does |
|---|---|
| `/shop` | Open the shop |
| `/shop balance` | Show your coins |
| `/shop reload` | Reload the config (operators only) |

## How it works

- **Buying:** click an item to buy 1, shift-click to buy a stack.
- **Selling:** click items in your own inventory while the shop is open. Only unmodified items sell (no renamed, enchanted or damaged gear).
- **Tiers:** the tabs along the top (Basics, Underground, Deep, Rare by default). Pay a tier's unlock price once and it stays unlocked permanently.

## Economy

Selling pays 30% of the buy price by default (rounded down). The config enforces a 25-40% range, so you can never sell something for more than you could buy it for, and buy-and-resell loops always lose coins. Profit comes from adding value yourself:

- **Growing:** wheat seeds cost 2 coins and a grown wheat sells for 6.
- **Cooking:** raw beef sells for 3, cooked beef sells for 15 (items can also set their own `sell` price).

## Config

On first run `config\shopkeeper.json` is created from the bundled default. Edit it, then run `/shop reload`. If the file is invalid, the old config stays active and the errors are logged and shown in chat. (If the file is already invalid at startup, the bundled default is used and the file is left untouched.)

```json
{
  "startingBalance": 100,
  "sellFraction": 0.30,
  "tiers": [
    {"id": "basics", "name": "Basics", "unlockPrice": 0, "icon": "minecraft:oak_log"},
    {"id": "underground", "name": "Underground", "unlockPrice": 500, "icon": "minecraft:iron_pickaxe"}
  ],
  "items": [
    {"id": "minecraft:wheat_seeds", "tier": "basics", "buy": 2},
    {"id": "minecraft:cooked_beef", "tier": "basics", "buy": 24, "sell": 15},
    {"id": "minecraft:iron_pickaxe", "tier": "underground", "buy": 120, "maxStack": 1}
  ]
}
```

| Field | Rules |
|---|---|
| `startingBalance` | Whole number, 0 or more |
| `sellFraction` | 0.25 to 0.40; default sell price is `floor(buy * sellFraction)` |
| `tiers[]` | `id`, `name`, `icon` (item id), `unlockPrice`; the first tier must cost 0 |
| `items[]` | `id`, `tier`, `buy` (above 0); optional `sell` (below `buy`), optional `maxStack` (1-99); max 45 items per tier |

An item whose sell price works out below 1 simply cannot be sold.

## Requirements

Minecraft 26.3, Fabric Loader 0.20.1 or newer, and Fabric API. [sgui](https://github.com/Patbox/sgui) is bundled inside the jar.

## Build and verify (PowerShell)

| Task | Command |
|---|---|
| Build + unit tests | `.\gradlew.bat build` |
| Everything, one-line results | `.\scripts\verify.ps1` (`-SkipGameTest`, `-NoDaemon`) |
| Launch the dev client | `.\gradlew.bat runClient` |

`verify.ps1` also runs the client gametest, which starts a real client window, drives input and saves screenshots to `build\run\clientGameTest\screenshots\`. It exits on its own (about 1.5 minutes).

Java 25 is required. Point Gradle at it once per machine in `~/.gradle/gradle.properties`: `org.gradle.java.home=C:/Program Files/Java/jdk-25.0.2`

## License

CC0-1.0, see `LICENSE`.
