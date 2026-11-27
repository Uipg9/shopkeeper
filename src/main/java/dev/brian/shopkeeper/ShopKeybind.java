package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.ShopRouter;

/** Names for the client shop keys. Pure constants so they can be unit-tested without a client. */
public final class ShopKeybind {
	/** Command sent when the Open Shop key is pressed (same as typing /shop). */
	public static final String COMMAND = "shop";
	public static final String KEY_NAME = "key." + Shopkeeper.MOD_ID + ".open_shop";
	public static final String CATEGORY_NAMESPACE = Shopkeeper.MOD_ID;
	public static final String CATEGORY_PATH = "shopkeeper";
	public static final String CATEGORY_LANG_KEY = "key.category." + CATEGORY_NAMESPACE + "." + CATEGORY_PATH;

	/** Title the server gives the shop menu; page keys only act on a container screen with this title. */
	public static final String SHOP_TITLE = "Shop";
	public static final int PREV_PAGE_SLOT = ShopRouter.PREV_PAGE_SLOT;
	public static final int NEXT_PAGE_SLOT = ShopRouter.NEXT_PAGE_SLOT;

	/** A client key (and matching Controlify binding) that sends one command. Only Open Shop has a default key. */
	public enum Action {
		OPEN_SHOP("open_shop", COMMAND, true),
		SELL_HAND("sell_hand", "sell", false),
		SELL_ALL("sell_all", "sell all", false),
		SELL_INVENTORY("sell_inventory", "sell inventory", false),
		SELL_UNDO("sell_undo", "sell undo", false),
		SELL_WAND("sell_wand", "shop wand", false);

		private final String path;
		private final String command;
		private final boolean boundByDefault;

		Action(String path, String command, boolean boundByDefault) {
			this.path = path;
			this.command = command;
			this.boundByDefault = boundByDefault;
		}

		/** Id path, shared by the key name and the Controlify binding id. */
		public String path() { return path; }

		/** Command sent without the leading slash. */
		public String command() { return command; }

		public boolean boundByDefault() { return boundByDefault; }

		public String keyName() { return "key." + Shopkeeper.MOD_ID + "." + path; }
	}

	public static boolean isShopTitle(String title) {
		return SHOP_TITLE.equals(title);
	}

	private ShopKeybind() {}
}
