package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class EconomyFuzzTest {
	static ShopConfig randomConfig(Random rnd) {
		double frac = 0.25 + rnd.nextInt(16) / 100.0;
		int nTiers = 1 + rnd.nextInt(4);
		List<ShopConfig.Tier> tiers = new ArrayList<>();
		for (int i = 0; i < nTiers; i++) {
			long price = i == 0 ? 0 : (rnd.nextInt(4) == 0 ? 0 : rnd.nextInt(2000));
			tiers.add(new ShopConfig.Tier("t" + i, "T" + i, price, "a:i"));
		}
		List<ShopConfig.Item> items = new ArrayList<>();
		int n = 1 + rnd.nextInt(12);
		for (int i = 0; i < n; i++) {
			long buy = switch (rnd.nextInt(4)) {
				case 0 -> 1 + rnd.nextInt(5);
				case 1 -> 1 + rnd.nextInt(1000);
				case 2 -> 1 + rnd.nextLong(1_000_000_000_000L);
				default -> Long.MAX_VALUE / (1 + rnd.nextInt(100));
			};
			Long sell = rnd.nextInt(3) == 0 ? Long.valueOf(rnd.nextLong(Math.min(buy, 1000))) : null;
			items.add(ShopConfig.makeItem("a:item" + i, "t" + rnd.nextInt(nTiers), buy, sell, null, frac));
		}
		long start = rnd.nextInt(5) == 0 ? Long.MAX_VALUE - rnd.nextInt(100) : rnd.nextInt(100_000);
		return new ShopConfig(start, frac, tiers, items);
	}

	@Test
	void randomOperationsKeepInvariants() {
		Random rnd = new Random(0xC0FFEE);
		for (int c = 0; c < 300; c++) {
			ShopConfig cfg = randomConfig(rnd);
			Economy e = new Economy(cfg);
			UUID p = new UUID(rnd.nextLong(), rnd.nextLong());
			e.ensurePlayer(p);
			for (int op = 0; op < 60; op++) {
				var before = e.snapshot();
				var item = cfg.items().get(rnd.nextInt(cfg.items().size()));
				int qty = switch (rnd.nextInt(4)) {
					case 0 -> rnd.nextInt(-2, 3);
					case 1 -> 1 + rnd.nextInt(64);
					case 2 -> 1 + rnd.nextInt(2000);
					default -> rnd.nextInt();
				};
				int fits = rnd.nextInt(3) == 0 ? rnd.nextInt(-1, 3) : rnd.nextInt(0, 100);
				switch (rnd.nextInt(4)) {
					case 0, 1 -> {
						var q = e.quoteBuy(p, item.id(), qty, fits);
						assertEquals(before, e.snapshot(), "quote mutated");
						var r = e.buy(p, item.id(), qty, fits);
						assertEquals(q.ok(), r.ok());
						if (r.ok()) {
							int expect = Math.min(qty, fits);
							assertEquals(expect, r.delivered());
							assertEquals(Math.multiplyExact(item.buy(), (long) expect), r.charged());
							assertEquals(before.get(p).balance() - r.charged(), e.balance(p));
							assertTrue(e.isUnlocked(p, item.tier()));
							if (item.sellable()) {
								Economy copy = new Economy(cfg);
								copy.restore(e.snapshot());
								var s = copy.sell(p, item.id(), r.delivered());
								if (s.ok()) {
									assertTrue(s.credited() < r.charged(), "profit!");
								} else {
									assertEquals(e.snapshot(), copy.snapshot());
								}
							}
						} else {
							assertEquals(before, e.snapshot(), "failed buy mutated");
						}
					}
					case 2 -> {
						var r = e.sell(p, item.id(), qty);
						if (r.ok()) {
							assertTrue(item.sellable());
							assertEquals(qty, r.removed());
							assertEquals(Math.multiplyExact(item.sell(), (long) qty), r.credited());
							assertEquals(before.get(p).balance() + r.credited(), e.balance(p));
						} else {
							assertEquals(before, e.snapshot(), "failed sell mutated");
						}
					}
					default -> {
						var tier = cfg.tiers().get(rnd.nextInt(cfg.tiers().size()));
						boolean was = e.isUnlocked(p, tier.id());
						long bal = e.balance(p);
						var r = e.unlock(p, tier.id());
						if (r.ok()) {
							assertFalse(was);
							assertTrue(e.isUnlocked(p, tier.id()));
							assertEquals(bal - tier.unlockPrice(), e.balance(p));
						} else {
							assertEquals(before, e.snapshot(), "failed unlock mutated");
							assertTrue(was || bal < tier.unlockPrice());
						}
					}
				}
				assertTrue(e.balance(p) >= 0, "negative balance");
			}
		}
	}
}
