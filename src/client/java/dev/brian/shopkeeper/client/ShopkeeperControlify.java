package dev.brian.shopkeeper.client;

import dev.brian.shopkeeper.ShopKeybind;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.bindings.BindContext;
import net.minecraft.network.chat.Component;

/**
 * Optional Controlify hook: only loaded by Controlify's "controlify" entrypoint, so the mod runs fine
 * without it. Adds an unbound "Open Shop" controller binding that emulates the Open Shop key.
 */
public class ShopkeeperControlify implements ControlifyEntrypoint {
	@Override
	public void onControlifyPreInit(PreInitContext ctx) {
		ctx.bindings().registerBinding(builder -> builder
			.id(ShopKeybind.CATEGORY_NAMESPACE, "open_shop")
			.name(Component.translatable(ShopKeybind.KEY_NAME))
			.category(Component.translatable("key.category." + ShopKeybind.CATEGORY_NAMESPACE + "." + ShopKeybind.CATEGORY_PATH))
			.allowedContexts(BindContext.IN_GAME)
			.keyEmulation(ShopkeeperClient.openShopKey()));
	}

	@Override
	public void onControlifyInit(InitContext ctx) {}

	@Override
	public void onControllersDiscovered(ControlifyApi controlify) {}
}
