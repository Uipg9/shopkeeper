# Black market "used gear" API notes (MC 26.3)

Compile-verified (gradlew compileJava) plus `javap` on `minecraft-common-deobf-26.3.jar`. NOT run on a server: runtime
behaviour (glint, tooltip, registry lookup with real data) is unverified.

## Imports
```java
import net.minecraft.core.Holder; import net.minecraft.core.HolderLookup; import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents; import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries; import net.minecraft.resources.Identifier; import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*; import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.{Enchantment, EnchantmentHelper, Enchantments, ItemEnchantments};
```

## 1. Item from id + damageable
```java
Identifier id = Identifier.tryParse("minecraft:iron_pickaxe");          // null if malformed (parse() throws)
Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);            // Registry.getOptional(Identifier)
ItemStack stack = new ItemStack(item.get());
stack.isDamageableItem(); stack.getMaxDamage();                          // false/0 for non-tools
```
Caveat: `getOptional` on an unknown id returns empty; `BuiltInRegistries.ITEM.get(id)` returns `Optional<Holder.Reference<Item>>`.
(Air is registered, so reject `stack.isEmpty()` too.)

## 2. Damage, never broken
```java
int max = stack.getMaxDamage();
int damage = (int) Math.round(max * (1.0 - frac));
stack.setDamageValue(Math.max(0, Math.min(max - 1, damage)));            // also getDamageValue(), isDamaged()
```
`setDamageValue` writes DataComponents.DAMAGE (clamps to 0..max itself; our max-1 keeps it unbroken).

## 3. Enchantments
```java
HolderLookup.RegistryLookup<Enchantment> lookup = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
Optional<Holder.Reference<Enchantment>> h = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, eid)); // HolderGetter.get(ResourceKey)
Holder.Reference<Enchantment> h2 = lookup.getOrThrow(Enchantments.SHARPNESS);                              // throws if missing
stack.enchant(h.get(), 3);                                                // EXISTS: ItemStack.enchant(Holder<Enchantment>, int)
EnchantmentHelper.updateEnchantments(stack, m -> m.set(holder, 3));       // EXISTS; Mutable has set/upgrade/removeIf/getLevel/keySet
ItemEnchantments a = EnchantmentHelper.getEnchantmentsForCrafting(stack); // EXISTS
ItemEnchantments b = stack.get(DataComponents.ENCHANTMENTS);              // null if none; also stack.getEnchantments(), stack.isEnchanted()
// iterate: b.entrySet() -> Object2IntMap.Entry<Holder<Enchantment>> (getKey().unwrapKey(), getIntValue()); b.getLevel(holder)
```
- Bytecode: `ItemStack.enchant` = `EnchantmentHelper.updateEnchantments(...)` -> sets DataComponents.ENCHANTMENTS, so the
  component patch is non-empty and ShopSell's "unmodified stack" check (`getComponentsPatch().isEmpty()`) rejects it.
  A damaged stack is also rejected via the DAMAGE patch.
- `enchant` does NOT check applicability (sharpness on a pickaxe is accepted); level not capped to max.
- Unknown enchant id: lookup.get returns empty (datapack could remove one) - skip it.

## 4. Rendering
- Glint: automatic. `ItemStack.hasFoil()` exists (ENCHANTMENTS non-empty or glint override). Not seen in a client.
- Vanilla enchant tooltip comes from ENCHANTMENTS (ItemEnchantments implements TooltipProvider); LORE is a separate
  component rendered after it, so adding lore does not remove the enchant lines:
```java
stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Price: 10").withStyle(Style.EMPTY.withItalic(false)))));
```
- Do NOT set TOOLTIP_DISPLAY (`new TooltipDisplay(boolean hideAll, SequencedSet hidden)`, `.DEFAULT.withHidden(type, true)`)
  or sgui `hideDefaultTooltip()`; hiding would drop enchants/durability. Recommendation: keep vanilla tooltip + price via LORE.
  Durability line shows only with advanced tooltips (F3+H); mention durability % in LORE if wanted.
- If using sgui `GuiElementBuilder(stack)`/`addLoreLine`, lore is appended without touching ENCHANTMENTS (not run).

## 5. Enchantments constants (all present as ResourceKey<Enchantment>)
SHARPNESS, KNOCKBACK, LOOTING, UNBREAKING, EFFICIENCY, FORTUNE, PROTECTION, THORNS, FEATHER_FALLING, POWER, QUICK_CHARGE, LURE:
all exist; ids are `minecraft:<lowercase>` (e.g. minecraft:feather_falling). Also present: SMITE, MENDING, SILK_TOUCH, INFINITY, SWEEPING_EDGE,
FIRE_ASPECT, PUNCH, FLAME, PIERCING, MULTISHOT, DENSITY, BREACH, WIND_BURST, LUNGE etc. (Registry presence at runtime depends on datapacks.)

## 6. Helper (compiled)
`UsedGear.make(RegistryAccess, String itemId, double durabilityLeft, List<Map.Entry<String,Integer>>)` returns ItemStack.EMPTY
for bad/non-damageable items, skips unknown enchants, clamps durability to [0,1] and damage to <= max-1. Body:
```java
Identifier id = Identifier.tryParse(itemId); if (id == null) return ItemStack.EMPTY;
Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id); if (item.isEmpty()) return ItemStack.EMPTY;
ItemStack stack = new ItemStack(item.get()); if (!stack.isDamageableItem()) return ItemStack.EMPTY;
int max = stack.getMaxDamage();
double frac = Math.max(0.0, Math.min(1.0, durabilityLeft));
stack.setDamageValue(Math.max(0, Math.min(max - 1, (int) Math.round(max * (1.0 - frac)))));
HolderLookup.RegistryLookup<Enchantment> lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
for (Map.Entry<String,Integer> e : enchants) {
    Identifier eid = Identifier.tryParse(e.getKey()); if (eid == null) continue;
    Optional<Holder.Reference<Enchantment>> h = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, eid));
    if (h.isPresent()) stack.enchant(h.get(), e.getValue());
}
return stack;
```
Untested: everything at runtime (no server). Item-data persistence (SavedData codec for stacks) not covered.
