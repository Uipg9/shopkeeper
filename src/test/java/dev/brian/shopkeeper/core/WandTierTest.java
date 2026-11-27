package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WandTierTest {
	@Test
	void payoutRoundsDownAndNeverExceedsFull() {
		assertEquals(75, WandTier.payout(100, 75));
		assertEquals(7, WandTier.payout(10, 75));
		assertEquals(100, WandTier.payout(100, 100));
		assertEquals(100, WandTier.payout(100, 250));
		assertEquals(0, WandTier.payout(-5, 75));
		assertTrue(WandTier.payout(Long.MAX_VALUE, 95) <= Long.MAX_VALUE);
	}

	@Test
	void tiersGetBetterAndNextStopsAtMax() {
		for (int i = 1; i < WandTier.MAX_LEVEL; i++) {
			WandTier.Tier a = WandTier.of(i), b = WandTier.of(i + 1);
			assertTrue(b.ratePct() > a.ratePct());
			assertTrue(b.cost() > a.cost());
			assertEquals(b, WandTier.next(i).orElseThrow());
		}
		assertTrue(WandTier.next(WandTier.MAX_LEVEL).isEmpty());
		assertTrue(WandTier.of(WandTier.MAX_LEVEL).unlimited());
		assertTrue(WandTier.of(WandTier.MAX_LEVEL).ratePct() <= 100, "wand must never pay above the normal sell price");
		assertEquals(WandTier.of(1), WandTier.of(0));
		assertEquals(0, WandTier.of(WandTier.MAX_LEVEL).rechargeCost());
	}
}
