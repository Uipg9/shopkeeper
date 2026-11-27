package dev.brian.shopkeeper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.brian.shopkeeper.core.SellPlan;
import dev.brian.shopkeeper.core.ShopConfig;

import java.util.List;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ShopCommands {
	private ShopCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
			dispatcher.register(Commands.literal("sell")
				.executes(ctx -> sellHand(ctx.getSource()))
				.then(Commands.literal("all").executes(ctx -> sellAll(ctx.getSource(), true)))
				.then(Commands.literal("inventory").executes(ctx -> sellAll(ctx.getSource(), false)))
				.then(Commands.literal("undo").executes(ctx -> undo(ctx.getSource()))));
			dispatcher.register(Commands.literal("worth").executes(ctx -> worth(ctx.getSource())));
			dispatcher.register(Commands.literal("sellwand")
				.executes(ctx -> openWandTab(ctx.getSource()))
				.then(Commands.literal("buy").executes(ctx -> wandAction(ctx.getSource(), SellWand::buy, "Bought a Sell Wand.")))
				.then(Commands.literal("upgrade").executes(ctx -> wandAction(ctx.getSource(), SellWand::upgrade, "Sell Wand upgraded.")))
				.then(Commands.literal("recharge").executes(ctx -> wandAction(ctx.getSource(), SellWand::recharge, "Sell Wand recharged."))));
			// String (not word) so quoted "minecraft:diamond" or "iron ingot" also parse.
			dispatcher.register(Commands.literal("buy")
				.then(Commands.argument("item", StringArgumentType.string())
					.suggests((ctx, b) -> ShopBuy.suggest(ctx.getSource(), b))
					.executes(ctx -> ShopBuy.buy(ctx.getSource(), StringArgumentType.getString(ctx, "item"), 1))
					.then(Commands.argument("quantity", IntegerArgumentType.integer(1, ShopBuy.MAX_QUANTITY))
						.executes(ctx -> ShopBuy.buy(ctx.getSource(), StringArgumentType.getString(ctx, "item"),
							IntegerArgumentType.getInteger(ctx, "quantity"))))));
			dispatcher.register(Commands.literal("shop")
				.executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					if (ShopService.get() == null) {
						ctx.getSource().sendFailure(Component.literal("The shop is not ready."));
						return 0;
					}
					new ShopGui(p).open();
					return Command.SINGLE_SUCCESS;
				})
				.then(Commands.literal("balance").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					ShopService svc = ShopService.get();
					if (svc == null) {
						ctx.getSource().sendFailure(Component.literal("The shop is not ready."));
						return 0;
					}
					boolean fresh = !svc.economy().hasPlayer(p.getUUID());
					svc.economy().ensurePlayer(p.getUUID());
					if (fresh) svc.save();
					long bal = svc.economy().balance(p.getUUID());
					ctx.getSource().sendSuccess(() -> Component.literal("Balance: " + bal + " coins"), false);
					return Command.SINGLE_SUCCESS;
				}))
				.then(Commands.literal("report").executes(ctx -> report(ctx.getSource())))
				.then(Commands.literal("sell").executes(ctx -> sellHand(ctx.getSource())))
				.then(Commands.literal("sellall").executes(ctx -> sellAll(ctx.getSource(), false)))
				.then(Commands.literal("wand").executes(ctx -> openWandTab(ctx.getSource())))
				.then(Commands.literal("reload")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> reload(ctx.getSource()))));
		});
	}

	private static int report(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.refreshDeal();
		svc.economy().ensurePlayer(p.getUUID());
		Component msg = ShopService.reportMessage(svc.economy().today(p.getUUID()));
		source.sendSuccess(() -> msg, false);
		return Command.SINGLE_SUCCESS;
	}

	private static int sellHand(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.refreshDeal();
		svc.economy().ensurePlayer(p.getUUID());
		ShopSell.Outcome o = ShopSell.sellSlot(svc, p, p.getInventory().getSelectedSlot());
		if (!o.ok()) {
			source.sendFailure(Component.literal(o.error()));
			return 0;
		}
		ShopSell.Recorder rec = new ShopSell.Recorder();
		rec.add(o);
		rec.commit(p);
		svc.save();
		String name = BuiltInRegistries.ITEM.getValue(Identifier.parse(o.itemId())).getDefaultInstance().getHoverName().getString();
		source.sendSuccess(() -> Component.literal("Sold " + o.removed() + " x " + name + " for " + o.credited() + " coins."), false);
		return Command.SINGLE_SUCCESS;
	}

	/** heldOnly: sell only stacks of the held item type (/sell all); otherwise everything sellable (/sell inventory). */
	private static int sellAll(CommandSourceStack source, boolean heldOnly) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.refreshDeal();
		svc.economy().ensurePlayer(p.getUUID());
		ItemStack held = p.getMainHandItem();
		if (heldOnly && held.isEmpty()) {
			source.sendFailure(Component.literal("Hold the item you want to sell, or use /sell inventory."));
			return 0;
		}
		Item heldItem = held.getItem();
		SellPlan.Totals totals = new SellPlan.Totals();
		ShopSell.Recorder rec = new ShopSell.Recorder();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack s = p.getInventory().getItem(slot);
			if (s.isEmpty()) continue;
			if (heldOnly && (!s.is(heldItem) || !ShopSell.isPlain(s))) continue;
			ShopSell.Outcome o = ShopSell.sellSlot(svc, p, slot);
			if (o.ok()) totals.sold(o.removed(), o.credited());
			else totals.skip();
			rec.add(o);
		}
		rec.commit(p);
		if (totals.sold() > 0) svc.save();
		String msg = totals.summary();
		if (totals.sold() == 0) {
			source.sendFailure(Component.literal(msg));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(msg), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int undo(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.economy().ensurePlayer(p.getUUID());
		String err = ShopSell.undo(svc, p);
		if (err != null) {
			source.sendFailure(Component.literal(err));
			return 0;
		}
		svc.save();
		source.sendSuccess(() -> Component.literal("Sale undone - items returned."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int worth(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.refreshDeal();
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) {
			source.sendFailure(Component.literal("Hold an item to check its worth."));
			return 0;
		}
		String name = held.getHoverName().getString();
		ShopConfig.Item it = svc.config().item(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
		if (it == null || !it.sellable()) {
			source.sendFailure(Component.literal("The shop does not buy " + name + "."));
			return 0;
		}
		if (!ShopSell.isPlain(held)) {
			source.sendFailure(Component.literal("Only unmodified items can be sold."));
			return 0;
		}
		long each = svc.economy().sellPrice(it);
		int total = 0;
		for (int slot = 0; slot < 36; slot++) {
			ItemStack s = p.getInventory().getItem(slot);
			if (s.is(held.getItem()) && ShopSell.isPlain(s)) total += s.getCount();
		}
		long stack = each * held.getCount();
		long all = each * total;
		int count = total;
		source.sendSuccess(() -> Component.literal(name + ": " + each + " each, " + stack + " for this stack, "
			+ all + " for all " + count + " (/sell all)").withStyle(ChatFormatting.GOLD), false);
		return Command.SINGLE_SUCCESS;
	}

	private interface WandOp {
		String run(ShopService svc, ServerPlayer p);
	}

	private static int wandAction(CommandSourceStack source, WandOp op, String okMsg) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.economy().ensurePlayer(p.getUUID());
		String err = op.run(svc, p);
		if (err != null) {
			source.sendFailure(Component.literal(err));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(okMsg), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int openWandTab(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		if (ShopService.get() == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		ShopGui gui = new ShopGui(p);
		gui.showWandTab();
		gui.open();
		return Command.SINGLE_SUCCESS;
	}

	private static int reload(CommandSourceStack source) {
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		List<String> errors = svc.reload();
		if (errors.isEmpty()) {
			// Menus read config live, but close shop menus so no stale tab layout stays on screen.
			ShopGui.closeAllOpen();
			source.sendSuccess(() -> Component.literal("Shop config reloaded."), true);
			return Command.SINGLE_SUCCESS;
		}
		for (String e : errors) source.sendFailure(Component.literal(e).withStyle(ChatFormatting.RED));
		return 0;
	}
}
