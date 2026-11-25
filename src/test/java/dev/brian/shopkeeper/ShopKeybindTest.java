package dev.brian.shopkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShopKeybindTest {
	@Test
	void sendsTheShopCommand() {
		assertEquals("shop", ShopKeybind.COMMAND);
	}

	@Test
	void keyAndCategoryUseTheModNamespace() {
		assertEquals("key.shopkeeper.open_shop", ShopKeybind.KEY_NAME);
		assertEquals(Shopkeeper.MOD_ID, ShopKeybind.CATEGORY_NAMESPACE);
		assertEquals("shopkeeper", ShopKeybind.CATEGORY_PATH);
	}
}
