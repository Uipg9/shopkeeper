package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.BlackMarket;
import dev.brian.shopkeeper.core.Economy;
import dev.brian.shopkeeper.core.ShopConfig;
import dev.brian.shopkeeper.core.ShopRouter;
import dev.brian.shopkeeper.core.ShopRouter.Action;
import dev.brian.shopkeeper.core.ShopRouter.ClickKind;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-side 9x6 shop menu. All decisions come from ShopRouter/Economy; config is read live on every click. */
public final class ShopGui extends SimpleGui {
	private static final Set<ShopGui> OPEN = ConcurrentHashMap.newKeySet();

	private final UUID id;
	private int selectedTab = 0;
	private boolean blackMarketSelected = false;
	/** 0-based page of the selected tier; reset on tab switch, clamped against the live config. */
	private int page = 0;

	/** Day the black market was last rendered with (eco.day()); a click on a different day is stale. */
	private long renderedDay = Long.MIN_VALUE;

	/** Offers for the given (monotonic shop) day, same seed as ShopService.refreshDeal. */
	private List<BlackMarket.Offer> offers(ShopService svc, long day) {
		var server = player.level().getServer();
		if (server == null) return List.of();
		return BlackMarket.generate(day, server.overworld().getSeed(), svc.config());
	}

	/** The tier of an offer's item, or null if the item is not in the config. */
	private static ShopConfig.Tier tierOf(ShopConfig cfg, BlackMarket.Offer o) {
		ShopConfig.Item it = cfg.item(o.itemId());
		return it == null ? null : cfg.tier(it.tier());
	}

	/** Name of the locked tier an offer needs, or null if the player may buy it. */
	private String lockedTierName(ShopConfig cfg, Economy eco, BlackMarket.Offer o) {
		ShopConfig.Item it = cfg.item(o.itemId());
		if (it == null || eco.isUnlocked(id, it.tier())) return null;
		ShopConfig.Tier t = tierOf(cfg, o);
		return t == null ? it.tier() : t.name();
	}

	/** Closes every shop menu currently on screen (used after a config reload). */
	public static void closeAllOpen() {
		for (ShopGui gui : new ArrayList<>(OPEN)) {
			OPEN.remove(gui);
			if (gui.isOpen()) gui.close();
		}
	}

	@Override
	public void onOpen() {
		OPEN.add(this);
	}

	@Override
	public void onPlayerClose(boolean success) {
		OPEN.remove(this);
	}

	public ShopGui(ServerPlayer player) {
		super(MenuType.GENERIC_9x6, player, false);
		this.id = player.getUUID();
		setTitle(Component.literal("Shop"));
		setLockPlayerInventory(true);
		ShopService svc = ShopService.get();
		if (svc != null) {
			boolean fresh = !svc.economy().hasPlayer(id);
			svc.economy().ensurePlayer(id);
			if (fresh) svc.save();
		}
		refresh();
	}

	private static Component plain(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(Style.EMPTY.withItalic(false).withColor(color));
	}

	private static Optional<Item> resolve(String itemId) {
		Identifier rl = Identifier.tryParse(itemId);
		if (rl == null) return Optional.empty();
		// Air-like items yield an empty stack: they can never be delivered.
		return BuiltInRegistries.ITEM.getOptional(rl).filter(i -> !new ItemStack(i).isEmpty());
	}

