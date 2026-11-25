package dev.brian.shopkeeper.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure in-memory economy. Every failing operation leaves state untouched. */
public final class Economy {
	/** Persistable per-player state. */
	public record PlayerSnapshot(long balance, Set<String> unlockedTiers, boolean grantedStarting) {
		public PlayerSnapshot {
			unlockedTiers = Set.copyOf(unlockedTiers);
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
	}

	private final ShopConfig config;
	private final Map<String, ShopConfig.Item> items;
	private final Map<UUID, Player> players = new HashMap<>();

	public Economy(ShopConfig config) {
		this.config = config;
		this.items = config.itemIndex();
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
			cost = Math.multiplyExact(item.buy(), (long) deliver);
		} catch (ArithmeticException ex) {
			return Result.fail("price overflow");
		}
		if (cost > p.balance) return Result.fail("insufficient funds");
		return new Result(true, cost, deliver, 0, 0, null);
	}

	/** Charges only for min(qty, fits). All-or-nothing on funds. */
	public Result buy(UUID id, String itemId, int qty, int fits) {
		Result q = quoteBuy(id, itemId, qty, fits);
		if (q.ok()) players.get(id).balance -= q.charged();
		return q;
	}

	/** Result.removed() is the count the caller must take from the inventory. */
	public Result sell(UUID id, String itemId, int qty) {
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
			credit = Math.multiplyExact(item.sell(), (long) qty);
			newBalance = Math.addExact(p.balance, credit);
		} catch (ArithmeticException ex) {
			return Result.fail("balance overflow");
		}
		p.balance = newBalance;
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
		return new Result(true, tier.unlockPrice(), 0, 0, 0, null);
	}

	public Map<UUID, PlayerSnapshot> snapshot() {
		Map<UUID, PlayerSnapshot> out = new HashMap<>();
		players.forEach((k, p) -> out.put(k, new PlayerSnapshot(p.balance, p.unlocked, p.granted)));
		return out;
	}

	/** Returns coins to a player (failed delivery). Throws on overflow or negative amounts; state is untouched then. */
	public void refund(UUID id, long amount) {
		if (amount < 0) throw new IllegalArgumentException("negative refund");
		Player p = players.get(id);
		if (p == null) throw new IllegalArgumentException("unknown player");
		p.balance = Math.addExact(p.balance, amount);
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
			players.put(k, p);
		});
	}
}
