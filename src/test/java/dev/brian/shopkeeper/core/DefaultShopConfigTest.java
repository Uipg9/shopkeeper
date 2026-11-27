package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DefaultShopConfigTest {
	static final int YIELD_PER_PLANT = 2;
	static final long MIN_COOK_MARGIN = 1;
	static final long MAX_COOK_MARGIN = 6;

	static final Map<String, String> RAW_TO_COOKED = Map.ofEntries(
			Map.entry("potato", "baked_potato"),
			Map.entry("beef", "cooked_beef"),
			Map.entry("porkchop", "cooked_porkchop"),
			Map.entry("chicken", "cooked_chicken"),
			Map.entry("mutton", "cooked_mutton"),
			Map.entry("cod", "cooked_cod"),
			Map.entry("salmon", "cooked_salmon"),
			Map.entry("raw_iron", "iron_ingot"),
			Map.entry("raw_copper", "copper_ingot"),
			Map.entry("raw_gold", "gold_ingot"),
			Map.entry("cobblestone", "stone"),
			Map.entry("sand", "glass"));

	static ShopConfig config;

	@BeforeAll
	static void load() throws IOException {
		try (InputStream in = DefaultShopConfigTest.class.getResourceAsStream("/shopkeeper/default_shop.json")) {
			assertNotNull(in, "default_shop.json missing from classpath");
			String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			ShopConfigParser.Result r = ShopConfigParser.parse(json);
			assertTrue(r.errors().isEmpty(), "parse errors: " + r.errors());
			config = r.config();
		}
	}

	static ShopConfig.Item item(String shortId) {
		ShopConfig.Item i = config.item("minecraft:" + shortId);
		assertNotNull(i, "missing item " + shortId);
		return i;
	}

	@Test
	void headerValues() {
		assertEquals(100, config.startingBalance());
		assertEquals(0.30, config.sellFraction(), 1e-9);
		assertEquals(List.of("basics", "underground", "deep", "rare"), config.tiers().stream().map(ShopConfig.Tier::id).toList());
		assertEquals(List.of(0L, 500L, 2500L, 10000L), config.tiers().stream().map(ShopConfig.Tier::unlockPrice).toList());
	}

	@Test
	void tiersWithinItemCap() {
		for (ShopConfig.Tier t : config.tiers()) {
			int n = config.itemsIn(t.id()).size();
			assertTrue(n > 0 && n <= ShopConfig.MAX_ITEMS_PER_TIER, t.id() + " has " + n);
		}
	}

	@Test
	void coversMostOfTheGameWithUniqueIds() {
		assertTrue(config.items().size() >= 450, "only " + config.items().size() + " items");
		assertEquals(config.items().size(), config.items().stream().map(ShopConfig.Item::id).distinct().count(), "duplicate ids");
	}

	@Test
	void everythingIsSellable() {
		for (ShopConfig.Item i : config.items()) {
			assertTrue(i.buy() >= 2, i.id() + " buy " + i.buy());
			assertTrue(i.sellable() && i.sell() >= 1, i.id() + " is not sellable (buy " + i.buy() + ")");
		}
	}

	/** output, output count, then input id/count pairs. Buying the inputs and selling the output must never profit. */
	static final List<Object[]> RECIPES = List.of(
			// money loops found by the recipe sweep at the old prices; sell is now set below input cost
			new Object[] {"name_tag", 1, "paper", 1, "iron_nugget", 1},
			new Object[] {"stone_sword", 1, "cobblestone", 2, "stick", 1},
			new Object[] {"saddle", 1, "leather", 3, "iron_ingot", 1},
			new Object[] {"end_crystal", 1, "glass", 7, "ender_eye", 1, "ghast_tear", 1},
			new Object[] {"oak_planks", 4, "oak_log", 1},
			new Object[] {"jungle_planks", 4, "jungle_log", 1},
			new Object[] {"stick", 4, "oak_planks", 2},
			new Object[] {"torch", 4, "coal", 1, "stick", 1},
			new Object[] {"chest", 1, "oak_planks", 8},
			new Object[] {"stone_bricks", 4, "stone", 4},
			new Object[] {"glass_pane", 16, "glass", 6},
			new Object[] {"bread", 1, "wheat", 3},
			new Object[] {"hay_block", 1, "wheat", 9},
			new Object[] {"wheat", 9, "hay_block", 1},
			new Object[] {"book", 1, "paper", 3, "leather", 1},
			new Object[] {"bookshelf", 1, "oak_planks", 6, "book", 3},
			new Object[] {"bucket", 1, "iron_ingot", 3},
			new Object[] {"iron_pickaxe", 1, "iron_ingot", 3, "stick", 2},
			new Object[] {"diamond_pickaxe", 1, "diamond", 3, "stick", 2},
			new Object[] {"iron_block", 1, "iron_ingot", 9},
			new Object[] {"iron_ingot", 9, "iron_block", 1},
			new Object[] {"iron_nugget", 9, "iron_ingot", 1},
			new Object[] {"iron_ingot", 1, "iron_nugget", 9},
			new Object[] {"gold_block", 1, "gold_ingot", 9},
			new Object[] {"gold_ingot", 9, "gold_block", 1},
			new Object[] {"gold_ingot", 1, "gold_nugget", 9},
			new Object[] {"copper_block", 1, "copper_ingot", 9},
			new Object[] {"copper_ingot", 9, "copper_block", 1},
			new Object[] {"diamond_block", 1, "diamond", 9},
			new Object[] {"diamond", 9, "diamond_block", 1},
			new Object[] {"emerald", 9, "emerald_block", 1},
			new Object[] {"redstone", 9, "redstone_block", 1},
			new Object[] {"lapis_lazuli", 9, "lapis_block", 1},
			new Object[] {"coal", 9, "coal_block", 1},
			new Object[] {"slime_ball", 9, "slime_block", 1},
			new Object[] {"bone_meal", 9, "bone_block", 1},
			new Object[] {"netherite_ingot", 9, "netherite_block", 1},
			new Object[] {"netherite_ingot", 1, "netherite_scrap", 4, "gold_ingot", 4},
			new Object[] {"golden_apple", 1, "gold_ingot", 8, "apple", 1},
			new Object[] {"ender_eye", 1, "ender_pearl", 1, "blaze_powder", 1},
			new Object[] {"blaze_powder", 2, "blaze_rod", 1},
			new Object[] {"end_rod", 4, "blaze_rod", 1, "popped_chorus_fruit", 1},
			new Object[] {"anvil", 1, "iron_block", 3, "iron_ingot", 4});

	@Test
	void craftingNeverMakesMoney() {
		for (Object[] r : RECIPES) {
			ShopConfig.Item out = item((String) r[0]);
			long cost = 0;
			for (int k = 2; k < r.length; k += 2) cost += item((String) r[k]).buy() * (Integer) r[k + 1];
			long proceeds = out.sell() * (Integer) r[1];
			assertTrue(proceeds <= cost, r[0] + " x" + r[1] + " sells for " + proceeds + " but its inputs cost " + cost);
		}
	}

	@Test
	void buyThenSellNeverProfits() {
		for (ShopConfig.Item i : config.items()) {
			assertTrue(i.id().startsWith("minecraft:"), i.id());
			if (i.sellable()) assertTrue(i.sell() < i.buy(), i.id());
		}
	}

	@Test
	void cookMarginsSmallAndPositive() {
		for (Map.Entry<String, String> e : RAW_TO_COOKED.entrySet()) {
			ShopConfig.Item raw = item(e.getKey());
			ShopConfig.Item cooked = item(e.getValue());
			assertTrue(cooked.sellable(), e.getValue() + " must be sellable");
			long margin = cooked.sell() - raw.buy();
			assertTrue(margin >= MIN_COOK_MARGIN && margin <= MAX_COOK_MARGIN, e.getKey() + " margin " + margin);
		}
	}

	@Test
	void growingCropsIsProfitable() {
		for (String crop : List.of("potato", "carrot", "wheat", "beetroot")) {
			ShopConfig.Item c = item(crop);
			assertTrue(c.sellable(), crop);
			assertTrue(c.sell() * YIELD_PER_PLANT > seedCost(crop), crop + " growth not profitable");
		}
		for (String seed : List.of("wheat_seeds", "beetroot_seeds")) {
			assertTrue(item(seed).buy() <= 3, seed + " should be cheap");
		}
		assertTrue(item("wheat").sell() * YIELD_PER_PLANT >= 4 * item("wheat_seeds").buy());
	}

	static long seedCost(String crop) {
		return switch (crop) {
			case "wheat" -> item("wheat_seeds").buy();
			case "beetroot" -> item("beetroot_seeds").buy();
			default -> item(crop).buy();
		};
	}

	@Test
	void pricesRiseWithRarity() {
		long[] medians = config.tiers().stream().mapToLong(t -> median(config.itemsIn(t.id()))).toArray();
		for (int i = 1; i < medians.length; i++) assertTrue(medians[i] > medians[i - 1], "median tier " + i);
		String[] ladder = {"iron_ingot", "gold_ingot", "diamond", "netherite_scrap", "netherite_ingot"};
		for (int i = 1; i < ladder.length; i++) {
			assertTrue(item(ladder[i]).buy() > item(ladder[i - 1]).buy(), ladder[i]);
		}
		assertTrue(item("diamond_pickaxe").buy() > item("iron_pickaxe").buy());
		assertTrue(item("netherite_pickaxe").buy() > item("diamond_pickaxe").buy());
		assertTrue(item("diamond_pickaxe").buy() > 3 * item("diamond").buy());
	}

	@Test
	void blackMarketPoolIsWideAndOffersAreDistinct() {
		long gear = config.items().stream().filter(i -> BlackMarket.gearType(i.id()) != null).count();
		assertTrue(gear >= 24, "only " + gear + " eligible gear items");
		for (long day = 0; day <= 200; day++) {
			List<BlackMarket.Offer> offers = BlackMarket.generate(day, 12345L, config).stream()
				.filter(o -> o.kind() == BlackMarket.Kind.GEAR).toList();
			assertEquals(6, offers.size(), "day " + day);
			assertEquals(6, offers.stream().map(BlackMarket.Offer::itemId).distinct().count(), "day " + day);
		}
	}

	@Test
	void gearLaddersStoneChainmailIron() {
		assertTrue(item("stone_pickaxe").buy() < item("iron_pickaxe").buy());
		assertTrue(item("stone_sword").buy() < item("iron_sword").buy());
		assertTrue(item("stone_axe").buy() < item("iron_axe").buy());
		for (String p : List.of("helmet", "chestplate", "leggings", "boots")) {
			assertTrue(item("chainmail_" + p).buy() < item("iron_" + p).buy(), p);
		}
	}

	@Test
	void everyVanillaToolAndArmorPieceIsStocked() {
		for (String m : List.of("wooden", "stone", "copper", "golden", "iron", "diamond", "netherite")) {
			for (String t : List.of("sword", "pickaxe", "axe", "shovel", "hoe", "spear")) item(m + "_" + t);
		}
		for (String m : List.of("leather", "copper", "chainmail", "golden", "iron", "diamond", "netherite")) {
			for (String p : List.of("helmet", "chestplate", "leggings", "boots")) item(m + "_" + p);
		}
		for (String g : List.of("turtle_helmet", "shears", "flint_and_steel", "bow", "crossbow", "shield", "fishing_rod")) item(g);
		assertTrue(item("golden_pickaxe").buy() < item("diamond_pickaxe").buy());
		assertTrue(item("wooden_pickaxe").buy() < item("stone_pickaxe").buy());
	}

	static long median(List<ShopConfig.Item> items) {
		long[] b = items.stream().mapToLong(ShopConfig.Item::buy).sorted().toArray();
		return b[b.length / 2];
	}
}
