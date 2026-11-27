package dev.brian.shopkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brian.shopkeeper.core.BlackMarket;
import java.util.List;
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

	@Test
	void durabilityLineOnlyForUsedItems() {
		assertTrue(BlackMarketStock.showsDurability(new BlackMarket.Offer(0, "minecraft:iron_sword", 0.5, List.of(), 10)));
		assertTrue(BlackMarketStock.showsDurability(new BlackMarket.Offer(0, "minecraft:trident", 0.3, List.of(), 10,
				BlackMarket.Kind.RARE, 1, false, List.of())));
		assertFalse(BlackMarketStock.showsDurability(new BlackMarket.Offer(0, "minecraft:saddle", 1.0, List.of(), 10,
				BlackMarket.Kind.RARE, 1, false, List.of())));
		assertFalse(BlackMarketStock.showsDurability(new BlackMarket.Offer(0, "minecraft:enchanted_book", 1.0, List.of(), 10,
				BlackMarket.Kind.BOOK, 1, false, List.of())));
		assertFalse(BlackMarketStock.showsDurability(new BlackMarket.Offer(0, "minecraft:bundle", 1.0, List.of(), 10,
				BlackMarket.Kind.CRATE, 1, false, List.of())));
	}
}
