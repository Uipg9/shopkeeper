package dev.brian.shopkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlackMarketStockTest {
	@Test
	void percentRounds() {
		assertEquals(10, BlackMarketStock.percentLeft(0.10));
		assertEquals(65, BlackMarketStock.percentLeft(0.65));
		assertEquals(0, BlackMarketStock.percentLeft(-3));
		assertEquals(100, BlackMarketStock.percentLeft(7));
		assertEquals(34, BlackMarketStock.percentLeft(0.337));
	}
}
