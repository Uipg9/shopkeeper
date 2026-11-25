package dev.brian.shopkeeper.core;

import java.util.function.IntPredicate;

/** Pure click routing for the 9x6 shop menu. Deny by default: only whitelisted (region, kind) pairs act. */
public final class ShopRouter {
	private ShopRouter() {}

	public static final int TAB_COUNT = 4;
	public static final int BALANCE_SLOT = 4;
	public static final int BLACK_MARKET_SLOT = 6;
	public static final int CLOSE_SLOT = 8;
	public static final int ITEM_START = 9;
	public static final int ITEM_END = 54; // exclusive
	public static final int PLAYER_START = 54;
	public static final int PLAYER_END = 90; // exclusive

	public enum ClickKind { LEFT, SHIFT_LEFT, RIGHT, SHIFT_RIGHT, OTHER }

	public enum Type { SELECT_TAB, SELECT_BLACK_MARKET, BUY_OFFER, UNLOCK, BUY_ONE, BUY_STACK, SELL_ONE, SELL_STACK, CLOSE, NONE }

	/** index: tab number, item index (0..44), or player slot (0..35: 0-26 main, 27-35 hotbar); -1 for none. */
	public record Action(Type type, int index) {
		public static final Action NONE = new Action(Type.NONE, -1);
	}

	/** Without a black market tab. */
	public static Action route(int slot, ClickKind kind, IntPredicate tabUnlocked) {
		return route(slot, kind, tabUnlocked, false, false);
	}

	/**
	 * tabUnlocked: tab index to whether that tier is unlocked for the clicking player.
	 * blackMarketShown: the black market tab exists; blackMarketSelected: it is the open tab (offers in slots 9+).
	 * Offer buys are single items only (left click); the offer index is the slot minus ITEM_START.
	 */
	public static Action route(int slot, ClickKind kind, IntPredicate tabUnlocked, boolean blackMarketShown, boolean blackMarketSelected) {
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
