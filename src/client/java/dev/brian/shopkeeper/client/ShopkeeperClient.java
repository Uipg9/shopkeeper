package dev.brian.shopkeeper.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.brian.shopkeeper.ShopKeybind;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Registers the "Open Shop" key (default B, rebindable in Controls). With Controlify installed,
 * {@link ShopkeeperControlify} adds a matching controller binding that presses this key.
 */
public class ShopkeeperClient implements ClientModInitializer {
	private static KeyMapping openShop;

	/** The Open Shop key, registered on first use (Controlify's entrypoint may run before ours). */
	public static synchronized KeyMapping openShopKey() {
		if (openShop == null) {
			KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(ShopKeybind.CATEGORY_NAMESPACE, ShopKeybind.CATEGORY_PATH));
			openShop = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(ShopKeybind.KEY_NAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));
		}
		return openShop;
	}

	@Override
	public void onInitializeClient() {
		KeyMapping key = openShopKey();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null) {
					mc.player.connection.sendCommand(ShopKeybind.COMMAND);
				}
			}
		});
	}
}
