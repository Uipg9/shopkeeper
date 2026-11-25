package dev.brian.shopkeeper.core;

import java.util.List;
import java.util.Random;

/** Pure daily deal picker: one HOT item (sell x1.5) and one CHEAP item (buy x0.75), deterministic from (day, seed). */
public final class Daily {
	private Daily() {}

	/** Either id may be null when too few items are eligible. */
	public record Deal(String hotId, String cheapId) {}

	/** Sell price on a hot day: min(floor(sell*3/2), buy-1), never below the normal sell. */
	public static long hotSell(ShopConfig.Item it) {
		if (!it.sellable()) return it.sell();
		return Math.max(it.sell(), Math.min(Math.floorDiv(it.sell() * 3, 2), it.buy() - 1));
	}

	/** Buy price on a sale day: max(ceil(buy*3/4), sell+1), never above the normal buy. */
	public static long cheapBuy(ShopConfig.Item it) {
		long c = Math.floorDiv(it.buy() * 3 + 3, 4);
		return Math.min(it.buy(), Math.max(c, it.sell() + 1));
	}

	public static Deal pick(long day, long worldSeed, ShopConfig cfg) {
		List<ShopConfig.Item> hots = cfg.items().stream().filter(i -> i.sellable() && hotSell(i) > i.sell()).toList();
		Random rnd = new Random(worldSeed * 0x9E3779B97F4A7C15L + day * 0xBF58476D1CE4E5B9L + 0x1234567L);
		String hot = hots.isEmpty() ? null : hots.get(rnd.nextInt(hots.size())).id();
		List<ShopConfig.Item> cheaps = cfg.items().stream()
			.filter(i -> cheapBuy(i) < i.buy() && !i.id().equals(hotIdOrEmpty(hot))).toList();
		String cheap = cheaps.isEmpty() ? null : cheaps.get(rnd.nextInt(cheaps.size())).id();
		return new Deal(hot, cheap);
	}

	private static String hotIdOrEmpty(String hot) {
		return hot == null ? "" : hot;
	}
}
