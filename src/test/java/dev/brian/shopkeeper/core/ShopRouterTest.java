package dev.brian.shopkeeper.core;

import static dev.brian.shopkeeper.core.ShopRouter.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShopRouterTest {
	static final java.util.function.IntPredicate ALL = i -> true;
	static final java.util.function.IntPredicate NONE_UNLOCKED = i -> false;

	@Test
	void tabsLeftOnly() {
		for (int s = 0; s < 4; s++) {
			assertEquals(new Action(Type.SELECT_TAB, s), route(s, ClickKind.LEFT, ALL));
			assertEquals(new Action(Type.UNLOCK, s), route(s, ClickKind.LEFT, NONE_UNLOCKED));
			for (ClickKind k : ClickKind.values()) {
				if (k == ClickKind.LEFT) continue;
				assertEquals(Action.NONE, route(s, k, ALL));
				assertEquals(Action.NONE, route(s, k, NONE_UNLOCKED));
			}
		}
	}

	@Test
	void tabUnlockedIsPerTab() {
		assertEquals(Type.SELECT_TAB, route(1, ClickKind.LEFT, i -> i == 1).type());
		assertEquals(Type.UNLOCK, route(2, ClickKind.LEFT, i -> i == 1).type());
	}

	@Test
	void balanceAndSpacersNeverAct() {
		for (int s : new int[] {4, 5, 6, 7}) {
			for (ClickKind k : ClickKind.values()) assertEquals(Action.NONE, route(s, k, ALL), s + " " + k);
		}
	}

	@Test
	void wandTabLeftOnly() {
		for (ClickKind k : ClickKind.values()) {
			Action a = route(8, k, ALL);
			if (k == ClickKind.LEFT) assertEquals(Type.SELECT_WAND, a.type());
			else assertEquals(Action.NONE, a);
		}
	}

	@Test
	void itemSlots() {
		for (int s = 9; s < 54; s++) {
			assertEquals(new Action(Type.BUY_ONE, s - 9), route(s, ClickKind.LEFT, ALL));
			assertEquals(new Action(Type.BUY_STACK, s - 9), route(s, ClickKind.SHIFT_LEFT, ALL));
			assertEquals(new Action(Type.DETAIL, s - 9), route(s, ClickKind.RIGHT, ALL));
			for (ClickKind k : new ClickKind[] {ClickKind.SHIFT_RIGHT, ClickKind.OTHER}) {
				assertEquals(Action.NONE, route(s, k, ALL));
			}
		}
	}

	@Test
	void playerSlots() {
		for (int s = 54; s < 90; s++) {
			assertEquals(new Action(Type.SELL_ONE, s - 54), route(s, ClickKind.LEFT, ALL));
			assertEquals(new Action(Type.SELL_STACK, s - 54), route(s, ClickKind.SHIFT_LEFT, ALL));
			for (ClickKind k : new ClickKind[] {ClickKind.RIGHT, ClickKind.SHIFT_RIGHT, ClickKind.OTHER}) {
				assertEquals(Action.NONE, route(s, k, ALL));
			}
		}
	}

	@Test
	void outOfRangeNeverActs() {
		for (int s : new int[] {-999, -1, 90, 91, 1000, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			for (ClickKind k : ClickKind.values()) assertEquals(Action.NONE, route(s, k, ALL));
		}
	}

	@Test
	void blackMarketTabOnlyWhenShownAndLeft() {
		for (ClickKind k : ClickKind.values()) {
			Action shown = route(6, k, ALL, true, false);
			if (k == ClickKind.LEFT) assertEquals(new Action(Type.SELECT_BLACK_MARKET, -1), shown);
			else assertEquals(Action.NONE, shown);
			assertEquals(Action.NONE, route(6, k, ALL, false, false), "hidden " + k);
			assertEquals(Action.NONE, route(6, k, ALL, false, true), "hidden+selected " + k);
		}
	}

	@Test
	void offerSlotsBuySingleOnly() {
		for (int s = 9; s < 54; s++) {
			assertEquals(new Action(Type.BUY_OFFER, s - 9), route(s, ClickKind.LEFT, ALL, true, true));
			for (ClickKind k : new ClickKind[] {ClickKind.SHIFT_LEFT, ClickKind.RIGHT, ClickKind.SHIFT_RIGHT, ClickKind.OTHER}) {
				assertEquals(Action.NONE, route(s, k, ALL, true, true), s + " " + k);
			}
		}
	}

	@Test
	void unselectedBlackMarketKeepsNormalItemRouting() {
		assertEquals(new Action(Type.BUY_ONE, 0), route(9, ClickKind.LEFT, ALL, true, false));
		assertEquals(new Action(Type.BUY_STACK, 0), route(9, ClickKind.SHIFT_LEFT, ALL, true, false));
	}

	@Test
	void otherRegionsUnchangedWhileBlackMarketSelected() {
		for (ClickKind k : ClickKind.values()) {
			for (int s : new int[] {4, 5, 7}) assertEquals(Action.NONE, route(s, k, ALL, true, true));
			assertEquals(route(8, k, ALL), route(8, k, ALL, true, true));
			for (int s = 0; s < 4; s++) assertEquals(route(s, k, ALL), route(s, k, ALL, true, true));
			for (int s = 54; s < 90; s++) assertEquals(route(s, k, ALL), route(s, k, ALL, true, true));
			for (int s : new int[] {-1, 90, Integer.MAX_VALUE, Integer.MIN_VALUE}) assertEquals(Action.NONE, route(s, k, ALL, true, true));
		}
	}

	@Test
	void pageButtonsLeftOnlyAndOnlyWhenVisible() {
		for (ClickKind k : ClickKind.values()) {
			for (boolean prev : new boolean[] {false, true}) {
				for (boolean next : new boolean[] {false, true}) {
					for (boolean bmShown : new boolean[] {false, true}) {
						Action p = route(PREV_PAGE_SLOT, k, ALL, bmShown, false, prev, next);
						Action n = route(NEXT_PAGE_SLOT, k, ALL, bmShown, false, prev, next);
						String ctx = k + " prev=" + prev + " next=" + next + " bm=" + bmShown;
						assertEquals(prev && k == ClickKind.LEFT ? new Action(Type.PREV_PAGE, -1) : Action.NONE, p, ctx);
						assertEquals(next && k == ClickKind.LEFT ? new Action(Type.NEXT_PAGE, -1) : Action.NONE, n, ctx);
						// Black Market view: page slots are empty and never act.
						assertEquals(Action.NONE, route(PREV_PAGE_SLOT, k, ALL, true, true, prev, next), ctx);
						assertEquals(Action.NONE, route(NEXT_PAGE_SLOT, k, ALL, true, true, prev, next), ctx);
					}
				}
			}
		}
	}

	@Test
	void pageSlotsAreFiveAndSeven() {
		assertEquals(5, PREV_PAGE_SLOT);
		assertEquals(7, NEXT_PAGE_SLOT);
		assertEquals(45, PAGE_SIZE);
		assertEquals(PAGE_SIZE, ITEM_END - ITEM_START);
	}

	@Test
	void pageFlagsDoNotChangeOtherRegions() {
		for (ClickKind k : ClickKind.values()) {
			for (boolean sel : new boolean[] {false, true}) {
				for (int s : new int[] {-1, 0, 1, 2, 3, 4, 6, 8, 9, 30, 53, 54, 70, 89, 90, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
					Action base = route(s, k, ALL, true, sel);
					assertEquals(base, route(s, k, ALL, true, sel, true, true), s + " " + k);
					assertEquals(base, route(s, k, ALL, true, sel, false, false), s + " " + k);
				}
			}
		}
		// The short overloads never show page buttons.
		for (ClickKind k : ClickKind.values()) {
			assertEquals(Action.NONE, route(5, k, ALL));
			assertEquals(Action.NONE, route(7, k, ALL, true, false));
		}
	}

	@Test
	void pageCountAndClamp() {
		assertEquals(1, pageCount(0));
		assertEquals(1, pageCount(1));
		assertEquals(1, pageCount(45));
		assertEquals(2, pageCount(46));
		assertEquals(10, pageCount(450));
		assertEquals(0, clampPage(-3, 100));
		assertEquals(2, clampPage(2, 100));
		assertEquals(2, clampPage(9, 100));
		assertEquals(0, clampPage(5, 0));
	}
}
