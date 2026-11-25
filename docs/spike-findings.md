# Spike findings (MC 26.3, Fabric, sgui 2.2.1+26.3)

Compiled code lives in `src/main/java/dev/brian/shopkeeper/spike/` (SpikeGui, SpikeData, SpikeMod; scratch, delete later).
`SpikeMod.register()` is NOT wired into `Shopkeeper.onInitialize`; call it to try `/shopspike` in game.
Nothing here was run in a client or server: everything below is compile-verified plus reading sgui sources
(`sgui-2.2.1+26.3-sources.jar`) and `javap` on `minecraft-common-deobf-26.3.jar`. Runtime click behaviour is read from
the sgui mixin, not observed.

## Verdict: sgui WORKS on 26.3 (resolves, compiles, jar-in-jars). Runtime behaviour: unverified in game.

- Repo: `https://maven.nucleoid.xyz/` (group `eu.pb4`). Published: 2.2.0+26.3, 2.2.1+26.3.
- Loom 1.19.4 plugin `net.fabricmc.fabric-loom` is non-remapping: **`modImplementation` does not exist**
  ("Could not find method modImplementation()"). Use `implementation` + `include`.
- `build.gradle`: Nucleoid maven (content filter `includeGroup "eu.pb4"`), `implementation "eu.pb4:sgui:${sgui_version}"`,
  `include "eu.pb4:sgui:${sgui_version}"`. `gradle.properties`: `sgui_version=2.2.1+26.3`.
- `.\gradlew.bat build` passes; `build/libs/shopkeeper-1.0.0.jar` contains `META-INF/jars/sgui-2.2.1+26.3.jar`.
- fabric.mod.json was NOT changed: jar-in-jar needs no `depends` entry. (Optionally add `"sgui": "*"`; not needed.)

## sgui click handling (from `ServerGamePacketListenerImplMixin` source)

```java
super(MenuType.GENERIC_9x6, player, false);   // false: player inv is real vanilla slots 54..89, not gui slots
setLockPlayerInventory(true);                  // route ALL clicks through gui, none to vanilla
@Override public boolean onAnyClick(int index, ClickType type, ContainerInput action) { /* log */ return false; }
```

- Mixin on `handleContainerClick`: `ignore = gui.onAnyClick(...)`. If `ignore && !lockPlayerInventory && (slot >= size || slot < 0 || customSlot != null)`
  it returns WITHOUT cancelling, i.e. **vanilla handles the click**. Defaults are `onAnyClick -> true`, lock `false`, so by default
  clicks on the player's own inventory (slot >= 54) and outside-window (-999) are NOT cancelled (player can move own items,
  shift-click from own inventory calls `quickMove` which can push items into the chest region).
- To cancel everything: `setLockPlayerInventory(true)` OR `onAnyClick` returns `false`. The spike does both.
- Once past that check, `gui.click(...)` runs the element callback (`GuiElementBuilder.setCallback((index, ClickType, ContainerInput, SlotBasedGui) -> ...)`)
  then `onClick(index, type, action, element)` (default false). The menu's own `clicked()` is never called, so shift-click,
  number-key swap (`NUM_KEY_1..9`, `OFFHAND_SWAP`), double-click (`MOUSE_DOUBLE_CLICK`), drag (`MOUSE_*_DRAG_START/ADD/END`),
  drop (`DROP`, `CTRL_DROP`), outside click (`MOUSE_LEFT_OUTSIDE`) all only reach our callbacks and change nothing server-side.
  Client-side optimistic changes are corrected by the next `broadcastChanges` (the mixin pre-writes the client's claimed slot state
  into remote cache then resumes updates); shift/double-click/drag-end additionally call `SguiUtils.sendCurrentMenu`. Confirm visually.
- Slot index map for GENERIC_9x6 with `manipulatePlayerSlots=false`: 0..53 chest, 54..80 player main (27), 81..89 hotbar, -999 outside.
  `ClickType` enum: `MOUSE_LEFT, MOUSE_RIGHT, MOUSE_LEFT_SHIFT, MOUSE_RIGHT_SHIFT, NUM_KEY_1..9, MOUSE_MIDDLE, DROP, CTRL_DROP,
  MOUSE_LEFT_OUTSIDE, MOUSE_RIGHT_OUTSIDE, *_DRAG_START/ADD/END, MOUSE_DOUBLE_CLICK, OFFHAND_SWAP, UNKNOWN`; fields `isLeft isRight shift numKey isDragging`.
- Lifecycle hooks on `GuiLike`: `onOpen()`, `onPlayerClose(boolean screenHandlerIsClosed)`, `onManualClose()`, `onRemoved()`, `afterRemoval()`,
  `onTick()`, `canPlayerClose()`, `handleException(Throwable)`. (There is NO `onClose()`.) `gui.close()` / `gui.isOpen()`, `setTitle(Component)`.
