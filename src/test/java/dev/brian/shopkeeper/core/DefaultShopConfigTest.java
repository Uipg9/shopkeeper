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

	static long median(List<ShopConfig.Item> items) {
		long[] b = items.stream().mapToLong(ShopConfig.Item::buy).sorted().toArray();
		return b[b.length / 2];
	}
}