	private static String idOf(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Every buyable item of the selected tier (all pages); empty when locked. */
	private List<ShopConfig.Item> tierItems(ShopConfig cfg, Economy eco) {
		if (selectedTab >= cfg.tiers().size() || selectedTab >= ShopRouter.TAB_COUNT) return List.of();
		ShopConfig.Tier tier = cfg.tiers().get(selectedTab);
		if (!eco.isUnlocked(id, tier.id())) return List.of();
		return cfg.itemsIn(tier.id()).stream().filter(i -> resolve(i.id()).isPresent()).toList();
	}

	/** The items on the current page (page must already be clamped). */
	private List<ShopConfig.Item> pageItems(List<ShopConfig.Item> all) {
		int from = Math.min(all.size(), page * ShopRouter.PAGE_SIZE);
		return all.subList(from, Math.min(all.size(), from + ShopRouter.PAGE_SIZE));
	}

	private static GuiElementBuilder pageButton(String name, int page, int pages) {
		return new GuiElementBuilder(Items.ARROW).setName(plain(name, ChatFormatting.AQUA))
			.addLoreLine(Component.literal("Page " + (page + 1) + " of " + pages).withStyle(ChatFormatting.GRAY));
	}

	private void refresh() {
		for (int i = 0; i < 54; i++) clearSlot(i);
		ShopService svc = ShopService.get();
		if (svc == null) return;
		svc.refreshDeal();
		ShopConfig cfg = svc.config();
		Economy eco = svc.economy();
		if (selectedTab >= cfg.tiers().size()) selectedTab = 0;
		long day = eco.day();
		renderedDay = day;
		List<BlackMarket.Offer> offers = offers(svc, day);
		if (offers.isEmpty()) blackMarketSelected = false;
		List<ShopConfig.Item> all = tierItems(cfg, eco);
		int pages = ShopRouter.pageCount(all.size());
		page = ShopRouter.clampPage(page, all.size());

		for (int t = 0; t < ShopRouter.TAB_COUNT && t < cfg.tiers().size(); t++) {
			ShopConfig.Tier tier = cfg.tiers().get(t);
			GuiElementBuilder b;
			if (eco.isUnlocked(id, tier.id())) {
				Item icon = resolve(tier.icon()).orElse(Items.BARRIER);
				b = new GuiElementBuilder(icon).setName(plain(tier.name(), ChatFormatting.GOLD));
				if (t == selectedTab && !blackMarketSelected) b.glow();
				b.addLoreLine(Component.literal(t == selectedTab && !blackMarketSelected ? "Selected" : "Click to browse").withStyle(ChatFormatting.GRAY));
			} else {
				b = new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).setName(plain(tier.name(), ChatFormatting.RED))
					.addLoreLine(Component.literal("Locked - unlock for " + tier.unlockPrice() + " coins - click to buy").withStyle(ChatFormatting.GRAY));
			}
			setSlot(t, b);
		}
		if (!offers.isEmpty()) {
			GuiElementBuilder bm = new GuiElementBuilder(Items.SKELETON_SKULL).setName(plain("Black Market", ChatFormatting.DARK_PURPLE))
				.addLoreLine(Component.literal("Used gear from fallen adventurers. New stock every day. Can't be sold back.").withStyle(ChatFormatting.GRAY));
			if (blackMarketSelected) bm.glow();
			setSlot(ShopRouter.BLACK_MARKET_SLOT, bm);
		}
		GuiElementBuilder balance = new GuiElementBuilder(Items.GOLD_INGOT)
			.setName(plain("Balance: " + eco.balance(id), ChatFormatting.YELLOW))
			.addLoreLine(Component.literal("Click an item to buy 1").withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.literal("Shift-click to buy a stack").withStyle(ChatFormatting.GRAY))
			.addLoreLine(Component.literal("Click your own items to sell").withStyle(ChatFormatting.GRAY));
		if (!blackMarketSelected && pages > 1) {
			balance.addLoreLine(Component.literal("Page " + (page + 1) + "/" + pages).withStyle(ChatFormatting.GRAY));
		}
		setSlot(ShopRouter.BALANCE_SLOT, balance);
		setSlot(ShopRouter.CLOSE_SLOT, new GuiElementBuilder(Items.BARRIER).setName(plain("Close", ChatFormatting.RED)));

		if (blackMarketSelected) {
			var access = player.level().registryAccess();
			for (BlackMarket.Offer o : offers) {
				int slot = ShopRouter.ITEM_START + o.slot();
				if (o.slot() < 0 || slot >= ShopRouter.ITEM_END) continue;
				boolean sold = eco.isBlackMarketBought(id, day, o.slot());
				ItemStack stack = BlackMarketStock.display(access, o, sold, lockedTierName(cfg, eco, o));
				if (stack.isEmpty()) continue;
				if (sold) {
					setSlot(slot, new GuiElementBuilder(Items.STAINED_GLASS_PANE.gray()).setName(plain("Sold out", ChatFormatting.GRAY)));
				} else {
					setSlot(slot, stack);
				}
			}
			return;
		}
		if (page > 0) setSlot(ShopRouter.PREV_PAGE_SLOT, pageButton("Previous page", page, pages));
		if (page < pages - 1) setSlot(ShopRouter.NEXT_PAGE_SLOT, pageButton("Next page", page, pages));
		List<ShopConfig.Item> shown = pageItems(all);
		for (int i = 0; i < shown.size(); i++) {
			ShopConfig.Item it = shown.get(i);
			Item item = resolve(it.id()).orElse(Items.BARRIER);
			long buyNow = eco.buyPrice(it);
			long sellNow = eco.sellPrice(it);
			String lore = it.sellable() ? "Buy: " + buyNow + "  Sell: " + sellNow : "Buy: " + buyNow + "  Not sellable";
			GuiElementBuilder eb = new GuiElementBuilder(item)
				.addLoreLine(Component.literal(lore).withStyle(ChatFormatting.GRAY));
			if (it.sellable() && sellNow != it.sell()) {
				eb.addLoreLine(Component.literal("HOT: sells for " + sellNow + " (normal " + it.sell() + ")").withStyle(ChatFormatting.GOLD));
				eb.glow();
			}
			if (buyNow != it.buy()) {
				eb.addLoreLine(Component.literal("SALE: buy for " + buyNow + " (normal " + it.buy() + ")").withStyle(ChatFormatting.GOLD));
				eb.glow();
			}
			setSlot(ShopRouter.ITEM_START + i, eb);
		}
	}

