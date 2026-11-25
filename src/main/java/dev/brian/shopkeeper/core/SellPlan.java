package dev.brian.shopkeeper.core;

/** Pure helpers for the /shop sell commands: result accumulation and messages. */
public final class SellPlan {
	private SellPlan() {}

	public static String failMessage(String economyError) {
		return "tier locked".equals(economyError) ? "That tier is locked." : "Cannot sell that.";
	}

	/** Accumulates the outcome of selling several stacks. */
	public static final class Totals {
		private long coins;
		private long items;
		private int sold;
		private int skipped;

		public void sold(int count, long credited) {
			items += count;
			coins += credited;
			sold++;
		}

		public void skip() {
			skipped++;
		}

		public long coins() { return coins; }
		public long items() { return items; }
		public int sold() { return sold; }
		public int skipped() { return skipped; }

		public String summary() {
			String skip = skipped == 0 ? "" : " (skipped " + skipped + (skipped == 1 ? " stack)" : " stacks)");
			if (sold == 0) return skipped == 0 ? "Nothing to sell." : "Nothing sold" + skip + ".";
			return "Sold " + items + (items == 1 ? " item" : " items") + " for " + coins + " coins" + skip + ".";
		}
	}
}
