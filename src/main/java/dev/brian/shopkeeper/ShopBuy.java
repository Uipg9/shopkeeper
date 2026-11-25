package dev.brian.shopkeeper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.brian.shopkeeper.core.Economy;
import dev.brian.shopkeeper.core.ItemQuery;
import dev.brian.shopkeeper.core.ShopConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** /buy &lt;item&gt; [quantity]: buys config items straight into the inventory, same rules as the menu. */
final class ShopBuy {
	private ShopBuy() {}

	static final int MAX_QUANTITY = 2304; // 36 slots x 64

	/** Short ids of config items; items in the player's unlocked tiers only, unless none of those match the prefix. */
	static CompletableFuture<Suggestions> suggest(CommandSourceStack source, SuggestionsBuilder builder) {
		ShopService svc = ShopService.get();
		if (svc == null) return builder.buildFuture();
		ShopConfig cfg = svc.config();
		Economy eco = svc.economy();
		ServerPlayer p = source.getPlayer();
		String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
		List<String> unlocked = new ArrayList<>();
		List<String> all = new ArrayList<>();
		for (ShopConfig.Item it : cfg.items()) {
			String s = ItemQuery.shortId(it.id());
			if (!s.startsWith(prefix)) continue;
			all.add(s);
			if (p != null && eco.isUnlocked(p.getUUID(), it.tier())) unlocked.add(s);
		}
		return SharedSuggestionProvider.suggest(unlocked.isEmpty() ? all : unlocked, builder);
	}

	static int buy(CommandSourceStack source, String query, int qty) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) return fail(source, p, "The shop is not ready.");
		if (qty < 1) return fail(source, p, "Quantity must be at least 1.");
		svc.refreshDeal();
		Economy eco = svc.economy();
		ShopConfig cfg = svc.config();
		UUID uuid = p.getUUID();
		boolean fresh = !eco.hasPlayer(uuid);
		eco.ensurePlayer(uuid);
		if (fresh) svc.save();

		ItemQuery.Result found = ItemQuery.resolve(query, cfg.items().stream().map(ShopConfig.Item::id).toList());
		if (!found.ok()) return fail(source, p, found.error());
		ShopConfig.Item it = cfg.item(found.id());
		Identifier rl = Identifier.tryParse(it.id());
		Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
		if (item == null || new ItemStack(item).isEmpty()) return fail(source, p, "The shop cannot sell " + ItemQuery.shortId(it.id()) + ".");
		String name = new ItemStack(item).getHoverName().getString();
		if (!eco.isUnlocked(uuid, it.tier())) {
			ShopConfig.Tier t = cfg.tier(it.tier());
			String tierName = t == null ? it.tier() : t.name();
			return fail(source, p, name + " is in the " + tierName + " tier - unlock it in /shop first.");
		}

		int max = new ItemStack(item).getMaxStackSize();
		ItemStack probe = new ItemStack(item, 1);
		int fits = 0;
		for (ItemStack in : p.getInventory().getNonEquipmentItems()) {
			if (in.isEmpty()) fits += max;
			else if (ItemStack.isSameItemSameComponents(in, probe)) fits += Math.max(0, max - in.getCount());
		}

		Economy.Result r = eco.buy(uuid, it.id(), qty, fits);
		if (!r.ok()) {
			String msg = switch (r.error()) {
				case "insufficient funds" -> notEnough(eco, uuid, it, name, Math.min(qty, fits));
				case "no room in inventory" -> "Your inventory is full.";
				case "tier locked" -> "That tier is locked.";
				default -> "Cannot buy: " + r.error();
			};
			return fail(source, p, msg);
		}

		long unit = r.charged() / r.delivered();
		long refunded = 0;
		int left = 0;
		try {
			Inventory inv = p.getInventory();
			int remaining = r.delivered();
			while (remaining > 0) {
				int n = Math.min(max, remaining);
				remaining -= n;
				ItemStack give = new ItemStack(item, n);
				inv.add(give);
				if (!give.isEmpty()) left += give.getCount();
			}
			if (left > 0) {
				refunded = Math.multiplyExact(unit, (long) left);
				eco.refund(uuid, refunded);
				Shopkeeper.LOGGER.error("Shop: {} of {} did not fit despite capacity check; refunded {} coins (player {})",
					left, it.id(), refunded, uuid);
			}
		} catch (RuntimeException ex) {
			refundQuietly(eco, uuid, r.charged(), it.id());
			svc.save();
			throw ex;
		}
		svc.save();

		long paid = r.charged() - refunded;
		int got = r.delivered() - left;
		String msg = "Bought " + got + " x " + name + " for " + paid + " coins.";
		if (r.delivered() < qty || refunded > 0) msg += " Your inventory only had room for " + got + ".";
		final String done = msg;
		p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
		source.sendSuccess(() -> Component.literal(done), false);
		return Command.SINGLE_SUCCESS;
	}

	private static String notEnough(Economy eco, UUID uuid, ShopConfig.Item it, String name, int qty) {
		long bal = eco.balance(uuid);
		String cost;
		try {
			cost = Long.toString(Math.multiplyExact(eco.buyPrice(it), (long) qty));
		} catch (ArithmeticException ex) {
			cost = "more than you can afford";
		}
		return "Not enough coins: " + qty + " x " + name + " costs " + cost + ", you have " + bal + ".";
	}

	private static void refundQuietly(Economy eco, UUID uuid, long amount, String itemId) {
		try {
			eco.refund(uuid, amount);
			Shopkeeper.LOGGER.error("Shop: delivery of {} failed; refunded {} coins (player {})", itemId, amount, uuid);
		} catch (RuntimeException refundEx) {
			Shopkeeper.LOGGER.error("Shop: delivery of {} failed AND refund of {} coins failed (player {})", itemId, amount, uuid, refundEx);
		}
	}

	private static int fail(CommandSourceStack source, ServerPlayer p, String msg) {
		p.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 1.0f);
		source.sendFailure(Component.literal(msg));
		return 0;
	}
}
