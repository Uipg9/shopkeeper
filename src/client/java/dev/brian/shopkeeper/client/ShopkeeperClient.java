package dev.brian.shopkeeper.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.brian.shopkeeper.ShopKeybind;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Registers the "Open Shop" key (default B, rebindable in Controls). Controlify lists modded
 * key mappings, so it can be bound to a controller button there.
 */
public class ShopkeeperClient implements ClientModInitializer {
	public static KeyMapping openShop;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(ShopKeybind.CATEGORY_NAMESPACE, ShopKeybind.CATEGORY_PATH));
		openShop = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(ShopKeybind.KEY_NAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openShop.consumeClick()) {
				if (mc.player != null && mc.gui.screen() == null) {
					mc.player.connection.sendCommand(ShopKeybind.COMMAND);
				}
			}
		});
	}
}
