package dev.brian.shopkeeper.client;

import dev.brian.shopkeeper.Shopkeeper;
import com.mojang.blaze3d.platform.InputConstants;
import com.pocketcanvascore.render.PocketTheme;
import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.gui.ModsScreen;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import dev.brian.shopkeeper.ShopService;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
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
 * <p>Mixin check: both mixin configs use {@code "defaultRequire": 1}, so a mixin that fails to
 * apply crashes the boot, which fails this test.
 */
public class ShopkeeperClientGameTest implements FabricClientGameTest {
	private static void clickGui(ClientGameTestContext context, Function<ExamplePocketScreen, int[]> target) {
		double[] pos = context.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof ExamplePocketScreen screen)) {
				return null;
			}
			double scale = mc.getWindow().getGuiScale();
			int[] c = target.apply(screen);
			return new double[] { c[0] * scale, c[1] * scale };
		});
		if (pos == null) {
			throw new AssertionError("ExamplePocketScreen is not the current screen");
		}
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
	}

	private static void expectText(ClientGameTestContext context, String expected, String step) {
		String actual = context.computeOnClient(
			mc -> mc.gui.screen() instanceof ExamplePocketScreen screen ? screen.inputText() : null);
		if (!expected.equals(actual)) {
			throw new AssertionError(step + ": expected text '" + expected + "' but was '" + actual + "'");
		}
	}

	private static void assertColorPresent(Path png, int argb, String what) {
		assertColorPresent(png, argb, what, 50);
	}

	private static void assertColorPresent(Path png, int argb, String what, long minHits) {
		try {
			BufferedImage img = ImageIO.read(png.toFile());
			int rgb = argb & 0xFFFFFF;
			long hits = 0;
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) hits++;
				}
			}
			if (hits < minHits) {
				throw new AssertionError(what + " not found in " + png + " (matching pixels: " + hits + ")");
			}
		} catch (IOException e) {
			throw new AssertionError("could not read screenshot " + png, e);
		}
	}

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

			// PocketCanvasCore: render a screen and confirm its theme colors really reached the frame.
			context.setScreen(ExampleCanvasScreen::new);
			context.waitTicks(10);
			Path shot = context.takeScreenshot("pocketcanvas");
			assertColorPresent(shot, PocketTheme.ACCENT_PRIMARY, "ACCENT_PRIMARY bar");
			assertColorPresent(shot, PocketTheme.ACCENT_SECONDARY, "ACCENT_SECONDARY circle");

			// PocketUICore: open a PocketScreen, click its button with a real simulated mouse
			// (left = InputConstants.MOUSE_BUTTON_LEFT, 1-based on 26.3), then close it with Escape.
			context.setScreen(ExamplePocketScreen::new);
			context.waitTicks(10);
			Path uiShot = context.takeScreenshot("pocketui_open");
			assertColorPresent(uiShot, PocketTheme.BACKGROUND_ELEVATED, "HoverButton fill", 500);
			clickGui(context, ExamplePocketScreen::counterCenter);
			int clicks = context.computeOnClient(mc -> ExamplePocketScreen.clicks);
			if (clicks != 1) {
				throw new AssertionError("expected 1 click on the PocketUICore button, got " + clicks);
			}
			context.takeScreenshot("pocketui_clicked");

			// Keyboard: focus the text input by clicking it, then type and edit with real key input.
			clickGui(context, ExamplePocketScreen::inputCenter);
			context.getInput().typeChars("hello");
			context.waitTicks(2);
			expectText(context, "hello", "typing");
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.waitTicks(2);
			expectText(context, "hell", "Backspace");
			context.getInput().pressKey(InputConstants.KEY_LEFT);
			context.getInput().typeChars("X");
			context.waitTicks(2);
			expectText(context, "helXl", "Left arrow then typing");
			context.getInput().pressKey(InputConstants.KEY_HOME);
			context.getInput().typeChars("<");
			context.waitTicks(2);
			expectText(context, "<helXl", "Home then typing");
			// Fabric's test input does not propagate modifier bits on 26.3 (holdControl() still delivers
			// modifiers=0 to the screen), so dispatch a Ctrl+A KeyEvent directly: SDL scancode KEY_A with
			// the SDL MOD_CONTROL mask, which is what the real keyboard path produces.
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof ExamplePocketScreen screen) {
					screen.keyPressed(new KeyEvent(InputConstants.KEY_A, 'a', InputConstants.MOD_CONTROL));
				}
			});
			context.getInput().typeChars("z");
			context.waitTicks(2);
			expectText(context, "z", "Ctrl+A then typing replaces the selection");
			context.takeScreenshot("pocketui_typed");
			// First Escape: the focused text input consumes it (unfocus); the screen stays open.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			boolean stillOpen = context.computeOnClient(mc -> mc.gui.screen() instanceof ExamplePocketScreen);
			if (!stillOpen) {
				throw new AssertionError("first Escape should only unfocus the text input, not close the screen");
			}
			// Second Escape: nothing consumes it, so the screen closes.
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			boolean closed = context.computeOnClient(mc -> mc.gui.screen() == null);
			if (!closed) {
				throw new AssertionError("second Escape did not close the PocketScreen");
			}

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

		private void assertUnchanged(String step, long bal, Map<String, Integer> inv) {
			context.waitTicks(6);
			expect(balance() == bal, step + ": balance changed from " + bal + " to " + balance());
			expect(inventory().equals(inv), step + ": inventory changed from " + inv + " to " + inventory());
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

			// 7. Close with the Close button.
			click(8);
			context.waitTicks(10);
			boolean closed = context.computeOnClient(mc -> mc.gui.screen() == null);
			expect(closed, "Close button did not close the shop");
			context.takeScreenshot("shop_closed");
			expect(balance() == bal, "closing changed the balance");
			finalBalance = bal;
			pass("7 close button closes the menu (final balance " + bal + ")");
		}
	}
}