	private static ClickKind kind(ClickType type) {
		if (type == ClickType.MOUSE_LEFT) return ClickKind.LEFT;
		if (type == ClickType.MOUSE_LEFT_SHIFT) return ClickKind.SHIFT_LEFT;
		if (type == ClickType.MOUSE_RIGHT) return ClickKind.RIGHT;
		if (type == ClickType.MOUSE_RIGHT_SHIFT) return ClickKind.SHIFT_RIGHT;
		return ClickKind.OTHER;
	}

	@Override
	public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
		try {
			handle(index, kind(type));
		} catch (RuntimeException ex) {
			Shopkeeper.LOGGER.error("Shop click failed", ex);
		}
		return false; // always cancel vanilla handling
	}

	private void handle(int index, ClickKind kind) {
		ShopService svc = ShopService.get();
		if (svc == null) {
			close();
			return;
		}
		svc.refreshDeal();
		ShopConfig cfg = svc.config();
		Economy eco = svc.economy();
		boolean fresh = !eco.hasPlayer(id);
		eco.ensurePlayer(id);
		if (fresh) svc.save();
		boolean bmShown = !offers(svc, eco.day()).isEmpty();
		if (!bmShown) blackMarketSelected = false;
		int itemCount = tierItems(cfg, eco).size();
		int pages = ShopRouter.pageCount(itemCount);
		if (page != ShopRouter.clampPage(page, itemCount)) {
			// The tier shrank under the open menu: the shown page is stale, so redraw and ignore this click.
			refresh();
			return;
		}
		Action act = ShopRouter.route(index, kind, t -> t < cfg.tiers().size() && eco.isUnlocked(id, cfg.tiers().get(t).id()),
			bmShown, blackMarketSelected, page > 0, page < pages - 1);
		switch (act.type()) {
			case NONE -> { }
			case CLOSE -> close();
			case SELECT_TAB -> {
				selectedTab = act.index();
				blackMarketSelected = false;
				page = 0;
				refresh();
			}
			case PREV_PAGE -> {
				page = ShopRouter.clampPage(page - 1, itemCount);
				refresh();
			}
			case NEXT_PAGE -> {
				page = ShopRouter.clampPage(page + 1, itemCount);
				refresh();
			}
			case SELECT_BLACK_MARKET -> {
				blackMarketSelected = true;
				refresh();
			}
			case BUY_OFFER -> buyOffer(svc, act.index());
			case UNLOCK -> {
				if (act.index() >= cfg.tiers().size()) return;
				Economy.Result r = eco.unlock(id, cfg.tiers().get(act.index()).id());
				if (r.ok()) {
					selectedTab = act.index();
					blackMarketSelected = false;
					page = 0;
					svc.save();
					ok();
				} else {
					fail("Not enough coins to unlock this tier.");
				}
				refresh();
			}
			case BUY_ONE -> buy(svc, act.index(), false);
			case BUY_STACK -> buy(svc, act.index(), true);
			case SELL_ONE -> sell(svc, act.index(), false);
			case SELL_STACK -> sell(svc, act.index(), true);
		}
	}

	private void buy(ShopService svc, int itemIndex, boolean stack) {
		ShopConfig cfg = svc.config();
		Economy eco = svc.economy();
		// Map the in-page index to the absolute index and re-check it against the live config.
		List<ShopConfig.Item> all = tierItems(cfg, eco);
		if (itemIndex < 0 || itemIndex >= ShopRouter.PAGE_SIZE) return;
		long abs = (long) page * ShopRouter.PAGE_SIZE + itemIndex;
		if (abs >= all.size()) return;
		ShopConfig.Item it = all.get((int) abs);
		Item item = resolve(it.id()).orElse(null);
		if (item == null) return;

		int max = new ItemStack(item).getMaxStackSize();
		int qty = 1;
		if (stack) {
			qty = max;
			if (it.maxStack() != null) qty = Math.min(qty, it.maxStack());
			qty = Math.max(1, qty);
		}
		ItemStack probe = new ItemStack(item, 1);
		int fits = 0;
		for (ItemStack in : player.getInventory().getNonEquipmentItems()) {
			if (in.isEmpty()) fits += max;
			else if (ItemStack.isSameItemSameComponents(in, probe)) fits += Math.max(0, max - in.getCount());
		}

		Economy.Result r = eco.buy(id, it.id(), qty, fits);
		if (!r.ok()) {
			String msg = switch (r.error()) {
				case "insufficient funds" -> "Not enough coins.";
				case "no room in inventory" -> "Your inventory is full.";
				case "tier locked" -> "That tier is locked.";
				default -> "Cannot buy: " + r.error();
			};
			fail(msg);
			return;
		}
		long unit = r.charged() / r.delivered();
		try {
			ItemStack give = new ItemStack(item, r.delivered());
			player.getInventory().add(give);
			int left = give.isEmpty() ? 0 : give.getCount();
			if (left > 0) {
				long back = Math.multiplyExact(unit, (long) left);
				eco.refund(id, back);
				Shopkeeper.LOGGER.error("Shop: {} of {} did not fit despite capacity check; refunded {} coins (player {})",
					left, it.id(), back, id);
			}
		} catch (RuntimeException ex) {
			refundQuietly(eco, r.charged(), it.id());
			svc.save();
			throw ex;
		}
		svc.save();
		ok();
		refresh();
	}

	/** Re-generates today's offers at click time; the menu contents are never trusted. */
	private void buyOffer(ShopService svc, int offerSlot) {
		Economy eco = svc.economy();
		ShopConfig cfg = svc.config();
		long day = eco.day(); // read once per click
		if (day != renderedDay) {
			refresh();
			fail("Stock changed - check the new offers.");
			return;
		}
		BlackMarket.Offer offer = null;
		for (BlackMarket.Offer o : offers(svc, day)) {
			if (o.slot() == offerSlot) offer = o;
		}
		if (offer == null) return;
		String locked = lockedTierName(cfg, eco, offer);
		if (locked != null) {
			fail("Unlock the " + locked + " tier first.");
			return;
		}
		if (eco.isBlackMarketBought(id, day, offer.slot())) {
			fail("Already sold out.");
			return;
		}
		ItemStack give = BlackMarketStock.make(player.level().registryAccess(), offer);
		if (give.isEmpty()) {
			fail("That item is unavailable.");
			return;
		}
		int max = give.getMaxStackSize();
		int fits = 0;
		for (ItemStack in : player.getInventory().getNonEquipmentItems()) {
			if (in.isEmpty()) fits += max;
			else if (ItemStack.isSameItemSameComponents(in, give)) fits += Math.max(0, max - in.getCount());
		}
		if (fits < 1) {
			fail("Your inventory is full.");
			return;
		}
		if (eco.balance(id) < offer.price()) {
			fail("Not enough coins.");
			return;
		}
		if (!eco.markBlackMarketBought(id, day, offer.slot())) return;
		Economy.Result r = eco.spend(id, offer.price());
		if (!r.ok()) {
			// unreachable after the balance check; the mark stays so nothing can be duplicated
			Shopkeeper.LOGGER.error("Shop: black market spend failed after check: {}", r.error());
			svc.save();
			return;
		}
		try {
			ItemStack out = give.copy();
			player.getInventory().add(out);
			if (!out.isEmpty()) {
				eco.refund(id, r.charged());
				Shopkeeper.LOGGER.error("Shop: black market item did not fit despite capacity check; refunded {} coins (player {})", r.charged(), id);
			}
		} catch (RuntimeException ex) {
			refundQuietly(eco, r.charged(), offer.itemId());
			svc.save();
			throw ex;
		}
		svc.save();
		ok();
		refresh();
	}

	private void refundQuietly(Economy eco, long amount, String itemId) {
		try {
			eco.refund(id, amount);
			Shopkeeper.LOGGER.error("Shop: delivery of {} failed; refunded {} coins (player {})", itemId, amount, id);
		} catch (RuntimeException refundEx) {
			Shopkeeper.LOGGER.error("Shop: delivery of {} failed AND refund of {} coins failed (player {})", itemId, amount, id, refundEx);
		}
	}

	private void sell(ShopService svc, int playerSlot, boolean whole) {
		if (playerSlot < 0 || playerSlot >= 36) return;
		// menu order: 0-26 main (inventory 9-35), 27-35 hotbar (inventory 0-8)
		int invSlot = playerSlot < 27 ? playerSlot + 9 : playerSlot - 27;
		Inventory inv = player.getInventory();
		ItemStack real = inv.getItem(invSlot);
		if (real.isEmpty()) return;
		if (!real.getComponentsPatch().isEmpty()
			|| !ItemStack.isSameItemSameComponents(real, real.getItem().getDefaultInstance())) {
			fail("Only unmodified items can be sold.");
			return;
		}
		ShopConfig.Item it = svc.config().item(idOf(real.getItem()));
		if (it == null || !it.sellable()) {
			fail("The shop does not buy that.");
			return;
		}
		int qty = whole ? real.getCount() : 1;
		Economy.Result r = svc.economy().sell(id, it.id(), qty);
		if (!r.ok()) {
			fail("tier locked".equals(r.error()) ? "That tier is locked." : "Cannot sell that.");
			return;
		}
		real.shrink(r.removed());
		svc.save();
		ok();
		refresh();
	}

	private void ok() {
		play(SoundEvents.EXPERIENCE_ORB_PICKUP);
	}

	private void fail(String msg) {
		play(SoundEvents.NOTE_BLOCK_BASS.value());
		player.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED), true);
	}

	private void play(SoundEvent sound) {
		player.playSound(sound, 0.8f, 1.0f);
	}
}
