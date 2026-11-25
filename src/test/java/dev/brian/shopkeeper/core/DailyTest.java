package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyTest {
	static ShopConfig defaults() throws Exception {
		try (InputStream in = DailyTest.class.getResourceAsStream("/shopkeeper/default_shop.json")) {
			return ShopConfigParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).config();
		}
	}

	@Test
	void deterministicAndDistinct() throws Exception {
		var cfg = defaults();
		for (long d = 0; d < 300; d++) {
			var a = Daily.pick(d, 42, cfg);
			assertEquals(a, Daily.pick(d, 42, cfg));
			assertNotNull(a.hotId());
			assertNotNull(a.cheapId());
			assertNotEquals(a.hotId(), a.cheapId());
			assertTrue(cfg.item(a.hotId()).sellable());
		}
	}

	@Test
	void variesAcrossDays() throws Exception {
		var cfg = defaults();
		var seen = new java.util.HashSet<Daily.Deal>();
		for (long d = 0; d < 50; d++) seen.add(Daily.pick(d, 7, cfg));
		assertTrue(seen.size() > 10);
	}

	@Test
	void priceFormulas() {
		var it = ShopConfig.makeItem("a:x", "t", 100, 30L, null, 0.3);
		assertEquals(45, Daily.hotSell(it));
		assertEquals(75, Daily.cheapBuy(it));
		var tight = ShopConfig.makeItem("a:y", "t", 3, 2L, null, 0.3);
		assertEquals(2, Daily.hotSell(tight));
		assertEquals(3, Daily.cheapBuy(tight));
		var nosell = ShopConfig.makeItem("a:z", "t", 1, null, null, 0.3);
		assertEquals(0, Daily.hotSell(nosell));
		assertEquals(1, Daily.cheapBuy(nosell));
	}

	@Test
	void defaultSellRuleMakesEverythingAboveOneSellable() {
		for (double f : new double[] {ShopConfig.MIN_SELL_FRACTION, 0.3, ShopConfig.MAX_SELL_FRACTION}) {
			assertFalse(ShopConfig.makeItem("a:one", "t", 1, null, null, f).sellable());
			for (long buy = 2; buy <= 2000; buy++) {
				var it = ShopConfig.makeItem("a:x", "t", buy, null, null, f);
				assertTrue(it.sellable(), buy + " @ " + f);
				assertEquals(Math.max(1, (long) Math.floor(buy * f)), it.sell(), buy + " @ " + f);
				assertTrue(it.sell() >= 1 && it.sell() < buy);
				// Daily deals never allow profit: hot sell < buy, cheap buy > sell.
				assertTrue(Daily.hotSell(it) >= it.sell() && Daily.hotSell(it) < buy);
				assertTrue(Daily.cheapBuy(it) > Daily.hotSell(it) && Daily.cheapBuy(it) <= buy);
			}
		}
		var two = ShopConfig.makeItem("a:two", "t", 2, null, null, 0.3);
		assertEquals(1, Daily.hotSell(two));
		assertEquals(2, Daily.cheapBuy(two));
		var three = ShopConfig.makeItem("a:three", "t", 3, null, null, 0.3);
		assertEquals(1, Daily.hotSell(three));
		assertEquals(3, Daily.cheapBuy(three));
	}

	@Test
	void explicitSellStillRespected() {
		assertEquals(7, ShopConfig.makeItem("a:x", "t", 10, 7L, null, 0.3).sell());
		// explicit 0 is a deliberate opt-out; explicit >= buy can never be sellable
		assertFalse(ShopConfig.makeItem("a:x", "t", 10, 0L, null, 0.3).sellable());
		assertFalse(ShopConfig.makeItem("a:x", "t", 10, 10L, null, 0.3).sellable());
	}

	@Test
	void invariantsOverManyDays() throws Exception {
		var cfg = defaults();
		for (long seed : new long[] {0, 1, -99, 123456789L}) {
			for (long d = 0; d <= 2000; d++) {
				var e = new Economy(cfg);
				e.setDeal(Daily.pick(d, seed, cfg));
				for (var it : cfg.items()) {
					if (it.sellable()) assertTrue(e.sellPrice(it) < e.buyPrice(it), it.id());
					assertTrue(e.buyPrice(it) <= it.buy() && e.buyPrice(it) >= 1);
					assertTrue(e.sellPrice(it) >= it.sell());
				}
			}
		}
	}

	@Test
	void buyThenSellNeverProfitsAndNullDealIsNormal() throws Exception {
		var cfg = defaults();
		var item = cfg.items().stream().filter(i -> i.tier().equals(cfg.tiers().get(0).id()) && i.sellable()).findFirst().get();
		var plain = new Economy(cfg);
		assertEquals(item.buy(), plain.buyPrice(item));
		assertEquals(item.sell(), plain.sellPrice(item));
		for (var deal : new Daily.Deal[] {new Daily.Deal(item.id(), null), new Daily.Deal(null, item.id())}) {
			var e = new Economy(cfg);
			UUID p = UUID.randomUUID();
			e.ensurePlayer(p);
			e.setDeal(deal);
			long before = e.balance(p);
			var b = e.buy(p, item.id(), 5, 64);
			assertTrue(b.ok());
			assertEquals(e.buyPrice(item) * 5, b.charged());
			e.sell(p, item.id(), 5);
			assertTrue(e.balance(p) <= before);
			assertFalse(e.buy(p, item.id(), 1, 0).ok());
		}
	}
}
