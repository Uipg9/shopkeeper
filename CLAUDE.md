# Shopkeeper: working notes for Claude

- Minecraft 26.3 Fabric mod. Java 25. Unobfuscated: use Mojang names, never add mappings/yarn.
- TDD: write a failing JUnit test in `src/test` first, then implement. In-game checks go in
  `src/client/.../*ClientGameTest.java`.
- Shell is Windows PowerShell: use `.\gradlew.bat`. Filter Gradle output to errors only.
- Verify with `.\scripts\verify.ps1` (build + unit tests + client gametest). Read the gametest
  screenshots in `build\run\clientGameTest\screenshots\` to check what actually rendered.
- Never kill processes to stop the client: `runClientGameTest` exits by itself.
- Loom is non-remapping: use `implementation` + `include`, there is no `modImplementation`.

## Architecture (single-player `/shop`, server-side chest menu)

- `core/` is pure Java (no Minecraft imports; item ids are strings): `ShopConfig` (validated record),
  `ShopConfigParser` (never throws, returns errors), `Economy` (balances, unlocked tiers, buy/sell math),
  `ShopRouter` (maps clicks to actions; deny-by-default, anything not explicitly handled does nothing).
  Unit-test this package heavily (`EconomyTest`, `EconomyFuzzTest`, `ShopConfigParserTest`, `ShopRouterTest`).
- `ShopService` owns the loaded config + live `Economy`, reads/creates `config\shopkeeper.json` from
  `src/main/resources/shopkeeper/default_shop.json`, and `reload()` keeps the old config on any error.
- `ShopData` is the `SavedData` (codec-driven) that persists the economy; `ShopCommands` registers `/shop`,
  `/shop balance`, `/shop reload` (op only).
- `ShopGui` is the menu, built on sgui (bundled via `include`). It locks the player inventory so every
  click goes through our callbacks. Only unmodified stacks sell.
- Client code (`src/client`) is only the gametest; gameplay needs no client-side logic.
- Tests: `src/test` for logic and codec round trips, `ShopkeeperClientGameTest` for the real GUI.
- Mod Menu is dev-only (`localRuntime`); do not make it a hard dependency.
- `docs/spike-findings.md` records 26.3 API facts (SavedData, sgui, permissions, inventory checks).