- Items: `new GuiElementBuilder(Items.X).setCount(n).setName(c).setItemName(c).addLoreLine(c).hideDefaultTooltip().hideTooltip().glow().setCallback(...)`.
  `setSlot(int, GuiElementBuilder)`, `setSlot(int, GuiElement)`, `clearSlot(int)`. `builder.asStack()` gives the ItemStack.
  `addLoreLine` styles non-italic; `addLoreLineRaw`/`setLoreRaw` do not; `setName` wipes italics too (vanilla would italicise custom names; check in game).

## 26.3 vanilla API facts (all compile-checked)

```java
// Custom name (italic off), lore, hidden tooltip, with vanilla components only
ItemStack s = new ItemStack(Items.DIAMOND, 3);                      // ItemStack(ItemLike, int)
s.set(DataComponents.CUSTOM_NAME, Component.literal("X").withStyle(Style.EMPTY.withItalic(false)));
s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("lore"))));   // ItemLore(List<Component>)
s.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>())); // (hideTooltip, hiddenComponents)
// or TooltipDisplay.DEFAULT.withHidden(DataComponents.LORE, true)

// "unmodified stack" check
boolean plain = stack.getComponentsPatch().isEmpty()
    && ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance());
// also: ItemStack.matches / isSameItem / matchesIgnoringComponents(a, b, Predicate<DataComponentType<?>>); Item.components(), stack.getPrototype()
// Caveat: damaged tools have a DAMAGE patch, so they count as modified (usually what you want).
```

Inventory capacity: there is no `canAdd`. `Inventory.add(ItemStack)` returns boolean and mutates the passed stack (leftover stays in it).
To check without mutating: `Inventory.add(stack.copy())` is not safe (it really inserts). Count room manually:

```java
int room = 0; int max = stack.getMaxStackSize();
for (ItemStack in : player.getInventory().getNonEquipmentItems()) {   // 36 slots: hotbar+main
    if (in.isEmpty()) room += max;
    else if (ItemStack.isSameItemSameComponents(in, stack)) room += max - in.getCount();
}
// also available: getFreeSlot(), getSlotWithRemainingSpace(stack), placeItemBackInInventory(stack, Prediction)
```

SavedData (new API: no `computeIfAbsent(factory, id)`; no `save(CompoundTag)` override; codec-driven):

```java
public class SpikeData extends SavedData {
    public static final Codec<SpikeData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("balances").forGetter(d -> d.balances)).apply(i, ...));
    public static final SavedDataType<SpikeData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("shopkeeper", "spike"), SpikeData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    static SpikeData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    // mutate, then setDirty();   (SavedData.setDirty() / setDirty(boolean) / isDirty())
}
```

- `SavedDataStorage` (package `net.minecraft.world.level.storage`; old name DimensionDataStorage is gone): `computeIfAbsent(type)`, `get(type)`, `set(type, value)`.
- `SavedDataType` is a record `(Identifier id, Supplier<T> constructor, Codec<T> codec, DataFixTypes dataFixType)`.
  `dataFixType` must be non-null: bytecode of `SavedDataStorage.readTagFromDisk` calls `DataFixTypes.update` without a null check.
  I used `SAVED_DATA_COMMAND_STORAGE` (untested at runtime; a round trip in a gametest/server run would confirm).
- Saved on server save/stop automatically once dirty.

Permissions (op-only `/shop reload`):

```java
Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))   // also LEVEL_ALL, LEVEL_MODERATORS, LEVEL_ADMINS, LEVEL_OWNERS
// inside execute / manual check:
source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)    // net.minecraft.server.permissions.Permissions
```

Commands: `CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal(..)...))` (Fabric command-api v2);
`ctx.getSource().getPlayerOrException()`, `sendSuccess(() -> Component, boolean)`, `sendFailure(Component)`.

Gson: `com.google.gson.Gson` compiles on the main classpath (Minecraft ships it; spike's `SpikeMod.GSON`). Not jar-in-jar'd by us, MC provides it at runtime.

Close menu on death / disconnect (Fabric API):

```java
ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> handler.getPlayer().closeContainer());
ServerPlayerEvents.LEAVE.register(player -> player.closeContainer());                       // equivalent
ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> { if (entity instanceof ServerPlayer p) p.closeContainer(); });
```
`ServerPlayerEvents.ALLOW_DEATH` is deprecated in this Fabric API version; use `AFTER_DEATH`. `ServerPlayer.closeContainer()` exists (also `doCloseContainer()`, `die(DamageSource)`, `hasDisconnected()`).
sgui also fires `onPlayerClose`/`onRemoved` when the container closes, so clean-up belongs there.
Also consider `stillValid()` override on the gui (e.g. return false if the shop block/entity is gone).

## Not verified / follow-ups
- No in-game run: confirm that cancelled clicks leave no ghost item on the cursor and that number-key swap into the chest does nothing.
- `SavedDataType` with `SAVED_DATA_COMMAND_STORAGE` fix type: confirm a save/load round trip on a real server.
- The spike code is not registered; delete the `spike` package before release.
