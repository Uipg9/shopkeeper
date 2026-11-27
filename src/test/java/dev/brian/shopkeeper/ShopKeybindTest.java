package dev.brian.shopkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.brian.shopkeeper.core.ShopRouter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShopKeybindTest {
	@Test
	void sendsTheShopCommand() {
		assertEquals("shop", ShopKeybind.COMMAND);
		assertEquals("shop", ShopKeybind.Action.OPEN_SHOP.command());
	}

	@Test
	void keyAndCategoryUseTheModNamespace() {
		assertEquals("key.shopkeeper.open_shop", ShopKeybind.KEY_NAME);
		assertEquals(ShopKeybind.KEY_NAME, ShopKeybind.Action.OPEN_SHOP.keyName());
		assertEquals(Shopkeeper.MOD_ID, ShopKeybind.CATEGORY_NAMESPACE);
		assertEquals("shopkeeper", ShopKeybind.CATEGORY_PATH);
		assertEquals("key.category.shopkeeper.shopkeeper", ShopKeybind.CATEGORY_LANG_KEY);
	}

	@Test
	void sellActionsSendTheirCommands() {
		assertEquals("sell", ShopKeybind.Action.SELL_HAND.command());
		assertEquals("sell all", ShopKeybind.Action.SELL_ALL.command());
		assertEquals("sell inventory", ShopKeybind.Action.SELL_INVENTORY.command());
		assertEquals("sell undo", ShopKeybind.Action.SELL_UNDO.command());
		assertEquals("shop wand", ShopKeybind.Action.SELL_WAND.command());
	}

	@Test
	void onlyOpenShopHasADefaultKey() {
		for (ShopKeybind.Action action : ShopKeybind.Action.values()) {
			assertEquals(action == ShopKeybind.Action.OPEN_SHOP, action.boundByDefault(), action.name());
		}
	}

	@Test
	void actionIdsAreUniqueAndNamespaced() {
		List<String> paths = Arrays.stream(ShopKeybind.Action.values()).map(ShopKeybind.Action::path).toList();
		assertEquals(paths.size(), new HashSet<>(paths).size());
		for (ShopKeybind.Action action : ShopKeybind.Action.values()) {
			assertEquals("key.shopkeeper." + action.path(), action.keyName());
			assertFalse(action.command().startsWith("/"), action.name());
		}
	}

	@Test
	void pageSlotsMatchTheShopGui() {
		assertEquals(ShopRouter.PREV_PAGE_SLOT, ShopKeybind.PREV_PAGE_SLOT);
		assertEquals(ShopRouter.NEXT_PAGE_SLOT, ShopKeybind.NEXT_PAGE_SLOT);
		assertEquals("Shop", ShopKeybind.SHOP_TITLE);
		assertTrue(ShopKeybind.isShopTitle("Shop"));
		assertFalse(ShopKeybind.isShopTitle("Black Market"));
		assertFalse(ShopKeybind.isShopTitle(null));
	}
}
