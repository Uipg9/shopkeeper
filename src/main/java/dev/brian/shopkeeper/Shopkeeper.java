package dev.brian.shopkeeper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Shopkeeper implements ModInitializer {
	public static final String MOD_ID = "shopkeeper";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ShopCommands.register();
		SellWand.register();
		ServerLifecycleEvents.SERVER_STARTED.register(ShopService::init);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ShopService.shutdown());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ShopService svc = ShopService.get();
			if (svc != null) svc.tick();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ShopService svc = ShopService.get();
			if (svc == null) return;
			svc.refreshDeal();
			if (svc.sendPendingReport(handler.getPlayer())) svc.save();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> handler.getPlayer().closeContainer());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) player.closeContainer();
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Translation key following the vanilla convention: {@code category.modid.path}. */
	public static String langKey(String category, String path) {
		return category + "." + MOD_ID + "." + path;
	}
}
