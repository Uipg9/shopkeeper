package dev.brian.shopkeeper.core;

import java.util.List;
import java.util.Optional;

/** Sell Wand tiers: cost to reach the tier, share of the normal sell price paid out, and uses per charge. */
public final class WandTier {
	private WandTier() {}

	/** Uses value meaning "never runs out". */
	public static final int UNLIMITED = -1;

	public record Tier(int level, String name, long cost, int ratePct, int uses) {
		public boolean unlimited() {
			return uses == UNLIMITED;
		}

		/** Coins to refill this tier's uses; 0 when unlimited. */
		public long rechargeCost() {
			return unlimited() ? 0 : Math.max(1, cost / 4);
		}
	}

	public static final List<Tier> TIERS = List.of(
		new Tier(1, "I", 1_000, 75, 50),
		new Tier(2, "II", 5_000, 85, 200),
		new Tier(3, "III", 20_000, 95, 1_000),
		new Tier(4, "IV", 60_000, 100, UNLIMITED));

	public static final int MAX_LEVEL = TIERS.size();

	/** The tier for a level, clamped into 1..MAX_LEVEL. */
	public static Tier of(int level) {
		return TIERS.get(Math.max(1, Math.min(MAX_LEVEL, level)) - 1);
	}

	public static Optional<Tier> next(int level) {
		return level >= MAX_LEVEL ? Optional.empty() : Optional.of(of(level + 1));
	}

	/** Coins paid for a sale that would normally credit fullCredit; rounds down, never above fullCredit. */
	public static long payout(long fullCredit, int ratePct) {
		if (fullCredit <= 0) return 0;
		int pct = Math.max(0, Math.min(100, ratePct));
		// split so huge credits never overflow: floor(full * pct / 100) exactly
		return fullCredit / 100 * pct + fullCredit % 100 * pct / 100;
	}
}
