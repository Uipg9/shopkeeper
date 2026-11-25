package dev.brian.shopkeeper;

/** Names for the client "Open Shop" key. Pure constants so they can be unit-tested without a client. */
public final class ShopKeybind {
	/** Command sent when the key is pressed (same as typing /shop). */
	public static final String COMMAND = "shop";
	public static final String KEY_NAME = "key." + Shopkeeper.MOD_ID + ".open_shop";
	public static final String CATEGORY_NAMESPACE = Shopkeeper.MOD_ID;
	public static final String CATEGORY_PATH = "shopkeeper";

	private ShopKeybind() {}
}
