package dev.brian.shopkeeper.core;

import java.util.function.IntPredicate;

/** Pure click routing for the 9x6 shop menu. Deny by default: only whitelisted (region, kind) pairs act. */
public final class ShopRouter {
	private ShopRouter() {}

	public static final int TAB_COUNT = 4;
	public static final int BALANCE_SLOT = 4;
	public static final int PREV_PAGE_SLOT = 5;
	public static final int BLACK_MARKET_SLOT = 6;
	public static final int NEXT_PAGE_SLOT = 7;
	public static final int CLOSE_SLOT = 8;
	public static final int ITEM_START = 9;
	public static final int ITEM_END = 54; // exclusive
	public static final int PAGE_SIZE = ITEM_END - ITEM_START;
	public static final int PLAYER_START = 54;
	public static final int PLAYER_END = 90; // exclusive

	public enum ClickKind { LEFT, SHIFT_LEFT, RIGHT, SHIFT_RIGHT, OTHER }

	public enum Type { SELECT_TAB, SELECT_BLACK_MARKET, PREV_PAGE, NEXT_PAGE, BUY_OFFER, UNLOCK, BUY_ONE, BUY_STACK, SELL_ONE, SELL_STACK, CLOSE, NONE }

	/** index: tab number, in-page item index (0..44), or player slot (0..35: 0-26 main, 27-35 hotbar); -1 for none. */
	public record Action(Type type, int index) {
		public static final Action NONE = new Action(Type.NONE, -1);
	}

	/** Without a black market tab. */
	public static Action route(int slot, ClickKind kind, IntPredicate tabUnlocked) {
		return route(slot, kind, tabUnlocked, false, false);
	}

	/** Without page buttons. */
	public static Action route(int slot, ClickKind kind, IntPredicate tabUnlocked, boolean blackMarketShown, boolean blackMarketSelected) {
		return route(slot, kind, tabUnlocked, blackMarketShown, blackMarketSelected, false, false);
	}

	/** Number of pages for a tier with itemCount items; always at least 1. */
	public static int pageCount(int itemCount) {
		return Math.max(1, (itemCount + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	/** Clamps a page index into [0, pageCount(itemCount)). */
	public static int clampPage(int page, int itemCount) {
		return Math.max(0, Math.min(page, pageCount(itemCount) - 1));
	}

	/**
	 * tabUnlocked: tab index to whether that tier is unlocked for the clicking player.
	 * blackMarketShown: the black market tab exists; blackMarketSelected: it is the open tab (offers in slots 9+).
	 * Offer buys are single items only (left click); the offer index is the slot minus ITEM_START.
	 * hasPrevPage/hasNextPage: that page button is visible (ignored while the black market is selected).
	 * Item buy indexes are in-page (0..44); the caller adds page * PAGE_SIZE.
	 */
	public static Action route(int slot, ClickKind kind, IntPredicate tabUnlocked, boolean blackMarketShown, boolean blackMarketSelected,
			boolean hasPrevPage, boolean hasNextPage) {
		if (slot == PREV_PAGE_SLOT) {
			return hasPrevPage && !blackMarketSelected && kind == ClickKind.LEFT ? new Action(Type.PREV_PAGE, -1) : Action.NONE;
		}
		if (slot == NEXT_PAGE_SLOT) {
			return hasNextPage && !blackMarketSelected && kind == ClickKind.LEFT ? new Action(Type.NEXT_PAGE, -1) : Action.NONE;
		}
		if (slot == BLACK_MARKET_SLOT) {
			return blackMarketShown && kind == ClickKind.LEFT ? new Action(Type.SELECT_BLACK_MARKET, -1) : Action.NONE;
		}
		if (slot >= 0 && slot < TAB_COUNT) {
			if (kind != ClickKind.LEFT) return Action.NONE;
			return new Action(tabUnlocked.test(slot) ? Type.SELECT_TAB : Type.UNLOCK, slot);
		}
		if (slot == CLOSE_SLOT) {
			return kind == ClickKind.LEFT ? new Action(Type.CLOSE, -1) : Action.NONE;
		}
		if (slot >= ITEM_START && slot < ITEM_END) {
			int i = slot - ITEM_START;
			if (blackMarketSelected) return kind == ClickKind.LEFT ? new Action(Type.BUY_OFFER, i) : Action.NONE;
			return switch (kind) {
				case LEFT -> new Action(Type.BUY_ONE, i);
				case SHIFT_LEFT -> new Action(Type.BUY_STACK, i);
				default -> Action.NONE;
			};
		}
		if (slot >= PLAYER_START && slot < PLAYER_END) {
			int i = slot - PLAYER_START;
			return switch (kind) {
				case LEFT -> new Action(Type.SELL_ONE, i);
				case SHIFT_LEFT -> new Action(Type.SELL_STACK, i);
				default -> Action.NONE;
			};
		}
		return Action.NONE;
	}
}
