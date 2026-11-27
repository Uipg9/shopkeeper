package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.BlackMarket.Ench;
import dev.brian.shopkeeper.core.BlackMarket.Good;
import dev.brian.shopkeeper.core.BlackMarket.Kind;
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
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Turns a pure black market Offer into a real ItemStack (server only). */
final class BlackMarketStock {
	private BlackMarketStock() {}

	static final String CRATE_NAME = "Mystery Bundle";

	/** Whole percent of durability left, clamped to 0..100. */
	static int percentLeft(double durabilityLeft) {
		return (int) Math.max(0, Math.min(100, Math.round(durabilityLeft * 100)));
	}

	/** Whether the lore shows a durability line: always for gear, for used rare goods (trident, netherite). */
	static boolean showsDurability(Offer offer) {
		return offer.kind() == Kind.GEAR || (offer.kind() == Kind.RARE && offer.durabilityLeft() < 1.0);
	}

	/**
	 * The item handed over on purchase (no lore). EMPTY if the item is unknown or unusable for its kind: gear must be
	 * damageable, a book needs at least one known enchant, a crate needs at least one known good. Never throws.
	 */
	static ItemStack make(RegistryAccess access, Offer offer) {
		try {
			return switch (offer.kind()) {
				case GEAR -> makeGear(access, offer);
				case BOOK -> makeBook(access, offer);
				case RARE -> makeRare(offer);
				case CRATE -> makeCrate(offer, true);
			};
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
	}

	private static Optional<Item> item(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id == null) return Optional.empty();
		return BuiltInRegistries.ITEM.getOptional(id);
	}

	private static ItemStack stackOf(String itemId) {
		Optional<Item> item = item(itemId);
		if (item.isEmpty()) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(item.get());
		return stack.isEmpty() ? ItemStack.EMPTY : stack;
	}

	private static void damage(ItemStack stack, double durabilityLeft) {
		int max = stack.getMaxDamage();
		double frac = Math.max(0.0, Math.min(1.0, durabilityLeft));
		stack.setDamageValue(Math.max(0, Math.min(max - 1, (int) Math.round(max * (1.0 - frac)))));
	}

	private static Optional<Holder.Reference<Enchantment>> enchant(HolderLookup.RegistryLookup<Enchantment> lookup,
			String enchantId) {
		Identifier eid = Identifier.tryParse(enchantId);
		if (eid == null) return Optional.empty();
		return lookup.get(ResourceKey.create(Registries.ENCHANTMENT, eid));
	}

	private static ItemStack makeGear(RegistryAccess access, Offer offer) {
		ItemStack stack = stackOf(offer.itemId());
		if (stack.isEmpty() || !stack.isDamageableItem()) return ItemStack.EMPTY;
		damage(stack, offer.durabilityLeft());
		HolderLookup.RegistryLookup<Enchantment> lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
		for (Ench e : offer.enchants()) {
			enchant(lookup, e.enchantId()).ifPresent(h -> stack.enchant(h, e.level()));
		}
		return stack;
	}

	private static ItemStack makeBook(RegistryAccess access, Offer offer) {
		ItemStack stack = stackOf(offer.itemId());
		if (stack.isEmpty()) return ItemStack.EMPTY;
		HolderLookup.RegistryLookup<Enchantment> lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (Ench e : offer.enchants()) {
			enchant(lookup, e.enchantId()).ifPresent(h -> stored.set(h, e.level()));
		}
		ItemEnchantments result = stored.toImmutable();
		if (result.isEmpty()) return ItemStack.EMPTY;
		stack.set(DataComponents.STORED_ENCHANTMENTS, result);
		return stack;
	}

	private static ItemStack makeRare(Offer offer) {
		ItemStack stack = stackOf(offer.itemId());
		if (stack.isEmpty()) return ItemStack.EMPTY;
		stack.setCount(Math.max(1, Math.min(stack.getMaxStackSize(), offer.count())));
		if (offer.durabilityLeft() < 1.0 && stack.isDamageableItem()) damage(stack, offer.durabilityLeft());
		return stack;
	}

	/** The named bundle; with contents for the real item, empty for the menu so the mystery stays hidden. */
	private static ItemStack makeCrate(Offer offer, boolean filled) {
		ItemStack stack = stackOf(offer.itemId());
		if (stack.isEmpty()) return ItemStack.EMPTY;
		BundleContents.Mutable contents = BundleContents.EMPTY.asMutable();
		int inserted = 0;
		for (Good g : offer.contents()) {
			Optional<Item> item = item(g.itemId());
			if (item.isEmpty()) continue;
			ItemStack in = new ItemStack(item.get(), Math.max(1, g.count()));
			if (in.isEmpty()) continue;
			inserted += contents.tryInsert(in);
		}
		if (inserted == 0) return ItemStack.EMPTY;
		if (filled) stack.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
		stack.set(DataComponents.CUSTOM_NAME,
				Component.literal(CRATE_NAME).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GOLD)));
		return stack;
	}

	private static Component line(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(Style.EMPTY.withItalic(false).withColor(color));
	}

	/**
	 * The item as shown in the menu, plus lore: durability (gear and used rare goods), crate hint, price, one per day /
	 * SOLD OUT, a gold LUCKY DEAL line (and glint) for the lucky offer, and a red requirement line when its tier is locked.
	 */
	static ItemStack display(RegistryAccess access, Offer offer, boolean sold, String lockedTierName) {
		ItemStack stack;
		try {
			stack = offer.kind() == Kind.CRATE ? makeCrate(offer, false) : make(access, offer);
		} catch (RuntimeException e) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) return stack;
		List<Component> lore = new ArrayList<>();
		if (showsDurability(offer)) {
			int pct = percentLeft(offer.durabilityLeft());
			lore.add(line("Used - " + pct + "% durability left", pct >= 40 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}
		if (offer.kind() == Kind.CRATE) {
			lore.add(line(offer.contents().size() + " random stacks of goods inside", ChatFormatting.GRAY));
		}
		lore.add(line("Price: " + offer.price() + " coins", ChatFormatting.YELLOW));
		if (offer.lucky()) {
			lore.add(line("LUCKY DEAL - half price!", ChatFormatting.GOLD));
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		lore.add(sold ? line("SOLD OUT", ChatFormatting.RED) : line("One per day", ChatFormatting.GRAY));
		if (lockedTierName != null) lore.add(line("Requires " + lockedTierName + " tier", ChatFormatting.RED));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}
}
