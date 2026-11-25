package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.Economy;
import dev.brian.shopkeeper.core.ShopConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Shared single-slot sell used by the /shop sell commands. Does not save; callers do. */
final class ShopSell {
	private ShopSell() {}

	record Outcome(boolean ok, int removed, long credited, String itemId, String error) {}

	/** Sells the whole stack in an inventory slot; the real stack is shrunk only after the economy succeeded. */
	static Outcome sellSlot(ShopService svc, ServerPlayer player, int invSlot) {
		Inventory inv = player.getInventory();
		ItemStack real = inv.getItem(invSlot);
		if (real.isEmpty()) return new Outcome(false, 0, 0, null, "Nothing to sell.");
		if (!real.getComponentsPatch().isEmpty()
			|| !ItemStack.isSameItemSameComponents(real, real.getItem().getDefaultInstance())) {
			return new Outcome(false, 0, 0, null, "Only unmodified items can be sold.");
		}
		String itemId = BuiltInRegistries.ITEM.getKey(real.getItem()).toString();
		ShopConfig.Item it = svc.config().item(itemId);
		if (it == null || !it.sellable()) return new Outcome(false, 0, 0, itemId, "The shop does not buy that.");
		Economy.Result r = svc.economy().sell(player.getUUID(), it.id(), real.getCount());
		if (!r.ok()) return new Outcome(false, 0, 0, itemId, dev.brian.shopkeeper.core.SellPlan.failMessage(r.error()));
		real.shrink(r.removed());
		return new Outcome(true, r.removed(), r.credited(), itemId, null);
	}
}
