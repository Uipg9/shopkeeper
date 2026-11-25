package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DayStatsTest {
	@Test
	void negativesClampAndNullIdBecomesEmpty() {
		DayStats d = new DayStats(1, -5, -6, -7, -8, -9, null);
		assertEquals(0, d.earned());
		assertEquals(0, d.spent());
		assertEquals(0, d.itemsSold());
		assertEquals(0, d.itemsBought());
		assertEquals(0, d.bestSaleCoins());
		assertEquals("", d.bestSaleItemId());
	}

	@Test
	void saleTracksBest() {
		DayStats d = DayStats.fresh(3).withSale("a:x", 2, 20).withSale("a:y", 1, 50).withSale("a:z", 1, 50);
		assertEquals(120, d.earned());
		assertEquals(4, d.itemsSold());
		assertEquals(50, d.bestSaleCoins());
		assertEquals("a:y", d.bestSaleItemId());
	}

	@Test
	void saturatesInsteadOfOverflowing() {
		DayStats d = DayStats.fresh(0).withSale("a:x", Integer.MAX_VALUE, Long.MAX_VALUE).withSale("a:x", 5, 5);
		assertEquals(Long.MAX_VALUE, d.earned());
		assertEquals(Integer.MAX_VALUE, d.itemsSold());
		DayStats p = DayStats.fresh(0).withPurchase(Integer.MAX_VALUE, Long.MAX_VALUE).withPurchase(1, 1);
		assertEquals(Long.MAX_VALUE, p.spent());
		assertEquals(Integer.MAX_VALUE, p.itemsBought());
	}

	@Test
	void refundFloorsAtZero() {
		DayStats d = DayStats.fresh(0).withPurchase(1, 10);
		assertEquals(4, d.withRefund(6).spent());
		assertEquals(0, d.withRefund(60).spent());
		assertEquals(10, d.withRefund(-5).spent());
	}
}
