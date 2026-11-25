package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomyReportTest {
	final UUID p = UUID.randomUUID();

	private Economy eco(long start) {
		Economy e = new Economy(EconomyTest.config(start, 0.3, 50, 10));
		e.setDay(5);
		e.ensurePlayer(p);
		return e;
	}

	@Test
	void buyAndSellAreRecorded() {
		Economy e = eco(100);
		assertTrue(e.buy(p, "a:one", 3, 3).ok()); // 30 spent
		var s = e.sell(p, "a:one", 4); // sell 3 each = 12
		assertTrue(s.ok());
		DayStats d = e.today(p);
		assertEquals(5, d.day());
		assertEquals(30, d.spent());
		assertEquals(s.credited(), d.earned());
		assertEquals(3, d.itemsBought());
		assertEquals(4, d.itemsSold());
		assertEquals(s.credited(), d.bestSaleCoins());
		assertEquals("a:one", d.bestSaleItemId());
	}

	@Test
	void unlockCountsAsSpentNotItems() {
		Economy e = eco(100);
		assertTrue(e.unlock(p, "adv").ok());
		assertEquals(50, e.today(p).spent());
		assertEquals(0, e.today(p).itemsBought());
	}

	@Test
	void failedOpsRecordNothing() {
		Economy e = eco(100);
		assertFalse(e.buy(p, "a:one", 11, 11).ok());
		assertFalse(e.buy(p, "a:two", 1, 1).ok());
		assertFalse(e.sell(p, "a:two", 1).ok());
		assertFalse(e.unlock(p, "adv-nope").ok());
		assertFalse(e.sell(p, "a:one", 0).ok());
		assertFalse(e.today(p).hasActivity());
		assertEquals(0, e.today(p).itemsSold());
		assertEquals(0, e.today(UUID.randomUUID()).earned());
	}

	@Test
	void refundReducesSpentWithFloor() {
		Economy e = eco(100);
		e.buy(p, "a:one", 2, 2); // 20
		e.refund(p, 15);
		assertEquals(5, e.today(p).spent());
		e.refund(p, 100);
		assertEquals(0, e.today(p).spent());
		assertEquals(100 - 20 + 115, e.balance(p));
	}

	@Test
	void refundOverflowLeavesStatsUntouched() {
		Economy big = new Economy(EconomyTest.config(Long.MAX_VALUE, 0.3, 50, 10));
		big.setDay(1);
		big.ensurePlayer(p);
		big.buy(p, "a:one", 1, 1);
		long spent = big.today(p).spent();
		assertThrows(ArithmeticException.class, () -> big.refund(p, Long.MAX_VALUE));
		assertEquals(spent, big.today(p).spent());
	}

	@Test
	void sellOverflowRecordsNothing() {
		Economy big = new Economy(EconomyTest.config(Long.MAX_VALUE, 0.3, 50, 10));
		big.setDay(1);
		big.ensurePlayer(p);
		assertFalse(big.sell(p, "a:one", 1).ok());
		assertEquals(0, big.today(p).earned());
	}

	@Test
	void reportConsumedOnceAndStartsFreshDay() {
		Economy e = eco(100);
		e.buy(p, "a:one", 2, 2);
		assertTrue(e.takePendingReport(p).isEmpty()); // same day: nothing pending
		e.setDay(6);
		var r = e.takePendingReport(p);
		assertTrue(r.isPresent());
		assertEquals(5, r.get().day());
		assertEquals(20, r.get().spent());
		assertTrue(e.takePendingReport(p).isEmpty());
		assertEquals(6, e.today(p).day());
		assertEquals(0, e.today(p).spent());
	}

	@Test
	void oldStatsStayReadableUntilConsumedEvenIfNewActivityHappens() {
		Economy e = eco(100);
		e.buy(p, "a:one", 2, 2);
		e.setDay(6);
		e.buy(p, "a:one", 1, 1); // rolls the old day into pending
		assertEquals(10, e.today(p).spent());
		assertEquals(6, e.today(p).day());
		assertEquals(20, e.takePendingReport(p).orElseThrow().spent());
	}

	@Test
	void emptyDayProducesNoReportAndSkippedDaysWork() {
		Economy e = eco(100);
		e.setDay(6);
		assertTrue(e.takePendingReport(p).isEmpty());
		e.buy(p, "a:one", 1, 1);
		e.setDay(40); // many skipped days
		var r = e.takePendingReport(p).orElseThrow();
		assertEquals(6, r.day());
		assertEquals(10, r.spent());
		assertEquals(40, e.today(p).day());
		e.setDay(41);
		assertTrue(e.takePendingReport(p).isEmpty());
	}

	@Test
	void setDayBackwardsDoesNotRoll() {
		Economy e = eco(100);
		e.buy(p, "a:one", 1, 1);
		e.setDay(3);
		assertTrue(e.takePendingReport(p).isEmpty());
		assertEquals(10, e.today(p).spent());
	}

	@Test
	void statsAndPendingSurviveSnapshotRestore() {
		Economy e = eco(100);
		e.buy(p, "a:one", 2, 2);
		e.setDay(6);
		e.buy(p, "a:one", 1, 1);
		Economy e2 = new Economy(e.config());
		e2.restore(e.snapshot());
		e2.setDay(6);
		assertEquals(10, e2.today(p).spent());
		assertEquals(20, e2.takePendingReport(p).orElseThrow().spent());
	}

	@Test
	void restoredOldDayRollsIntoPendingOnJoin() {
		Economy e = eco(100);
		e.buy(p, "a:one", 2, 2);
		Economy e2 = new Economy(e.config());
		e2.restore(e.snapshot());
		e2.setDay(9);
		assertEquals(20, e2.takePendingReport(p).orElseThrow().spent());
	}

	@Test
	void blackMarketTracking() {
		Economy e = eco(100);
		assertTrue(e.blackMarketBought(p, 5).isEmpty());
		assertTrue(e.markBlackMarketBought(p, 5, 2));
		assertFalse(e.markBlackMarketBought(p, 5, 2));
		assertTrue(e.markBlackMarketBought(p, 5, 0));
		assertTrue(e.isBlackMarketBought(p, 5, 2));
		assertEquals(Set.of(0, 2), e.blackMarketBought(p, 5));
		assertFalse(e.isBlackMarketBought(p, 6, 2)); // other day: dropped
		assertTrue(e.blackMarketBought(p, 6).isEmpty());
		assertTrue(e.markBlackMarketBought(p, 6, 2));
		assertFalse(e.isBlackMarketBought(p, 5, 0));
		assertFalse(e.markBlackMarketBought(p, 6, -1));
		assertFalse(e.markBlackMarketBought(UUID.randomUUID(), 6, 1));
	}

	@Test
	void blackMarketNeverClearsWhenDayGoesBackwards() {
		Economy e = eco(100);
		assertTrue(e.markBlackMarketBought(p, 5, 0));
		assertTrue(e.isBlackMarketBought(p, 3, 0)); // earlier day treated as stored day
		assertTrue(e.isBlackMarketBought(p, 5, 0));
		assertFalse(e.markBlackMarketBought(p, 3, 0));
		assertFalse(e.isBlackMarketBought(p, 6, 0)); // later day clears
	}

	@Test
	void blackMarketSurvivesSnapshot() {
		Economy e = eco(100);
		e.markBlackMarketBought(p, 5, 3);
		Economy e2 = new Economy(e.config());
		e2.restore(e.snapshot());
		assertTrue(e2.isBlackMarketBought(p, 5, 3));
		assertFalse(e2.isBlackMarketBought(p, 6, 3));
	}
}
