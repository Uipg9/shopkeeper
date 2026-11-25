# Shopkeeper

A single-player `/shop` for **Minecraft 26.3** (Fabric). Earn coins by selling what you farm, mine and cook, then spend them on tools, materials and rare items. It is a plain vanilla chest menu, so there is nothing to install on the client beyond the mod itself, and it works with controllers.

## Commands

| Command | What it does |
|---|---|
| `/shop` | Open the shop |
| `/shop balance` | Show your coins |
| `/shop sell` | Sell the stack in your main hand |
| `/shop sellall` | Sell every eligible stack in your inventory and hotbar (skips modified, unsellable and locked-tier stacks) and show one summary message |
| `/sell` | Sell the stack in your main hand (same as `/shop sell`) |
| `/sell all` | Sell everything eligible (same as `/shop sellall`) |
| `/buy <item> [quantity]` | Buy without opening the menu, e.g. `/buy oak_log 64` or `/buy "iron ingot" 3` (tab-completes; quantity 1-2304; buys only what fits in your inventory) |
| `/shop report` | Show today's earnings report |
| `/shop reload` | Reload the config (operators only) |

## How it works

- **Buying:** click an item to buy 1, shift-click to buy a stack.
- **Selling:** click items in your own inventory while the shop is open. Only unmodified items sell (no renamed, enchanted or damaged gear).
- **Tiers:** the tabs along the top (Basics, Underground, Deep, Rare by default). Pay a tier's unlock price once and it stays unlocked permanently.

## Economy

Selling pays 30% of the buy price by default (rounded down). The config enforces a 25-40% range, so you can never sell something for more than you could buy it for, and buy-and-resell loops always lose coins. Profit comes from adding value yourself:

- **Growing:** wheat seeds cost 2 coins and a grown wheat sells for 6.
- **Cooking:** raw beef sells for 3, cooked beef sells for 15 (items can also set their own `sell` price).

## Daily deals

Each Minecraft day one item is **HOT** (sells at x1.5, never above its buy price) and a different one is on **SALE** (buys at x0.75, never below its sell price). They are picked from the world seed and the day, so nothing is saved. Deals show in the item tooltip with a glow. Any tier can be picked, but a deal only helps if you have unlocked that tier.

## Daily earnings report

When the Minecraft day rolls over (or when you log in) you get one chat line for the previous day: coins earned, spent and net, items sold and bought, your best sale, and a verdict (Great day! / Decent day. / Quiet day. / Rough day...). Days with no activity are skipped. `/shop report` shows today's on demand.

## Black Market

A tab in the shop menu offers a few cheap, used vanilla gear pieces each day: partial durability and a couple of modest enchantments. Stock rotates every Minecraft day from the world seed, and each offer can be bought once per day. No new items are added to the game.

- Gear costs at most 70% of the new price.
- You can't sell it back (the shop only buys unmodified items).
- Gear from a tier you haven't unlocked shows "Requires <tier> tier" and can't be bought until you unlock that tier.

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
  ],
  "blackMarket": {"enabled": true, "slots": 6}
}
```

| Field | Rules |
|---|---|
| `startingBalance` | Whole number, 0 or more |
| `sellFraction` | 0.25 to 0.40; default sell price is `floor(buy * sellFraction)` |
| `tiers[]` | `id`, `name`, `icon` (item id), `unlockPrice`; the first tier must cost 0 |
| `items[]` | `id`, `tier`, `buy` (above 0); optional `sell` (below `buy`), optional `maxStack` (1-99); max 450 items per tier (the menu shows 45 per page with Previous/Next arrows); without an explicit `sell`, every item with `buy` of 2 or more sells for at least 1 |
| `blackMarket` | Optional. `enabled` (default `true`), `slots` (1-9, default 6) |

An item whose sell price works out below 1 simply cannot be sold.

Only gear that is present in `items[]` can appear in the Black Market: wooden, stone, golden, chainmail, iron, diamond and turtle tools and armor, plus bow, crossbow, fishing rod and shield. Netherite, mace, trident and elytra never appear.

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
