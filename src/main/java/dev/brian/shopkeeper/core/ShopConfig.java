package dev.brian.shopkeeper.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable, validated shop definition. Pure Java: item ids are plain strings. */
public record ShopConfig(long startingBalance, double sellFraction, List<Tier> tiers, List<Item> items) {
	public static final double MIN_SELL_FRACTION = 0.25;
	public static final double MAX_SELL_FRACTION = 0.40;
	public static final int MAX_ITEMS_PER_TIER = 45;

	public record Tier(String id, String name, long unlockPrice, String icon) {}

	/** sell is 0 and sellable false when the item cannot be sold back. maxStack null = vanilla. */
	public record Item(String id, String tier, long buy, long sell, boolean sellable, Integer maxStack) {}

	public ShopConfig {
		tiers = List.copyOf(tiers);
		items = List.copyOf(items);
	}

	/** Builds an item, resolving the effective sell price (explicit, else floor(buy*fraction)). */
	public static Item makeItem(String id, String tier, long buy, Long explicitSell, Integer maxStack, double fraction) {
		long sell = explicitSell != null ? explicitSell : (long) Math.floor((double) buy * fraction);
		boolean sellable = sell >= 1 && sell < buy;
		return new Item(id, tier, buy, sellable ? sell : 0, sellable, maxStack);
	}

	public Item item(String id) {
		for (Item i : items) if (i.id().equals(id)) return i;
		return null;
	}

	public Tier tier(String id) {
		for (Tier t : tiers) if (t.id().equals(id)) return t;
		return null;
	}

	public List<Item> itemsIn(String tierId) {
		return items.stream().filter(i -> i.tier().equals(tierId)).toList();
	}

	Map<String, Item> itemIndex() {
		Map<String, Item> m = new HashMap<>();
		for (Item i : items) m.put(i.id(), i);
		return m;
	}
}
