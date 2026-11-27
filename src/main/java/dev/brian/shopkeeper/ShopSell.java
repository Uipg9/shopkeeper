package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.Economy;
import dev.brian.shopkeeper.core.ShopConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shared slot sell used by the sell commands, plus the short-lived undo log. Does not save; callers do. */
final class ShopSell {
	private ShopSell() {}

	record Outcome(boolean ok, int removed, long credited, String itemId, String error) {}

	/** A command sale that can be bought back for the same coins within UNDO_TICKS. */
	record LastSale(List<ItemStack> items, long credited, long gameTime) {}

	static final long UNDO_TICKS = 30 * 20;
	private static final Map<UUID, LastSale> LAST = new ConcurrentHashMap<>();

	/** True for a non-empty stack with no data components changed (only those can be sold). */
	static boolean isPlain(ItemStack s) {
		return !s.isEmpty() && s.getComponentsPatch().isEmpty()
			&& ItemStack.isSameItemSameComponents(s, s.getItem().getDefaultInstance());
	}

	/** Sells the whole stack in an inventory slot; the real stack is shrunk only after the economy succeeded. */
	static Outcome sellSlot(ShopService svc, ServerPlayer player, int invSlot) {
		Inventory inv = player.getInventory();
		ItemStack real = inv.getItem(invSlot);
		if (real.isEmpty()) return new Outcome(false, 0, 0, null, "Nothing to sell.");
		if (!isPlain(real)) return new Outcome(false, 0, 0, null, "Only unmodified items can be sold.");
		String itemId = BuiltInRegistries.ITEM.getKey(real.getItem()).toString();
		ShopConfig.Item it = svc.config().item(itemId);
		if (it == null || !it.sellable()) return new Outcome(false, 0, 0, itemId, "The shop does not buy that.");
		Economy.Result r = svc.economy().sell(player.getUUID(), it.id(), real.getCount());
		if (!r.ok()) return new Outcome(false, 0, 0, itemId, dev.brian.shopkeeper.core.SellPlan.failMessage(r.error()));
		real.shrink(r.removed());
		return new Outcome(true, r.removed(), r.credited(), itemId, null);
	}

	/** Collects the sold items of one command into an undo entry. */
	static final class Recorder {
		private final List<ItemStack> items = new ArrayList<>();
		private long credited;

		void add(Outcome o) {
			if (!o.ok() || o.removed() <= 0) return;
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(o.itemId()));
			items.add(new ItemStack(item, o.removed()));
			credited += o.credited();
		}

		void commit(ServerPlayer p) {
			if (items.isEmpty()) return;
			LAST.put(p.getUUID(), new LastSale(List.copyOf(items), credited, p.level().getGameTime()));
		}
	}

	/** Buys back the last command sale at the same price. Returns null on success, or an error message. */
	static String undo(ShopService svc, ServerPlayer p) {
		LastSale sale = LAST.get(p.getUUID());
		long now = p.level().getGameTime();
		if (sale == null || now - sale.gameTime() > UNDO_TICKS || now < sale.gameTime()) {
			LAST.remove(p.getUUID());
			return "Nothing to undo (sales can be undone for 30 seconds).";
		}
		if (sale.credited() > 0) {
			Economy.Result r = svc.economy().spend(p.getUUID(), sale.credited());
			if (!r.ok()) return "You need " + sale.credited() + " coins to buy that back.";
		}
		LAST.remove(p.getUUID());
		for (ItemStack s : sale.items()) {
			int left = s.getCount();
			int max = s.getMaxStackSize();
			while (left > 0) {
				ItemStack part = s.copyWithCount(Math.min(max, left));
				left -= part.getCount();
				p.getInventory().add(part);
				if (!part.isEmpty()) p.drop(part, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		return null;
	}
}
