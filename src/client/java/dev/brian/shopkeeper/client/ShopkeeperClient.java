package dev.brian.shopkeeper.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.brian.shopkeeper.ShopKeybind;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Registers the shop keys (Open Shop on B, the sell keys unbound; all rebindable in Controls) and
 * PageUp/PageDown paging inside the shop menu. With Controlify installed, {@link ShopkeeperControlify}
 * adds matching controller bindings that press these keys, plus bumper paging.
 */
public class ShopkeeperClient implements ClientModInitializer {
	private static final Map<ShopKeybind.Action, KeyMapping> KEYS = new EnumMap<>(ShopKeybind.Action.class);

	/** The key for an action, registered on first use (Controlify's entrypoint may run before ours). */
	public static synchronized KeyMapping key(ShopKeybind.Action action) {
		registerKeys();
		return KEYS.get(action);
	}

	/** The Open Shop key. */
	public static KeyMapping openShopKey() {
		return key(ShopKeybind.Action.OPEN_SHOP);
	}

	private static void registerKeys() {
		if (!KEYS.isEmpty()) return;
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(ShopKeybind.CATEGORY_NAMESPACE, ShopKeybind.CATEGORY_PATH));
		for (ShopKeybind.Action action : ShopKeybind.Action.values()) {
			int defaultKey = action.boundByDefault() ? InputConstants.KEY_B : InputConstants.UNKNOWN.getValue();
			KEYS.put(action, KeyMappingHelper.registerKeyMapping(
				new KeyMapping(action.keyName(), InputConstants.Type.KEYBOARD, defaultKey, category)));
		}
	}

	@Override
	public void onInitializeClient() {
		synchronized (ShopkeeperClient.class) {
			registerKeys();
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			for (ShopKeybind.Action action : ShopKeybind.Action.values()) {
				KeyMapping key = KEYS.get(action);
				while (key.consumeClick()) {
					if (mc.player != null && mc.gui.screen() == null) {
						mc.player.connection.sendCommand(action.command());
					}
				}
			}
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (!isShopScreen(screen)) return;
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
				if (event.key() == InputConstants.KEY_PAGEUP) clickShopPage(Minecraft.getInstance(), false);
				else if (event.key() == InputConstants.KEY_PAGEDOWN) clickShopPage(Minecraft.getInstance(), true);
			});
		});
	}

	static boolean isShopScreen(Screen screen) {
		return screen instanceof AbstractContainerScreen<?> && ShopKeybind.isShopTitle(screen.getTitle().getString());
	}

	/**
	 * Left-clicks the shop's Previous/Next page button if the shop menu is open. The server ignores the
	 * click when there is no such page, so this is safe on the first/last page.
	 */
	public static void clickShopPage(Minecraft mc, boolean next) {
		if (mc.player == null || mc.gameMode == null) return;
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !isShopScreen(screen)) return;
		int slot = next ? ShopKeybind.NEXT_PAGE_SLOT : ShopKeybind.PREV_PAGE_SLOT;
		if (slot >= screen.getMenu().slots.size()) return;
		mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
	}
}
