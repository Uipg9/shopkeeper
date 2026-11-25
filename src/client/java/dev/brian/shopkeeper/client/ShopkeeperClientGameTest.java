package dev.brian.shopkeeper.client;

import dev.brian.shopkeeper.Shopkeeper;
import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.gui.ModsScreen;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import dev.brian.shopkeeper.ShopService;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * In-game smoke test: boots the client, joins a world, drives input, takes screenshots.
 * Run with {@code .\gradlew.bat runClientGameTest}; screenshots land in
 * {@code build\run\clientGameTest\screenshots}.
 *
 * In-game smoke test for the shop (the mod has no mixins).
 */
public class ShopkeeperClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded(Shopkeeper.MOD_ID)) {
			throw new AssertionError(Shopkeeper.MOD_ID + " is not loaded");
		}

		context.waitForScreen(TitleScreen.class);
		context.waitTicks(60); // let the menu fade-in finish so the screenshot is clean
		context.takeScreenshot("title_screen");

		TestWorldSave save;
		long expectedBalance;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(100);
			boolean inWorld = context.computeOnClient(mc -> mc.player != null && mc.level != null);
			if (!inWorld) {
				throw new AssertionError("player/level missing after world creation");
			}
			context.takeScreenshot("in_world");

			ShopRun run = new ShopRun(context, world);
			run.execute();
			expectedBalance = run.finalBalance;
			save = world.getWorldSave();

			// Drive real input: open the inventory with the bound key, then close it again.
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(AbstractContainerScreen.class);
			context.takeScreenshot("inventory_open");

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			context.takeScreenshot("inventory_closed");

			// Mod Menu (dev-only): the mod must be registered in its list and the Mods screen must open.
			boolean listed = context.computeOnClient(mc -> ModMenu.MODS.containsKey(Shopkeeper.MOD_ID));
			if (!listed) {
				throw new AssertionError(Shopkeeper.MOD_ID + " is not listed in Mod Menu");
			}
			context.setScreen(() -> new ModsScreen(null));
			context.waitTicks(20);
			context.takeScreenshot("mod_menu");
			context.setScreen(() -> null);
			context.waitTicks(5);
		}

		// Persistence: reopen the same save; balance and the Underground unlock must have survived.
		try (TestSingleplayerContext again = save.open()) {
			context.waitTicks(100);
			long bal = ShopRun.balanceOf(again);
			if (bal != expectedBalance) {
				throw new AssertionError("persistence: balance after reopen " + bal + ", expected " + expectedBalance);
			}
			boolean unlocked = again.getServer().computeOnServer(server -> ShopService.get().economy()
				.isUnlocked(server.getPlayerList().getPlayers().getFirst().getUUID(), "underground"));
			if (!unlocked) {
				throw new AssertionError("persistence: Underground tier not unlocked after reopen");
			}
			System.out.println("[shopkeeper-test] PASS persistence (balance " + bal + ", underground unlocked)");
			context.takeScreenshot("after_reopen");
		}
	}

	/** The end-to-end shop scenario. Everything the player does goes through the real client; checks read server state. */
	private static final class ShopRun {
		private final ClientGameTestContext context;
		private final TestSingleplayerContext world;
		long finalBalance;

		ShopRun(ClientGameTestContext context, TestSingleplayerContext world) {
			this.context = context;
			this.world = world;
		}

		private static void pass(String what) {
			System.out.println("[shopkeeper-test] PASS " + what);
		}

		private static void expect(boolean ok, String msg) {
			if (!ok) throw new AssertionError(msg);
		}

		static long balanceOf(TestSingleplayerContext w) {
			return w.getServer().computeOnServer(server -> {
				UUID id = server.getPlayerList().getPlayers().getFirst().getUUID();
				ShopService.get().economy().ensurePlayer(id);
				return ShopService.get().economy().balance(id);
			});
		}

		private long balance() {
			return balanceOf(world);
		}

		private boolean unlocked(String tier) {
			return world.getServer().computeOnServer(server -> ShopService.get().economy()
				.isUnlocked(server.getPlayerList().getPlayers().getFirst().getUUID(), tier));
		}

		/** item id -> total count over the whole server-side inventory plus the carried stack. */
		private Map<String, Integer> inventory() {
			return world.getServer().computeOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				Map<String, Integer> out = new HashMap<>();
				Inventory inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) add(out, inv.getItem(i));
				add(out, p.containerMenu.getCarried());
				return out;
			});
		}

		private static void add(Map<String, Integer> out, ItemStack st) {
			if (!st.isEmpty()) out.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), st.getCount(), Integer::sum);
		}

		private int count(String id) {
			return inventory().getOrDefault(id, 0);
		}

		private void give(String stack) {
			world.getServer().runCommand("give @p " + stack);
			context.waitTicks(6);
		}

		private void requireShopScreen(String step) {
			boolean ok = context.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen cs
				&& cs.getMenu() instanceof ChestMenu m && m.getRowCount() == 6);
			expect(ok, step + ": the 9x6 shop ChestMenu screen is not open");
		}

		/** Menu index of the first player-inventory slot holding a matching stack. */
		private int playerSlot(Predicate<ItemStack> match, String what) {
			Integer idx = context.computeOnClient(mc -> {
				AbstractContainerMenu m = mc.player.containerMenu;
				for (int i = 54; i < m.slots.size(); i++) {
					Slot sl = m.slots.get(i);
					if (sl.container instanceof Inventory && match.test(sl.getItem())) return i;
				}
				return -1;
			});
			expect(idx >= 54, "no player-inventory slot shows " + what + " in the open menu");
			return idx;
		}

		private static boolean isItem(ItemStack st, String id) {
			return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(id);
		}

		private void hover(int menuIndex) {
			double[] pos = context.computeOnClient(mc -> {
				try {
					ContainerScreen cs = (ContainerScreen) mc.gui.screen();
					Field l = AbstractContainerScreen.class.getDeclaredField("leftPos");
					Field t = AbstractContainerScreen.class.getDeclaredField("topPos");
					l.setAccessible(true);
					t.setAccessible(true);
					Slot sl = cs.getMenu().slots.get(menuIndex);
					double scale = mc.getWindow().getGuiScale();
					return new double[] { (l.getInt(cs) + sl.x + 8) * scale, (t.getInt(cs) + sl.y + 8) * scale };
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			});
			context.getInput().setCursorPos(pos[0], pos[1]);
			context.waitTicks(3);
		}

		/** Real mouse left click on a menu slot. */
		private void click(int menuIndex) {
			hover(menuIndex);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(6);
		}

		/** Shift-click: Fabric's test input drops modifier bits on 26.3, so send the exact packet a real shift-click makes. */
		private void shiftClick(int menuIndex) {
			raw(menuIndex, 0, ContainerInput.QUICK_MOVE);
			context.waitTicks(6);
		}

		private void raw(int slot, int button, ContainerInput type) {
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(
				mc.player.containerMenu.containerId, slot, button, type, mc.player));
		}

		private void openShopViaChat() {
			context.getInput().pressKey(options -> options.keyCommand);
			context.waitTicks(5);
			context.getInput().typeChars("shop");
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(10);
		}

		private void chatCommand(String cmd) {
			context.getInput().pressKey(options -> options.keyCommand);
			context.waitTicks(5);
			context.getInput().typeChars(cmd);
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(10);
		}

		private static long sellPriceSum(long fallback) {
			ShopService svc = ShopService.get();
			return svc.config().item("minecraft:stone").sell() * 10 + svc.config().item("minecraft:baked_potato").sell() * 5;
		}

		private void assertUnchanged(String step, long bal, Map<String, Integer> inv) {
			context.waitTicks(6);
			expect(balance() == bal, step + ": balance changed from " + bal + " to " + balance());
			expect(inventory().equals(inv), step + ": inventory changed from " + inv + " to " + inventory());
		}

		/** Everything the test needs to know about one deal item, read on the server thread. */
		private record DealItem(String id, String tier, int tierIndex, long buy, long sell, long buyNow, long sellNow, boolean sellable) {}

		private DealItem dealItem(String id) {
			return world.getServer().computeOnServer(server -> {
				ShopService svc = ShopService.get();
				var it = svc.config().item(id);
				int ti = -1;
				for (int i = 0; i < svc.config().tiers().size(); i++) {
					if (svc.config().tiers().get(i).id().equals(it.tier())) ti = i;
				}
				var eco = svc.economy();
				return new DealItem(id, it.tier(), ti, it.buy(), it.sell(), eco.buyPrice(it), eco.sellPrice(it), it.sellable());
			});
		}

		/** Menu index (shop area, 9..53) of the slot showing this item, or -1. */
		private int shopSlot(String id) {
			return context.computeOnClient(mc -> {
				AbstractContainerMenu m = mc.player.containerMenu;
				for (int i = 9; i < 54; i++) if (isItem(m.slots.get(i).getItem(), id)) return i;
				return -1;
			});
		}

		private String lore(int menuIndex) {
			return context.computeOnClient(mc -> {
				var l = mc.player.containerMenu.slots.get(menuIndex).getItem().get(DataComponents.LORE);
				StringBuilder sb = new StringBuilder();
				if (l != null) l.lines().forEach(c -> sb.append(c.getString()).append('\n'));
				return sb.toString();
			});
		}

		/**
		 * Daily deals end to end. The picked deal (from the real day + world seed) is NOT overwritten: the GUI re-picks
		 * on every open/click, so forcing a deal would be undone. Economy-level checks always run; the UI checks run
		 * for each deal item whose tier is unlocked for the player (Basics always, Underground by now). Locked-tier
		 * deal items are only checked at economy level (clicking a locked tab would try to buy the unlock).
		 */
		private long dailyDeals(long bal) {
			String[] ids = world.getServer().computeOnServer(server -> {
				ShopService.get().refreshDeal();
				var d = ShopService.get().economy().deal();
				expect(d != null && d.hotId() != null && d.cheapId() != null, "daily deal missing an item: " + d);
				expect(!d.hotId().equals(d.cheapId()), "hot and cheap are the same item: " + d.hotId());
				return new String[] { d.hotId(), d.cheapId() };
			});
			DealItem hot = dealItem(ids[0]);
			DealItem cheap = dealItem(ids[1]);
			expect(hot.sellable() && hot.sellNow() > hot.sell() && hot.sellNow() < hot.buy(),
				"hot price " + hot.sellNow() + " not in (" + hot.sell() + ", " + hot.buy() + ") for " + hot.id());
			expect(cheap.buyNow() < cheap.buy() && cheap.buyNow() >= cheap.sell() + 1,
				"cheap price " + cheap.buyNow() + " not in [" + (cheap.sell() + 1) + ", " + cheap.buy() + ") for " + cheap.id());
			pass("9a deal today: hot " + hot.id() + " sell " + hot.sell() + "->" + hot.sellNow() + ", cheap " + cheap.id() + " buy " + cheap.buy() + "->" + cheap.buyNow());

			// Funds so the buy can't fail on balance.
			world.getServer().computeOnServer(server -> {
				ShopService.get().economy().refund(server.getPlayerList().getPlayers().getFirst().getUUID(), 10000);
				return null;
			});
			bal += 10000;
			expect(balance() == bal, "refund bookkeeping: " + balance() + " vs " + bal);

			boolean hotUi = unlocked(hot.tier()) && hot.tierIndex() >= 0;
			boolean cheapUi = unlocked(cheap.tier()) && cheap.tierIndex() >= 0;
			if (cheapUi) {
				click(cheap.tierIndex());
				int slot = shopSlot(cheap.id());
				expect(slot >= 9, "cheap item " + cheap.id() + " not shown in its unlocked tab");
				String lore = lore(slot);
				expect(lore.contains("SALE") && lore.contains("buy for " + cheap.buyNow()) && lore.contains("Buy: " + cheap.buyNow()),
					"cheap lore wrong: " + lore);
				context.takeScreenshot("shop_daily_sale");
				int before = count(cheap.id());
				click(slot);
				expect(count(cheap.id()) == before + 1, "cheap buy delivered " + (count(cheap.id()) - before));
				expect(balance() == bal - cheap.buyNow(), "cheap buy charged " + (bal - balance()) + ", expected " + cheap.buyNow());
				bal -= cheap.buyNow();
				pass("9b UI sale: SALE lore shows " + cheap.buyNow() + " and the click charged exactly that");
			}
			if (hotUi) {
				click(hot.tierIndex());
				int slot = shopSlot(hot.id());
				expect(slot >= 9, "hot item " + hot.id() + " not shown in its unlocked tab");
				String lore = lore(slot);
				expect(lore.contains("HOT") && lore.contains("sells for " + hot.sellNow()) && lore.contains("Sell: " + hot.sellNow()),
					"hot lore wrong: " + lore);
				context.takeScreenshot("shop_daily_hot");
				give(hot.id());
				int before = count(hot.id());
				click(playerSlot(st -> isItem(st, hot.id()) && !st.has(DataComponents.CUSTOM_NAME), "unnamed " + hot.id()));
				expect(count(hot.id()) == before - 1, "hot sell removed " + (before - count(hot.id())));
				expect(balance() == bal + hot.sellNow(), "hot sell credited " + (balance() - bal) + ", expected " + hot.sellNow());
				bal += hot.sellNow();
				pass("9c UI hot: HOT lore shows " + hot.sellNow() + " and the click credited exactly that");
			}
			if (!cheapUi || !hotUi) System.out.println("[shopkeeper-test] NOTE deal UI partly skipped (locked tier): hotUi=" + hotUi + " cheapUi=" + cheapUi);
			click(0); // back to Basics
			expect(balance() == bal, "deal scenario bookkeeping: " + balance() + " vs " + bal);
			return bal;
		}

		/** Today's black market offers, computed like ShopGui does (overworld clock day + world seed). */
		private java.util.List<dev.brian.shopkeeper.core.BlackMarket.Offer> todaysOffers() {
			return world.getServer().computeOnServer(server -> {
				ShopService.get().refreshDeal();
				var level = server.overworld();
				return dev.brian.shopkeeper.core.BlackMarket.generate(level.getOverworldClockTime() / 24000L, level.getSeed(),
					ShopService.get().config());
			});
		}

		private static Map<String, Integer> enchantsOf(ItemStack st) {
			Map<String, Integer> out = new HashMap<>();
			var ench = st.get(DataComponents.ENCHANTMENTS);
			if (ench == null) return out;
			for (var e : ench.entrySet()) {
				out.put(e.getKey().unwrapKey().map(k -> k.identifier().toString()).orElse("?"), e.getIntValue());
			}
			return out;
		}

		private static Map<String, Integer> enchantsOf(java.util.List<dev.brian.shopkeeper.core.BlackMarket.Ench> list) {
			Map<String, Integer> out = new HashMap<>();
			for (var e : list) out.put(e.enchantId(), e.level());
			return out;
		}

		/** Server-side count of stacks of this item with exactly this damage and enchants. */
		private int usedGearCount(String id, int damage, Map<String, Integer> enchants) {
			return world.getServer().computeOnServer(server -> {
				Inventory inv = server.getPlayerList().getPlayers().getFirst().getInventory();
				int n = 0;
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack st = inv.getItem(i);
					if (isItem(st, id) && st.getDamageValue() == damage && enchantsOf(st).equals(enchants)) n += st.getCount();
				}
				return n;
			});
		}

		private ItemStack clientSlot(int menuIndex) {
			return context.computeOnClient(mc -> mc.player.containerMenu.slots.get(menuIndex).getItem().copy());
		}

		/** Text of every chat message the client has received, newest first (reads ChatComponent.allMessages reflectively). */
		private java.util.List<String> chatLines() {
			return context.computeOnClient(mc -> {
				try {
					Field f = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
					f.setAccessible(true);
					java.util.List<?> all = (java.util.List<?>) f.get(mc.gui.hud.getChat());
					java.util.List<String> out = new java.util.ArrayList<>();
					for (Object o : all) out.add(((net.minecraft.client.multiplayer.chat.GuiMessage) o).content().getString());
					return out;
				} catch (ReflectiveOperationException e) {
					throw new AssertionError(e);
				}
			});
		}

		private void moveCursorAway() {
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(3);
		}

		private long blackMarket(long bal) {
			var offers = todaysOffers();
			expect(!offers.isEmpty(), "black market generated no offers");
			var o = offers.get(0);
			world.getServer().computeOnServer(server -> {
				ShopService.get().economy().refund(server.getPlayerList().getPlayers().getFirst().getUUID(), 20000);
				return null;
			});
			bal += 20000;
			expect(balance() == bal, "black market refund bookkeeping: " + balance() + " vs " + bal);

			click(6);
			int slot = 9 + o.slot();
			ItemStack shown = clientSlot(slot);
			expect(isItem(shown, o.itemId()), "black market slot " + slot + " shows " + shown + ", expected " + o.itemId());
			int damage = shown.getDamageValue();
			expect(damage > 0, "black market offer is not damaged: " + damage);
			expect(enchantsOf(shown).equals(enchantsOf(o.enchants())), "client enchants " + enchantsOf(shown) + " vs " + o.enchants());
			if (!o.enchants().isEmpty()) {
				var ench = shown.get(DataComponents.ENCHANTMENTS);
				expect(ench != null && !ench.isEmpty(), "enchanted offer shows no ENCHANTMENTS component");
			}
			String lore = lore(slot);
			expect(lore.contains("Price: " + o.price() + " coins"), "black market lore wrong: " + lore);
			moveCursorAway();
			context.takeScreenshot("shop_black_market");
			hover(slot);
			context.waitTicks(10);
			context.takeScreenshot("shop_black_market_tooltip");
			pass("A1 black market tab shows " + o.itemId() + " dmg " + damage + " ench " + o.enchants() + " price " + o.price());

			// Buy it.
			Map<String, Integer> want = enchantsOf(o.enchants());
			int before = usedGearCount(o.itemId(), damage, want);
			click(slot);
			expect(balance() == bal - o.price(), "black market buy charged " + (bal - balance()) + ", expected " + o.price());
			bal -= o.price();
			int got = usedGearCount(o.itemId(), damage, want);
			expect(got == before + 1, "black market buy delivered " + (got - before) + " matching stacks");
			ItemStack after = clientSlot(slot);
			expect(isItem(after, "minecraft:gray_stained_glass_pane") && after.getHoverName().getString().contains("Sold out"),
				"sold slot shows " + after + " / " + after.getHoverName().getString());
			moveCursorAway();
			context.takeScreenshot("shop_black_market_sold");
			pass("A2 black market buy: charged exactly " + o.price() + ", used gear delivered, slot shows Sold out");

			// Second click on the sold slot: nothing.
			Map<String, Integer> inv = inventory();
			click(slot);
			assertUnchanged("click a sold out black market slot", bal, inv);

			// Selling the bought (modified) gear back must be refused.
			click(playerSlot(st -> isItem(st, o.itemId()) && st.getDamageValue() == damage, "bought gear"));
			assertUnchanged("sell back black market gear", bal, inv);
			expect(usedGearCount(o.itemId(), damage, want) == before + 1, "bought gear vanished after sell-back click");

			// Shift-click an unbought offer: nothing.
			if (offers.size() > 1) {
				shiftClick(9 + offers.get(1).slot());
				assertUnchanged("shift-click a black market offer", bal, inv);
			} else {
				System.out.println("[shopkeeper-test] NOTE only one black market offer; shift-click probe skipped");
			}
			pass("A3 sold out click, sell-back and shift-click on offers all did nothing");

			// Tier tabs still work.
			click(0);
			expect(isItem(clientSlot(9), "minecraft:oak_log"), "Basics tab did not show oak_log after the black market: " + clientSlot(9));
			expect(balance() == bal, "black market scenario bookkeeping: " + balance() + " vs " + bal);
			pass("A4 tier tab 0 works again after the black market (balance " + bal + ")");
			return bal;
		}

		/** Daily report: /shop report shows today's totals; after a day rolls over the report arrives by itself. */
		private void dailyReport() {
			UUID[] idBox = new UUID[1];
			world.getServer().computeOnServer(server -> {
				ShopService.get().refreshDeal();
				idBox[0] = server.getPlayerList().getPlayers().getFirst().getUUID();
				return null;
			});
			var stats = world.getServer().computeOnServer(server -> ShopService.get().economy().today(idBox[0]));
			expect(stats.earned() > 0, "no earnings recorded today before the report test: " + stats);

			int seen = chatLines().size();
			chatCommand("shop report");
			var now = chatLines();
			String mine = null;
			for (int i = 0; i < now.size() - seen; i++) if (now.get(i).startsWith("[Shop] Shop report, day " + stats.day())) mine = now.get(i);
			expect(mine != null, "/shop report produced no report line; new chat: " + now.subList(0, Math.max(0, now.size() - seen)));
			expect(mine.contains("earned " + stats.earned()) && mine.contains("spent " + stats.spent()),
				"report line does not match stats " + stats + ": " + mine);
			context.takeScreenshot("shop_report_command");
			pass("B1 /shop report: " + mine);

			// Next day: ShopService.tick runs every 100 ticks and delivers yesterday's report once.
			int seen2 = chatLines().size();
			world.getServer().runCommand("time add 24000");
			context.waitTicks(130);
			var after = chatLines();
			String auto = null;
			for (int i = 0; i < after.size() - seen2; i++) {
				String line = after.get(i);
				if (line.startsWith("[Shop] Shop report, day " + stats.day()) && line.contains("earned") && line.contains("spent")) auto = line;
			}
			expect(auto != null, "no automatic report after the day rolled; new chat: " + after.subList(0, Math.max(0, after.size() - seen2)));
			context.takeScreenshot("shop_report_auto");
			boolean consumed = world.getServer().computeOnServer(server -> {
				var eco = ShopService.get().economy();
				var t = eco.today(idBox[0]);
				return eco.takePendingReport(idBox[0]).isEmpty() && t.earned() == 0 && t.spent() == 0 && t.day() > stats.day();
			});
			expect(consumed, "after the rollover today's stats did not restart at zero or the report was not consumed");
			pass("B2 next-day report delivered once: " + auto);
		}

		void execute() {
			// 6. Registry check first so a bad id is reported even if clicks later fail.
			java.util.List<String> unknown = world.getServer().computeOnServer(server -> ShopService.get().unknownItemIds());
			expect(unknown.isEmpty(), "default config has unknown item ids: " + unknown);
			pass("6 registry: all default config item ids resolve");

			// 1. /shop via the real chat box.
			long bal = balance();
			expect(bal == 100, "starting balance should be 100, was " + bal);
			openShopViaChat();
			context.waitForScreen(ContainerScreen.class);
			context.waitTicks(10);
			requireShopScreen("/shop");
			context.takeScreenshot("shop_open");
			pass("1 /shop opened a 9x6 ChestMenu; starting balance 100");

			// 2. SELL: 10 potatoes, 3 each. Left click sells one, shift-click sells the stack.
			give("minecraft:potato 10");
			expect(count("minecraft:potato") == 10, "give potato failed");
			click(playerSlot(st -> isItem(st, "minecraft:potato"), "potato"));
			expect(balance() == bal + 3, "sell one potato: balance " + balance() + ", expected " + (bal + 3));
			expect(count("minecraft:potato") == 9, "sell one potato: count " + count("minecraft:potato"));
			shiftClick(playerSlot(st -> isItem(st, "minecraft:potato"), "potato"));
			expect(balance() == bal + 30, "sell stack of potatoes: balance " + balance() + ", expected " + (bal + 30));
			expect(count("minecraft:potato") == 0, "sell stack: potatoes left " + count("minecraft:potato"));
			bal += 30;
			context.takeScreenshot("shop_after_sell");
			pass("2 sell: +3 for one potato, +27 for the stack (balance " + bal + ")");

			// 3. BUY. Basics tab: oak_log = item 0 (slot 9), stick = 9 (slot 18), dirt = 12 (slot 21).
			shiftClick(18); // 64 sticks * 2 = 128 of 130
			expect(count("minecraft:stick") == 64, "shift-buy sticks: got " + count("minecraft:stick"));
			expect(balance() == bal - 128, "shift-buy sticks: balance " + balance());
			bal -= 128;
			Map<String, Integer> inv = inventory();
			click(9); // oak_log costs 8, balance is 2
			assertUnchanged("buy with insufficient funds", bal, inv);
			shiftClick(21); // 64 dirt = 128, balance 2
			assertUnchanged("shift-buy with insufficient funds", bal, inv);
			click(21); // 1 dirt = 2
			expect(count("minecraft:dirt") == 1, "buy one dirt: got " + count("minecraft:dirt"));
			expect(balance() == bal - 2, "buy one dirt: balance " + balance());
			bal -= 2;
			context.takeScreenshot("shop_after_buy");
			pass("3 buy: left=1 item, shift=stack; insufficient funds delivers/charges nothing (balance " + bal + ")");

			// 4. Locked tier. Underground (tab slot 1) costs 500; balance is 0.
			expect(!unlocked("underground"), "underground should start locked");
			click(1);
			expect(!unlocked("underground"), "unlock with 0 coins must fail");
			expect(balance() == bal, "failed unlock charged coins: " + balance());
			context.takeScreenshot("shop_locked_tier");
			give("minecraft:baked_potato 64");
			give("minecraft:baked_potato 64");
			expect(count("minecraft:baked_potato") == 128, "give baked_potato failed");
			shiftClick(playerSlot(st -> isItem(st, "minecraft:baked_potato"), "baked potato"));
			shiftClick(playerSlot(st -> isItem(st, "minecraft:baked_potato"), "baked potato"));
			expect(count("minecraft:baked_potato") == 0, "baked potatoes left: " + count("minecraft:baked_potato"));
			bal += 128 * 7;
			expect(balance() == bal, "after selling 128 baked potatoes: " + balance() + ", expected " + bal);
			click(2); // Deep costs 2500: still not enough
			expect(!unlocked("deep"), "deep must stay locked at balance " + bal);
			expect(balance() == bal, "failed deep unlock charged coins");
			click(1);
			expect(unlocked("underground"), "underground should now be unlocked");
			expect(balance() == bal - 500, "unlock price not charged: " + balance());
			bal -= 500;
			context.takeScreenshot("shop_underground");
			click(9); // coal, 12
			expect(count("minecraft:coal") == 1, "buy from unlocked tier: coal " + count("minecraft:coal"));
			bal -= 12;
			expect(balance() == bal, "coal price: " + balance());
			pass("4 unlock: fails at 0 and below price, succeeds with enough (-500), item buyable (balance " + bal + ")");

			// 5. Exploit probes on the Basics tab. Nothing may be duplicated, lost, or credited.
			click(0); // back to Basics
			give("minecraft:stone 10");
			give("minecraft:diamond_sword");
			give("minecraft:potato[minecraft:custom_name=\"Fancy\"] 3");
			inv = inventory();
			expect(inv.getOrDefault("minecraft:potato", 0) == 3, "named potato give failed: " + inv);
			int stone = playerSlot(st -> isItem(st, "minecraft:stone"), "stone");
			int sword = playerSlot(st -> isItem(st, "minecraft:diamond_sword"), "diamond sword");
			int named = playerSlot(st -> isItem(st, "minecraft:potato"), "named potato");

			shiftClick(sword);
			assertUnchanged("shift-click an unsellable item", bal, inv);
			shiftClick(named);
			click(named);
			assertUnchanged("sell an item with custom components", bal, inv);

			hover(9);
			context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
			assertUnchanged("number-key swap over a shop slot", bal, inv);
			hover(stone);
			context.getInput().pressKey(options -> options.keyHotbarSlots[3]);
			assertUnchanged("number-key swap over a player slot", bal, inv);
			hover(stone);
			context.getInput().pressKey(options -> options.keyDrop);
			assertUnchanged("drop key over a player slot", bal, inv);
			raw(stone, 0, ContainerInput.SWAP);
			raw(9, 2, ContainerInput.SWAP);
			raw(stone, 1, ContainerInput.THROW);
			raw(9, 0, ContainerInput.CLONE);
			assertUnchanged("raw swap/throw/clone packets", bal, inv);

			// Drag across slots, via real mouse and via raw quick-craft packets. The press lands on the
			// unsellable sword (a press on a sellable slot is a legitimate click and would sell one).
			hover(sword);
			context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.getInput().moveCursor(20, 0);
			context.waitTicks(2);
			context.getInput().moveCursor(20, 0);
			context.waitTicks(2);
			context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			assertUnchanged("real mouse drag", bal, inv);
			raw(-999, 0, ContainerInput.QUICK_CRAFT);
			raw(10, 1, ContainerInput.QUICK_CRAFT);
			raw(11, 1, ContainerInput.QUICK_CRAFT);
			raw(stone, 1, ContainerInput.QUICK_CRAFT);
			raw(-999, 2, ContainerInput.QUICK_CRAFT);
			assertUnchanged("raw quick-craft drag", bal, inv);

			// Double click: PICKUP_ALL on player and shop slots must do nothing.
			raw(stone, 0, ContainerInput.PICKUP_ALL);
			raw(10, 0, ContainerInput.PICKUP_ALL);
			assertUnchanged("pickup-all (double click)", bal, inv);
			// Two rapid real clicks (one tick apart): each is a legitimate sell of 1 stone (5 coins). The
			// invariant is that coins credited match stones removed exactly, never more.
			hover(stone);
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTick();
			context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			context.waitTicks(8);
			boolean carriedEmpty = context.computeOnClient(mc -> mc.player.containerMenu.getCarried().isEmpty());
			expect(carriedEmpty, "client carries a stack after cancelled clicks");
			int soldStone = 10 - count("minecraft:stone");
			expect(soldStone >= 1 && soldStone <= 2, "rapid clicks removed " + soldStone + " stone");
			expect(balance() == bal + 5L * soldStone, "rapid clicks credited " + (balance() - bal) + " for " + soldStone + " stone");
			bal += 5L * soldStone;
			context.takeScreenshot("shop_after_probes");
			pass("5 probes: swap, number key, drop, clone, drag, double-click, shift on unsellable/named: no dup, no loss, no credit");

			bal = dailyDeals(bal);
			bal = blackMarket(bal);

			// 7. Close with the Close button.
			click(8);
			context.waitTicks(10);
			boolean closed = context.computeOnClient(mc -> mc.gui.screen() == null);
			expect(closed, "Close button did not close the shop");
			context.takeScreenshot("shop_closed");
			expect(balance() == bal, "closing changed the balance");
			finalBalance = bal;
			pass("7 close button closes the menu (final balance " + bal + ")");

			// 8. /shop sell (main hand) and /shop sellall (inventory + hotbar), real chat input.
			world.getServer().runCommand("clear @p");
			world.getServer().computeOnServer(server -> {
				server.getPlayerList().getPlayers().getFirst().getInventory().setSelectedSlot(0);
				return null;
			});
			context.waitTicks(4);
			give("minecraft:potato 20");
			give("minecraft:diamond_sword");
			give("minecraft:diamond_sword 1");
			give("minecraft:potato[minecraft:custom_name=\"Fancy\"] 3");
			give("minecraft:stone 10");
			give("minecraft:baked_potato 5");
			bal = balance();
			chatCommand("shop sell");
			expect(count("minecraft:potato") == 3, "/shop sell: potatoes left " + count("minecraft:potato") + ", expected 3 (named)");
			expect(balance() >= bal + 3L * 20, "/shop sell: credited " + (balance() - bal) + ", expected >= 60");
			bal = balance();
			pass("8a /shop sell sold the 20 potatoes in hand (balance " + bal + ")");

			chatCommand("shop sellall");
			expect(count("minecraft:potato") == 3, "/shop sellall sold the renamed potatoes");
			expect(count("minecraft:diamond_sword") == 2, "/shop sellall touched unsellable swords");
			expect(count("minecraft:stone") == 0 && count("minecraft:baked_potato") == 0,
				"/shop sellall left sellable stacks: " + inventory());
			long base = 5L * 10 + 7L * 5;
			long sums = world.getServer().computeOnServer(server -> sellPriceSum(base));
			expect(balance() >= bal + sums, "/shop sellall credited " + (balance() - bal) + ", expected >= " + sums);
			bal = balance();
			finalBalance = bal;
			pass("8b /shop sellall sold stone + baked potatoes, skipped renamed potatoes and swords (balance " + bal + ")");

			dailyReport();
			expect(balance() == finalBalance, "daily report scenario changed the balance: " + balance() + " vs " + finalBalance);
		}
	}
}
