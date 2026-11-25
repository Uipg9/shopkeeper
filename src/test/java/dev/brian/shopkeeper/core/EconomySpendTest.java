package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomySpendTest {
	private static Economy eco(UUID id) {
		ShopConfig cfg = new ShopConfig(100, 0.5, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")), List.of());
		Economy e = new Economy(cfg);
		e.ensurePlayer(id);
		return e;
	}

	@Test
	void spendDeductsAndRejects() {
		UUID id = UUID.randomUUID();
		Economy e = eco(id);
		assertTrue(e.spend(id, 30).ok());
		assertEquals(70, e.balance(id));
		assertFalse(e.spend(id, 71).ok());
		assertFalse(e.spend(id, 0).ok());
		assertFalse(e.spend(id, -5).ok());
		assertFalse(e.spend(UUID.randomUUID(), 1).ok());
		assertEquals(70, e.balance(id));
		assertTrue(e.spend(id, 70).ok());
		assertEquals(0, e.balance(id));
	}

	@Test
	void spendCountsAsOneItemBought() {
		UUID id = UUID.randomUUID();
		Economy e = eco(id);
		e.setDay(1);
		assertTrue(e.spend(id, 30).ok());
		assertEquals(1, e.today(id).itemsBought());
		assertEquals(30, e.today(id).spent());
	}
}
