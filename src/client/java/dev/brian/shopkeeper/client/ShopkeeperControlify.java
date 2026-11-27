package dev.brian.shopkeeper.client;

import dev.brian.shopkeeper.ShopKeybind;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.entrypoint.InitContext;
import dev.isxander.controlify.api.entrypoint.PreInitContext;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Optional Controlify hook: only loaded by Controlify's "controlify" entrypoint, so the mod runs fine
 * without it. Adds an unbound controller binding per shop key that emulates that key; their entries in
 * assets/controlify/radial_icons.json make them radial menu candidates (radialCandidate() is deprecated).
 * Also pages the shop menu with Controlify's own "next/previous tab" bindings (RB/LB by default).
 */
public class ShopkeeperControlify implements ControlifyEntrypoint {
	@Override
	public void onControlifyPreInit(PreInitContext ctx) {
		for (ShopKeybind.Action action : ShopKeybind.Action.values()) {
			KeyMapping key = ShopkeeperClient.key(action);
			ctx.bindings().registerBinding(builder -> builder
				.id(ShopKeybind.CATEGORY_NAMESPACE, action.path())
				.name(Component.translatable(action.keyName()))
				.category(Component.translatable(ShopKeybind.CATEGORY_LANG_KEY))
				.allowedContexts(BindContext.IN_GAME)
				.addKeyCorrelation(key)
				.keyEmulation(key));
		}
	}

	@Override
	public void onControlifyInit(InitContext ctx) {
		ControlifyEvents.ACTIVE_CONTROLLER_TICKED.register(event -> {
			Minecraft mc = Minecraft.getInstance();
			if (!ShopkeeperClient.isShopScreen(mc.gui.screen())) return;
			ControllerEntity controller = event.controller();
			if (justPressed(ControlifyBindings.GUI_PREV_TAB, controller)) ShopkeeperClient.clickShopPage(mc, false);
			if (justPressed(ControlifyBindings.GUI_NEXT_TAB, controller)) ShopkeeperClient.clickShopPage(mc, true);
		});
	}

	private static boolean justPressed(InputBindingSupplier supplier, ControllerEntity controller) {
		InputBinding binding = supplier.onOrNull(controller);
		return binding != null && binding.justPressed();
	}

	@Override
	public void onControllersDiscovered(ControlifyApi controlify) {}
}
