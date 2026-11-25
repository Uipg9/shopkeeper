package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.BlackMarket.Ench;
import dev.brian.shopkeeper.core.BlackMarket.Offer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;

/** Turns a pure black market Offer into a real used-gear ItemStack (server only). */
final class BlackMarketStock {
	private BlackMarketStock() {}

	/** Whole percent of durability left, clamped to 0..100. */
	static int percentLeft(double durabilityLeft) {
		return (int) Math.max(0, Math.min(100, Math.round(durabilityLeft * 100)));
	}

	/** The gear itself (damaged, enchanted, no lore); EMPTY if the item is unknown, not damageable, or any enchant cannot be applied. */
	static ItemStack make(RegistryAccess access, Offer offer) {
		Identifier id = Identifier.tryParse(offer.itemId());
		if (id == null) return ItemStack.EMPTY;
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		if (item.isEmpty()) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(item.get());
		if (stack.isEmpty() || !stack.isDamageableItem()) return ItemStack.EMPTY;
		int max = stack.getMaxDamage();
		double frac = Math.max(0.0, Math.min(1.0, offer.durabilityLeft()));
		stack.setDamageValue(Math.max(0, Math.min(max - 1, (int) Math.round(max * (1.0 - frac)))));
		HolderLookup.RegistryLookup<Enchantment> lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
		for (Ench e : offer.enchants()) {
			Identifier eid = Identifier.tryParse(e.enchantId());
			if (eid == null) continue;
			Optional<Holder.Reference<Enchantment>> h = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, eid));
			if (h.isPresent()) stack.enchant(h.get(), e.level());
		}
		return stack;
	}

	private static Component line(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(Style.EMPTY.withItalic(false).withColor(color));
	}

	/** The item as shown in the menu: gear plus lore (durability, price, one per day / SOLD OUT, and a red requirement line when its tier is locked). */
	static ItemStack display(RegistryAccess access, Offer offer, boolean sold, String lockedTierName) {
		ItemStack stack = make(access, offer);
		if (stack.isEmpty()) return stack;
		int pct = percentLeft(offer.durabilityLeft());
		List<Component> lore = new ArrayList<>();
		lore.add(line("Used - " + pct + "% durability left", pct >= 40 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		lore.add(line("Price: " + offer.price() + " coins", ChatFormatting.YELLOW));
		lore.add(sold ? line("SOLD OUT", ChatFormatting.RED) : line("One per day", ChatFormatting.GRAY));
		if (lockedTierName != null) lore.add(line("Requires " + lockedTierName + " tier", ChatFormatting.RED));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}
}
