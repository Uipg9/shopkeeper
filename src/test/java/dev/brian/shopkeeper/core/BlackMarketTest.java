package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlackMarketTest {
	static final long[] SEEDS = {0L, 1L, -7L, 123456789L, Long.MIN_VALUE};
	static final Set<String> BANNED = Set.of("netherite", "mace", "trident", "elytra");
	static final Set<String> FORBIDDEN_ENCH = Set.of("minecraft:mending", "minecraft:silk_touch",
			"minecraft:binding_curse", "minecraft:vanishing_curse");

	static ShopConfig config;

	@BeforeAll
	static void load() throws IOException {
		try (InputStream in = BlackMarketTest.class.getResourceAsStream("/shopkeeper/default_shop.json")) {
			assertNotNull(in);
			var r = ShopConfigParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			assertTrue(r.ok(), r.errors().toString());
			config = r.config();
		}
	}

	/** Config with every gear type and many materials, to exercise all enchant tables. */
	static ShopConfig richConfig(int slots, boolean enabled) {
		List<String> ids = new java.util.ArrayList<>();
		for (String m : List.of("wooden", "stone", "golden", "chainmail", "iron", "diamond", "turtle", "netherite")) {
			for (String t : List.of("sword", "axe", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots")) {
				ids.add("minecraft:" + m + "_" + t);
			}
		}
		ids.addAll(List.of("minecraft:bow", "minecraft:crossbow", "minecraft:fishing_rod", "minecraft:shield",
				"minecraft:mace", "minecraft:trident", "minecraft:elytra", "minecraft:stone", "minecraft:blaze_rod"));
		var items = ids.stream().map(id -> ShopConfig.makeItem(id, "t", 1 + (id.hashCode() & 0x3ff), null, null, 0.3)).toList();
		return new ShopConfig(0, 0.3, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")), items,
				new ShopConfig.BlackMarketSettings(enabled, slots));
	}

	@Test
	void deterministic() {
		for (long seed : SEEDS) {
			for (long day = 0; day < 50; day++) {
				assertEquals(BlackMarket.generate(day, seed, config), BlackMarket.generate(day, seed, config));
			}
		}
	}

	@Test
	void differentDaysDiffer() {
		Set<List<BlackMarket.Offer>> seen = new HashSet<>();
		for (long day = 0; day < 20; day++) seen.add(BlackMarket.generate(day, 42, richConfig(6, true)));
		assertTrue(seen.size() >= 18, "only " + seen.size() + " distinct days");
	}

	@Test
	void differentSeedsDiffer() {
		assertNotEquals(BlackMarket.generate(5, 1, richConfig(6, true)), BlackMarket.generate(5, 2, richConfig(6, true)));
	}

	@Test
	void poolFilterAndNoDuplicates() {
		for (ShopConfig cfg : List.of(config, richConfig(9, true))) {
			for (long seed : SEEDS) {
				for (long day = 0; day <= 2000; day += 7) {
					List<BlackMarket.Offer> offers = BlackMarket.generate(day, seed, cfg);
					Set<String> ids = new HashSet<>();
					for (BlackMarket.Offer o : offers) {
						assertNotNull(cfg.item(o.itemId()), o.itemId() + " not in config");
						for (String b : BANNED) assertFalse(o.itemId().contains(b), o.itemId());
						assertTrue(ids.add(o.itemId()), "duplicate " + o.itemId());
					}
					for (int i = 0; i < offers.size(); i++) assertEquals(i, offers.get(i).slot());
				}
			}
		}
	}

	@Test
	void defaultConfigOffersAreGearOnly() {
		for (BlackMarket.Offer o : BlackMarket.generate(3, 3, config)) {
			assertNotNull(BlackMarket.gearType(o.itemId()), o.itemId());
		}
		assertFalse(BlackMarket.generate(3, 3, config).isEmpty());
	}

	@Test
	void slotsAndSmallPool() {
		assertEquals(6, BlackMarket.generate(1, 1, richConfig(6, true)).size());
		assertEquals(1, BlackMarket.generate(1, 1, richConfig(1, true)).size());
		assertEquals(9, BlackMarket.generate(1, 1, richConfig(9, true)).size());
		var tiny = new ShopConfig(0, 0.3, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")),
				List.of(ShopConfig.makeItem("minecraft:iron_sword", "t", 100, null, null, 0.3),
						ShopConfig.makeItem("minecraft:dirt", "t", 1, null, null, 0.3)));
		assertEquals(1, BlackMarket.generate(1, 1, tiny).size());
		var none = new ShopConfig(0, 0.3, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")),
				List.of(ShopConfig.makeItem("minecraft:dirt", "t", 1, null, null, 0.3)));
		assertTrue(BlackMarket.generate(1, 1, none).isEmpty());
	}

	@Test
	void disabledYieldsNothing() {
		assertTrue(BlackMarket.generate(1, 1, richConfig(6, false)).isEmpty());
	}

	@Test
	void enchantsValidPerGearType() {
		int enchanted = 0, total = 0;
		for (long seed : SEEDS) {
			for (long day = 0; day <= 2000; day += 3) {
				for (BlackMarket.Offer o : BlackMarket.generate(day, seed, richConfig(9, true))) {
					total++;
					String type = BlackMarket.gearType(o.itemId());
					assertNotNull(type, o.itemId());
					Map<String, Integer> max = new java.util.HashMap<>();
					for (var opt : BlackMarket.optionsFor(type)) max.put(opt.id(), opt.maxLevel());
					assertTrue(o.enchants().size() <= 2);
					if (!o.enchants().isEmpty()) enchanted++;
					Set<String> seen = new HashSet<>();
					for (BlackMarket.Ench e : o.enchants()) {
						assertTrue(seen.add(e.enchantId()), "dupe " + e + " on " + o);
						assertTrue(e.enchantId().startsWith("minecraft:"));
						assertFalse(FORBIDDEN_ENCH.contains(e.enchantId()));
						assertTrue(max.containsKey(e.enchantId()), e + " not allowed on " + o.itemId());
						assertTrue(e.level() >= 1 && e.level() <= max.get(e.enchantId()) && e.level() <= 3, e.toString());
					}
				}
			}
		}
		double frac = (double) enchanted / total;
		assertTrue(frac > 0.6 && frac < 0.8, "enchanted fraction " + frac);
	}

	@Test
	void enchantTableSpotChecks() {
		assertEquals(Set.of("minecraft:sharpness", "minecraft:unbreaking", "minecraft:knockback", "minecraft:looting"),
				ids("sword"));
		assertTrue(ids("boots").contains("minecraft:feather_falling"));
		assertFalse(ids("helmet").contains("minecraft:feather_falling"));
		assertTrue(ids("pickaxe").contains("minecraft:fortune"));
		assertFalse(ids("axe").contains("minecraft:fortune"));
		assertEquals(Set.of("minecraft:unbreaking"), ids("shield"));
		assertEquals(Set.of("minecraft:power", "minecraft:unbreaking"), ids("bow"));
		assertEquals(Set.of("minecraft:quick_charge", "minecraft:unbreaking"), ids("crossbow"));
		assertEquals(Set.of("minecraft:lure", "minecraft:unbreaking"), ids("fishing_rod"));
	}

	static Set<String> ids(String type) {
		Set<String> s = new HashSet<>();
		for (var o : BlackMarket.optionsFor(type)) s.add(o.id());
		return s;
	}

	@Test
	void gearTypeFilter() {
		for (String ok : List.of("minecraft:iron_sword", "minecraft:wooden_hoe", "minecraft:turtle_helmet",
				"minecraft:chainmail_boots", "minecraft:golden_chestplate", "minecraft:bow", "minecraft:shield",
				"minecraft:crossbow", "minecraft:fishing_rod", "minecraft:stone_pickaxe", "minecraft:diamond_leggings")) {
			assertNotNull(BlackMarket.gearType(ok), ok);
		}
		for (String bad : List.of("minecraft:netherite_sword", "minecraft:mace", "minecraft:trident", "minecraft:elytra",
				"minecraft:blaze_rod", "minecraft:iron_ingot", "minecraft:stone", "minecraft:leather_helmet",
				"minecraft:iron_horse_armor", "mod:iron_sword", "minecraft:diamond")) {
			assertNull(BlackMarket.gearType(bad), bad);
		}
	}

	@Test
	void durabilityAndPriceBounds() {
		for (ShopConfig cfg : List.of(config, richConfig(9, true))) {
			for (long seed : SEEDS) {
				for (long day = 0; day <= 2000; day++) {
					for (BlackMarket.Offer o : BlackMarket.generate(day, seed, cfg)) {
						assertTrue(o.durabilityLeft() >= 0.10 - 1e-9 && o.durabilityLeft() <= 0.65 + 1e-9, "" + o.durabilityLeft());
						long base = cfg.item(o.itemId()).buy();
						assertTrue(o.price() >= 1, "price " + o.price());
						assertTrue(o.price() <= Math.max(1, base * 70 / 100), o.itemId() + " base " + base + " price " + o.price());
						assertEquals(BlackMarket.price(base, o.durabilityLeft(), o.enchants()), o.price());
					}
				}
			}
		}
	}

	@Test
	void priceFormula() {
		// base 1000, 50% durability: 1000*(0.15+0.175)=325, + 10*(3+2)=50 -> 375
		assertEquals(375, BlackMarket.price(1000, 0.5, List.of(new BlackMarket.Ench("minecraft:sharpness", 3),
				new BlackMarket.Ench("minecraft:unbreaking", 2))));
		// cap at 70%
		assertEquals(70, BlackMarket.price(100, 0.65, List.of(new BlackMarket.Ench("a:b", 3), new BlackMarket.Ench("a:c", 3))));
		// tiny base never below 1
		assertEquals(1, BlackMarket.price(1, 0.1, List.of()));
		assertEquals(1, BlackMarket.price(2, 0.1, List.of(new BlackMarket.Ench("a:b", 3))));
	}
}
