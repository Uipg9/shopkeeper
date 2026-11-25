package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SellPlanTest {
	@Test
	void emptyTotalsStartAtZero() {
		SellPlan.Totals t = new SellPlan.Totals();
		assertEquals(0, t.coins());
		assertEquals(0, t.items());
		assertEquals(0, t.sold());
		assertEquals(0, t.skipped());
		assertEquals("Nothing to sell.", t.summary());
	}

	@Test
	void accumulatesSalesAndSkips() {
		SellPlan.Totals t = new SellPlan.Totals();
		t.sold(20, 60);
		t.sold(3, 21);
		t.skip();
		t.skip();
		assertEquals(81, t.coins());
		assertEquals(23, t.items());
		assertEquals(2, t.sold());
		assertEquals(2, t.skipped());
		assertEquals("Sold 23 items for 81 coins (skipped 2 stacks).", t.summary());
	}

	@Test
	void summarySingularAndNoSkips() {
		SellPlan.Totals t = new SellPlan.Totals();
		t.sold(1, 3);
		assertEquals("Sold 1 item for 3 coins.", t.summary());
		t.skip();
		assertEquals("Sold 1 item for 3 coins (skipped 1 stack).", t.summary());
	}

	@Test
	void onlySkippedReportsNothingSold() {
		SellPlan.Totals t = new SellPlan.Totals();
		t.skip();
		assertEquals("Nothing sold (skipped 1 stack).", t.summary());
	}

	@Test
	void failureMessages() {
		assertEquals("That tier is locked.", SellPlan.failMessage("tier locked"));
		assertEquals("Cannot sell that.", SellPlan.failMessage("item cannot be sold"));
		assertEquals("Cannot sell that.", SellPlan.failMessage(null));
	}
}
