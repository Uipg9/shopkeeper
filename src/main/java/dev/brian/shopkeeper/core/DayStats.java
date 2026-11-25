package dev.brian.shopkeeper.core;

/** Immutable per-player, per-day trade totals. All counters are non-negative and saturate instead of overflowing. */
public record DayStats(long day, long earned, long spent, int itemsSold, int itemsBought, long bestSaleCoins, String bestSaleItemId) {
	public DayStats {
		earned = Math.max(0, earned);
		spent = Math.max(0, spent);
		itemsSold = Math.max(0, itemsSold);
		itemsBought = Math.max(0, itemsBought);
		bestSaleCoins = Math.max(0, bestSaleCoins);
		if (bestSaleItemId == null) bestSaleItemId = "";
	}

	public static DayStats fresh(long day) {
		return new DayStats(day, 0, 0, 0, 0, 0, "");
	}

	public boolean hasActivity() {
		return earned > 0 || spent > 0;
	}

	private static long add(long a, long b) {
		long r = a + b;
		return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
	}

	private static int addInt(int a, int b) {
		long r = (long) a + b;
		return r > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) r;
	}

	public DayStats withSale(String itemId, int qty, long coins) {
		boolean best = coins > bestSaleCoins;
		return new DayStats(day, add(earned, coins), spent, addInt(itemsSold, qty), itemsBought,
			best ? coins : bestSaleCoins, best ? itemId : bestSaleItemId);
	}

	public DayStats withPurchase(int qty, long coins) {
		return new DayStats(day, earned, add(spent, coins), itemsSold, addInt(itemsBought, qty), bestSaleCoins, bestSaleItemId);
	}

	/** Reduces spent, floor 0. */
	public DayStats withRefund(long coins) {
		return new DayStats(day, earned, Math.max(0, spent - Math.max(0, coins)), itemsSold, itemsBought, bestSaleCoins, bestSaleItemId);
	}
}
