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
	void closeLeftOnly() {
		for (ClickKind k : ClickKind.values()) {
			Action a = route(8, k, ALL);
			if (k == ClickKind.LEFT) assertEquals(Type.CLOSE, a.type());
			else assertEquals(Action.NONE, a);
		}
	}

	@Test
	void itemSlots() {
		for (int s = 9; s < 54; s++) {
			assertEquals(new Action(Type.BUY_ONE, s - 9), route(s, ClickKind.LEFT, ALL));
			assertEquals(new Action(Type.BUY_STACK, s - 9), route(s, ClickKind.SHIFT_LEFT, ALL));
			for (ClickKind k : new ClickKind[] {ClickKind.RIGHT, ClickKind.SHIFT_RIGHT, ClickKind.OTHER}) {
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
}
