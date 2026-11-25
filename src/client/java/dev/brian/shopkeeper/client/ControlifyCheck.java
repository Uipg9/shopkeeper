package dev.brian.shopkeeper.client;

import dev.isxander.controlify.api.bind.ControlifyBindApi;
import net.minecraft.resources.Identifier;

/** Gametest helper; only loaded when Controlify is installed. */
final class ControlifyCheck {
	private ControlifyCheck() {}

	static boolean openShopBindingRegistered() {
		Identifier id = Identifier.fromNamespaceAndPath("shopkeeper", "open_shop");
		return ControlifyBindApi.get().getAllBindIds().anyMatch(id::equals);
	}
}
