package dev.brian.shopkeeper.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure in-memory economy. Every failing operation leaves state untouched. */
public final class Economy {
	/** Persistable per-player state. */
	public record PlayerSnapshot(long balance, Set<String> unlockedTiers, boolean grantedStarting, DayStats stats,
			DayStats pendingReport, long blackMarketDay, Set<Integer> blackMarketSlots) {
		public PlayerSnapshot {
			unlockedTiers = Set.copyOf(unlockedTiers);
			blackMarketSlots = Set.copyOf(blackMarketSlots);
		}

		/** stats/pendingReport may be null (none). */
		public PlayerSnapshot(long balance, Set<String> unlockedTiers, boolean grantedStarting) {
			this(balance, unlockedTiers, grantedStarting, null, null, 0, Set.of());
		}
	}

	/** charged/delivered for buys; credited/removed for sells; charged = unlock price for unlocks. */
	public record Result(boolean ok, long charged, int delivered, long credited, int removed, String error) {
		static Result fail(String why) {
			return new Result(false, 0, 0, 0, 0, why);
		}
	}

	private static final class Player {
		long balance;
		final Set<String> unlocked = new HashSet<>();
		boolean granted;
		DayStats stats;
		DayStats pending;
		long bmDay;
		final Set<Integer> bmSlots = new HashSet<>();
	}

	private final ShopConfig config;
	private final Map<String, ShopConfig.Item> items;
	private final Map<UUID, Player> players = new HashMap<>();

	public Economy(ShopConfig config) {
		this.config = config;
		this.items = config.itemIndex();
	}

	private Daily.Deal deal;

	public void setDeal(Daily.Deal deal) {
		this.deal = deal;
	}

	public Daily.Deal deal() {
		return deal;
	}

	private long day = -1;

	/** Current in-game day. Stats roll over lazily per player when they next change or are read. Never goes backwards. */
	public void setDay(long day) {
		if (day > this.day) this.day = day;
	}

	public long day() {
		return day;
	}

	/** Moves an older day's stats into pending (if it had trades) and starts a fresh day. */
	private DayStats roll(Player p) {
		long now = Math.max(day, 0);
		if (p.stats == null) {
			p.stats = DayStats.fresh(now);
		} else if (day >= 0 && p.stats.day() < day) {
			if (p.stats.hasActivity()) p.pending = p.stats;
			p.stats = DayStats.fresh(day);
		}
		return p.stats;
	}

	/** Today's stats so far (fresh zeros for unknown players). Rolls the player's day if needed. */
	public DayStats today(UUID id) {
		Player p = players.get(id);
		return p == null ? DayStats.fresh(Math.max(day, 0)) : roll(p);
	}

	/** The previous day's stats if there was any trading, returned once. */
	public Optional<DayStats> takePendingReport(UUID id) {
		Player p = players.get(id);
		if (p == null) return Optional.empty();
		roll(p);
		DayStats r = p.pending;
		p.pending = null;
		return r != null && r.hasActivity() ? Optional.of(r) : Optional.empty();
	}

	/** Items bought today from the black market slots (copy). Purchases from other days are dropped. */
	public Set<Integer> blackMarketBought(UUID id, long day) {
		Player p = players.get(id);
		if (p == null) return Set.of();
		if (day > p.bmDay) { // never clear because a clock went backwards
			p.bmSlots.clear();
			p.bmDay = day;
		}
		return Set.copyOf(p.bmSlots);
	}

	public boolean isBlackMarketBought(UUID id, long day, int slot) {
		return blackMarketBought(id, day).contains(slot);
	}

	/** True if newly marked; false for unknown players, negative slots, or already bought. */
	public boolean markBlackMarketBought(UUID id, long day, int slot) {
		if (slot < 0 || !players.containsKey(id)) return false;
		blackMarketBought(id, day);
		return players.get(id).bmSlots.add(slot);
	}

	/** Effective buy price including today's sale. */
	public long buyPrice(ShopConfig.Item item) {
		return deal != null && item.id().equals(deal.cheapId()) ? Daily.cheapBuy(item) : item.buy();
	}

	/** Effective sell price including today's hot item. */
	public long sellPrice(ShopConfig.Item item) {
		return deal != null && item.sellable() && item.id().equals(deal.hotId()) ? Daily.hotSell(item) : item.sell();
	}

	public ShopConfig config() {
		return config;
	}

	/** Grants the starting balance once and unlocks every free tier. Safe to call repeatedly. */
	public void ensurePlayer(UUID id) {
		Player p = players.computeIfAbsent(id, k -> new Player());
		if (!p.granted) {
			p.balance = config.startingBalance();
			p.granted = true;
		}
		for (ShopConfig.Tier t : config.tiers()) {
			if (t.unlockPrice() == 0) p.unlocked.add(t.id());
		}
	}

	public boolean hasPlayer(UUID id) {
		return players.containsKey(id);
	}

	public long balance(UUID id) {
		Player p = players.get(id);
		return p == null ? 0 : p.balance;
	}

	public boolean isUnlocked(UUID id, String tierId) {
		Player p = players.get(id);
		return p != null && p.unlocked.contains(tierId);
	}

	public Set<String> unlockedTiers(UUID id) {
		Player p = players.get(id);
		return p == null ? Set.of() : Set.copyOf(p.unlocked);
	}

