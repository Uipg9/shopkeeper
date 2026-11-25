package dev.brian.shopkeeper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.brian.shopkeeper.core.SellPlan;

import java.util.List;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class ShopCommands {
	private ShopCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
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
				.then(Commands.literal("sell").executes(ctx -> sellHand(ctx.getSource())))
				.then(Commands.literal("sellall").executes(ctx -> sellAll(ctx.getSource())))
				.then(Commands.literal("reload")
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> reload(ctx.getSource())))));
	}

	private static int sellHand(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.economy().ensurePlayer(p.getUUID());
		ShopSell.Outcome o = ShopSell.sellSlot(svc, p, p.getInventory().getSelectedSlot());
		if (!o.ok()) {
			source.sendFailure(Component.literal(o.error()));
			return 0;
		}
		svc.save();
		String name = BuiltInRegistries.ITEM.getValue(Identifier.parse(o.itemId())).getDefaultInstance().getHoverName().getString();
		source.sendSuccess(() -> Component.literal("Sold " + o.removed() + " x " + name + " for " + o.credited() + " coins."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int sellAll(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer p = source.getPlayerOrException();
		ShopService svc = ShopService.get();
		if (svc == null) {
			source.sendFailure(Component.literal("The shop is not ready."));
			return 0;
		}
		svc.economy().ensurePlayer(p.getUUID());
		SellPlan.Totals totals = new SellPlan.Totals();
		for (int slot = 0; slot < 36; slot++) {
			if (p.getInventory().getItem(slot).isEmpty()) continue;
			ShopSell.Outcome o = ShopSell.sellSlot(svc, p, slot);
			if (o.ok()) totals.sold(o.removed(), o.credited());
			else totals.skip();
		}
		if (totals.sold() > 0) svc.save();
		String msg = totals.summary();
		if (totals.sold() == 0) {
			source.sendFailure(Component.literal(msg));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(msg), false);
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
