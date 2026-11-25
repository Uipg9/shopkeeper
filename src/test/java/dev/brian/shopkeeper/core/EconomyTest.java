package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class EconomyTest {
	static ShopConfig config(long start, double frac, long advPrice, long buy) {
		var tiers = List.of(new ShopConfig.Tier("basics", "Basics", 0, "a:icon"), new ShopConfig.Tier("adv", "Adv", advPrice, "a:icon"));
		var items = List.of(ShopConfig.makeItem("a:one", "basics", buy, null, null, frac),
				ShopConfig.makeItem("a:two", "adv", buy, null, null, frac),
				ShopConfig.makeItem("a:cheap", "basics", 1, null, null, frac));
		return new ShopConfig(start, frac, tiers, items);
	}

	final UUID p = UUID.randomUUID();

	@Test
	void startingBalanceGrantedOnce() {
		var e = new Economy(config(100, 0.3, 500, 10));
		e.ensurePlayer(p);
		assertEquals(100, e.balance(p));
		assertTrue(e.buy(p, "a:one", 1, 1).ok());
		e.ensurePlayer(p);
		assertEquals(90, e.balance(p));
		assertTrue(e.isUnlocked(p, "basics"));
		assertFalse(e.isUnlocked(p, "adv"));
	}

	@Test
	void buyChargesOnlyForFit() {
		var e = new Economy(config(100, 0.3, 500, 10));
		e.ensurePlayer(p);
		var q = e.quoteBuy(p, "a:one", 5, 3);
		assertTrue(q.ok());
		assertEquals(30, q.charged());
		assertEquals(3, q.delivered());
		assertEquals(100, e.balance(p));
		var r = e.buy(p, "a:one", 5, 3);
		assertEquals(30, r.charged());
		assertEquals(3, r.delivered());
		assertEquals(70, e.balance(p));
	}

	@Test
	void buyFailuresLeaveStateUnchanged() {
		var e = new Economy(config(100, 0.3, 500, 10));
		e.ensurePlayer(p);
		assertFalse(e.buy(p, "a:one", 11, 11).ok()); // 110 > 100, no partial
		assertFalse(e.buy(p, "a:two", 1, 1).ok()); // locked
		assertFalse(e.buy(p, "a:nope", 1, 1).ok());
		assertFalse(e.buy(p, "a:one", 0, 5).ok());
		assertFalse(e.buy(p, "a:one", 5, 0).ok());
		assertFalse(e.buy(p, "a:one", -1, 5).ok());
		assertEquals(100, e.balance(p));
		assertFalse(e.buy(UUID.randomUUID(), "a:one", 1, 1).ok()); // player never ensured
	}

	@Test
	void sellRules() {
		var e = new Economy(config(100, 0.3, 500, 10));
		e.ensurePlayer(p);
		var r = e.sell(p, "a:one", 4);
		assertTrue(r.ok());
		assertEquals(4, r.removed());
		assertEquals(12, r.credited());
		assertEquals(112, e.balance(p));
		assertFalse(e.sell(p, "a:cheap", 1).ok()); // unsellable
		assertFalse(e.sell(p, "a:two", 1).ok()); // locked tier
		assertFalse(e.sell(p, "a:nope", 1).ok());
		assertFalse(e.sell(p, "a:one", 0).ok());
		assertEquals(112, e.balance(p));
	}

	@Test
	void unlockRules() {
		var e = new Economy(config(600, 0.3, 500, 10));
		e.ensurePlayer(p);
		assertFalse(e.unlock(p, "basics").ok()); // already
		assertFalse(e.unlock(p, "zzz").ok());
		assertTrue(e.unlock(p, "adv").ok());
		assertEquals(100, e.balance(p));
		assertTrue(e.isUnlocked(p, "adv"));
		assertFalse(e.unlock(p, "adv").ok());
		assertEquals(100, e.balance(p));
		var q = UUID.randomUUID();
		var e2 = new Economy(config(100, 0.3, 500, 10));
		e2.ensurePlayer(q);
		assertFalse(e2.unlock(q, "adv").ok());
		assertEquals(100, e2.balance(q));
		assertFalse(e2.isUnlocked(q, "adv"));
	}

	@Test
	void overflowIsRejected() {
		var e = new Economy(config(Long.MAX_VALUE, 0.3, 500, Long.MAX_VALUE / 2 + 1));
		e.ensurePlayer(p);
		assertFalse(e.buy(p, "a:one", 2, 2).ok());
		assertEquals(Long.MAX_VALUE, e.balance(p));
		var e2 = new Economy(config(Long.MAX_VALUE, 0.3, 500, 10));
		e2.ensurePlayer(p);
		assertFalse(e2.sell(p, "a:one", 1).ok()); // credit would overflow balance
		assertEquals(Long.MAX_VALUE, e2.balance(p));
	}

	@Test
	void unlocksAbsentFromConfigSurviveRoundTrip() {
		var full = config(600, 0.3, 500, 10);
		var e = new Economy(full);
		e.ensurePlayer(p);
		e.unlock(p, "adv");
		var reduced = new ShopConfig(600, 0.3, List.of(full.tiers().get(0)),
				List.of(ShopConfig.makeItem("a:one", "basics", 10, null, null, 0.3)));
		var e2 = new Economy(reduced);
		e2.restore(e.snapshot());
		assertTrue(e2.snapshot().get(p).unlockedTiers().contains("adv"));
		var e3 = new Economy(full);
		e3.restore(e2.snapshot());
		assertTrue(e3.isUnlocked(p, "adv"));
		assertEquals(e.balance(p), e3.balance(p));
	}

	@Test
	void refundCreditsAndGuardsOverflow() {
		var e = new Economy(config(100, 0.3, 500, 10));
		e.ensurePlayer(p);
		e.refund(p, 25);
		assertEquals(125, e.balance(p));
		e.refund(p, 0);
		assertEquals(125, e.balance(p));
		assertThrows(IllegalArgumentException.class, () -> e.refund(p, -1));
		var big = new Economy(config(Long.MAX_VALUE, 0.3, 500, 10));
		big.ensurePlayer(p);
		assertThrows(ArithmeticException.class, () -> big.refund(p, 1));
		assertEquals(Long.MAX_VALUE, big.balance(p));
	}

	@Test
	void snapshotRestoreRoundTrip() {
		var cfg = config(600, 0.3, 500, 10);
		var e = new Economy(cfg);
		e.ensurePlayer(p);
		e.unlock(p, "adv");
		e.buy(p, "a:two", 2, 2);
		var snap = e.snapshot();
		var e2 = new Economy(cfg);
		e2.restore(snap);
		assertEquals(e.balance(p), e2.balance(p));
		assertTrue(e2.isUnlocked(p, "adv"));
		e2.ensurePlayer(p);
		assertEquals(e.balance(p), e2.balance(p)); // no second grant
		assertEquals(snap, e2.snapshot());
	}
}