	/** What buy() would do, without changing anything. */
	public Result quoteBuy(UUID id, String itemId, int qty, int fits) {
		Player p = players.get(id);
		if (p == null) return Result.fail("unknown player");
		ShopConfig.Item item = items.get(itemId);
		if (item == null) return Result.fail("unknown item");
		if (!p.unlocked.contains(item.tier())) return Result.fail("tier locked");
		if (qty <= 0) return Result.fail("quantity must be positive");
		int deliver = Math.min(qty, fits);
		if (deliver <= 0) return Result.fail("no room in inventory");
		long cost;
		try {
			cost = Math.multiplyExact(buyPrice(item), (long) deliver);
		} catch (ArithmeticException ex) {
			return Result.fail("price overflow");
		}
		if (cost > p.balance) return Result.fail("insufficient funds");
		return new Result(true, cost, deliver, 0, 0, null);
	}

	/** Charges only for min(qty, fits). All-or-nothing on funds. */
	public Result buy(UUID id, String itemId, int qty, int fits) {
		Result q = quoteBuy(id, itemId, qty, fits);
		if (q.ok()) {
			Player p = players.get(id);
			p.balance -= q.charged();
			p.stats = roll(p).withPurchase(q.delivered(), q.charged());
		}
		return q;
	}

	/** Charges an arbitrary price (black market). All-or-nothing on funds; counts as one item bought in today's stats. */
	public Result spend(UUID id, long price) {
		Player p = players.get(id);
		if (p == null) return Result.fail("unknown player");
		if (price <= 0) return Result.fail("price must be positive");
		if (price > p.balance) return Result.fail("insufficient funds");
		p.balance = Math.subtractExact(p.balance, price);
		p.stats = roll(p).withPurchase(1, price);
		return new Result(true, price, 0, 0, 0, null);
	}

	/** Result.removed() is the count the caller must take from the inventory. */
	public Result sell(UUID id, String itemId, int qty) {
		return sell(id, itemId, qty, 100);
	}

	/** Sells at ratePct percent of the normal sell price (the Sell Wand); rounds down, so a sale may credit 0. */
	public Result sell(UUID id, String itemId, int qty, int ratePct) {
		Player p = players.get(id);
		if (p == null) return Result.fail("unknown player");
		ShopConfig.Item item = items.get(itemId);
		if (item == null) return Result.fail("unknown item");
		if (!item.sellable()) return Result.fail("item cannot be sold");
		if (!p.unlocked.contains(item.tier())) return Result.fail("tier locked");
		if (qty <= 0) return Result.fail("quantity must be positive");
		long credit;
		long newBalance;
		try {
			credit = WandTier.payout(Math.multiplyExact(sellPrice(item), (long) qty), ratePct);
			newBalance = Math.addExact(p.balance, credit);
		} catch (ArithmeticException ex) {
			return Result.fail("balance overflow");
		}
		p.balance = newBalance;
		p.stats = roll(p).withSale(itemId, qty, credit);
		return new Result(true, 0, 0, credit, qty, null);
	}

	public Result unlock(UUID id, String tierId) {
		Player p = players.get(id);
		if (p == null) return Result.fail("unknown player");
		ShopConfig.Tier tier = config.tier(tierId);
		if (tier == null) return Result.fail("unknown tier");
		if (p.unlocked.contains(tierId)) return Result.fail("already unlocked");
		if (tier.unlockPrice() > p.balance) return Result.fail("insufficient funds");
		p.balance -= tier.unlockPrice();
		p.unlocked.add(tierId);
		p.stats = roll(p).withPurchase(0, tier.unlockPrice());
		return new Result(true, tier.unlockPrice(), 0, 0, 0, null);
	}

	public Map<UUID, PlayerSnapshot> snapshot() {
		Map<UUID, PlayerSnapshot> out = new HashMap<>();
		players.forEach((k, p) -> out.put(k, new PlayerSnapshot(p.balance, p.unlocked, p.granted, p.stats, p.pending, p.bmDay, p.bmSlots)));
		return out;
	}

	/** Returns coins to a player (failed delivery). Throws on overflow or negative amounts; state is untouched then. */
	public void refund(UUID id, long amount) {
		if (amount < 0) throw new IllegalArgumentException("negative refund");
		Player p = players.get(id);
		if (p == null) throw new IllegalArgumentException("unknown player");
		p.balance = Math.addExact(p.balance, amount);
		p.stats = roll(p).withRefund(amount);
	}

	/**
	 * Replaces all state. Negative balances clamp to 0. Unlocked tier ids absent from the current config are kept
	 * (so a bad or edited config never erases paid unlocks); lookups only match tiers the config defines.
	 */
	public void restore(Map<UUID, PlayerSnapshot> snap) {
		players.clear();
		snap.forEach((k, s) -> {
			Player p = new Player();
			p.balance = Math.max(0, s.balance());
			p.granted = s.grantedStarting();
			p.unlocked.addAll(s.unlockedTiers());
			p.stats = s.stats();
			p.pending = s.pendingReport();
			p.bmDay = s.blackMarketDay();
			s.blackMarketSlots().stream().filter(x -> x >= 0).forEach(p.bmSlots::add);
			players.put(k, p);
		});
	}
}
