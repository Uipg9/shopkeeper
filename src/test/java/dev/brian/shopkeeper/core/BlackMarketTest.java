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
		return richConfig(new ShopConfig.BlackMarketSettings(enabled, slots));
	}

	static ShopConfig richConfig(ShopConfig.BlackMarketSettings bm) {
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
				bm);
	}

	static List<BlackMarket.Offer> gear(List<BlackMarket.Offer> offers) {
		return offers.stream().filter(o -> o.kind() == BlackMarket.Kind.GEAR).toList();
	}

	static List<BlackMarket.Offer> of(List<BlackMarket.Offer> offers, BlackMarket.Kind kind) {
		return offers.stream().filter(o -> o.kind() == kind).toList();
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
					for (BlackMarket.Offer o : gear(offers)) {
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
		for (BlackMarket.Offer o : gear(BlackMarket.generate(3, 3, config))) {
			assertNotNull(BlackMarket.gearType(o.itemId()), o.itemId());
		}
		assertEquals(6, gear(BlackMarket.generate(3, 3, config)).size());
	}

	@Test
	void slotsAndSmallPool() {
		assertEquals(6, gear(BlackMarket.generate(1, 1, richConfig(6, true))).size());
		assertEquals(1, gear(BlackMarket.generate(1, 1, richConfig(1, true))).size());
		assertEquals(9, gear(BlackMarket.generate(1, 1, richConfig(9, true))).size());
		assertEquals(18, gear(BlackMarket.generate(1, 1, richConfig(18, true))).size());
		var tiny = new ShopConfig(0, 0.3, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")),
				List.of(ShopConfig.makeItem("minecraft:iron_sword", "t", 100, null, null, 0.3),
						ShopConfig.makeItem("minecraft:dirt", "t", 1, null, null, 0.3)));
		assertEquals(1, gear(BlackMarket.generate(1, 1, tiny)).size());
		var none = new ShopConfig(0, 0.3, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")),
				List.of(ShopConfig.makeItem("minecraft:dirt", "t", 1, null, null, 0.3)));
		assertTrue(gear(BlackMarket.generate(1, 1, none)).isEmpty());
		assertEquals(7, BlackMarket.generate(1, 1, none).size(), "extras do not need gear in the config");
		var noneNoExtras = new ShopConfig(0, 0.3, List.of(new ShopConfig.Tier("t", "T", 0, "minecraft:stone")),
				List.of(ShopConfig.makeItem("minecraft:dirt", "t", 1, null, null, 0.3)),
				new ShopConfig.BlackMarketSettings(true, 6, false, false, false));
		assertTrue(BlackMarket.generate(1, 1, noneNoExtras).isEmpty());
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
				for (BlackMarket.Offer o : gear(BlackMarket.generate(day, seed, richConfig(9, true)))) {
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
					for (BlackMarket.Offer o : gear(BlackMarket.generate(day, seed, cfg))) {
						assertTrue(o.durabilityLeft() >= 0.10 - 1e-9 && o.durabilityLeft() <= 0.65 + 1e-9, "" + o.durabilityLeft());
						long base = cfg.item(o.itemId()).buy();
						assertTrue(o.price() >= 1, "price " + o.price());
						assertTrue(o.price() <= Math.max(1, base * 70 / 100), o.itemId() + " base " + base + " price " + o.price());
						long full = BlackMarket.price(base, o.durabilityLeft(), o.enchants());
						assertEquals(o.lucky() ? Math.max(1, full / 2) : full, o.price());
					}
				}
			}
		}
	}

	@Test
	void kindCountsAndSlots() {
		for (long seed : SEEDS) {
			for (long day = 0; day <= 500; day += 5) {
				List<BlackMarket.Offer> offers = BlackMarket.generate(day, seed, richConfig(18, true));
				assertEquals(18, gear(offers).size());
				assertEquals(BlackMarket.BOOKS_PER_DAY, of(offers, BlackMarket.Kind.BOOK).size());
				assertEquals(BlackMarket.RARE_PER_DAY, of(offers, BlackMarket.Kind.RARE).size());
				assertEquals(BlackMarket.CRATES_PER_DAY, of(offers, BlackMarket.Kind.CRATE).size());
				assertTrue(offers.size() <= 45);
				Set<Integer> slots = new HashSet<>();
				for (int i = 0; i < offers.size(); i++) {
					assertEquals(i, offers.get(i).slot());
					assertTrue(slots.add(offers.get(i).slot()));
					assertTrue(offers.get(i).slot() < 45);
				}
			}
		}
	}

	@Test
	void exactlyOneLuckyAtHalfPrice() {
		int[] luckyKinds = new int[BlackMarket.Kind.values().length];
		for (long seed : SEEDS) {
			for (long day = 0; day <= 1000; day++) {
				List<BlackMarket.Offer> offers = BlackMarket.generate(day, seed, config);
				List<BlackMarket.Offer> lucky = offers.stream().filter(BlackMarket.Offer::lucky).toList();
				assertEquals(1, lucky.size(), "day " + day);
				assertTrue(lucky.get(0).price() >= 1);
				luckyKinds[lucky.get(0).kind().ordinal()]++;
			}
		}
		for (int n : luckyKinds) assertTrue(n > 0, "every kind can be lucky");
	}

	@Test
	void extrasAreDeterministicAndVary() {
		Set<List<BlackMarket.Offer>> seen = new HashSet<>();
		for (long day = 0; day < 20; day++) {
			var a = BlackMarket.generate(day, 9, config);
			assertEquals(a, BlackMarket.generate(day, 9, config));
			seen.add(a.subList(6, a.size()));
		}
		assertTrue(seen.size() >= 18);
	}

	@Test
	void rareGoodsAreValid() {
		Set<String> allowed = new HashSet<>();
		for (var r : BlackMarket.RARE_GOODS) allowed.addAll(r.ids());
		int totems = 0, netherite = 0, total = 0;
		for (long seed : SEEDS) {
			for (long day = 0; day <= 2000; day++) {
				var rare = of(BlackMarket.generate(day, seed, config), BlackMarket.Kind.RARE);
				assertEquals(rare.size(), rare.stream().map(BlackMarket.Offer::itemId).distinct().count());
				for (var o : rare) {
					total++;
					assertTrue(allowed.contains(o.itemId()), o.itemId());
					assertFalse(o.itemId().contains("elytra"));
					assertTrue(o.price() >= 1);
					if (o.itemId().equals("minecraft:totem_of_undying")) totems++;
					if (o.itemId().contains("netherite")) netherite++;
					if (o.itemId().equals("minecraft:nautilus_shell")) assertTrue(o.count() >= 2 && o.count() <= 4);
					else assertEquals(1, o.count());
					boolean used = o.itemId().equals("minecraft:trident") || o.itemId().contains("netherite");
					if (used) {
						assertTrue(o.durabilityLeft() >= 0.10 - 1e-9 && o.durabilityLeft() <= 0.65 + 1e-9);
					} else {
						assertEquals(1.0, o.durabilityLeft());
					}
				}
			}
		}
		assertTrue(totems > 0 && totems < total / 15, "totems " + totems + "/" + total);
		assertTrue(netherite > 0 && netherite < totems * 2, "netherite " + netherite);
	}

	@Test
	void booksAreCappedAndMendingIsRare() {
		Map<String, Integer> max = new java.util.HashMap<>();
		for (var b : BlackMarket.BOOK_OPTIONS) max.put(b.id(), b.maxLevel());
		int mending = 0, books = 0;
		for (long seed : SEEDS) {
			for (long day = 0; day <= 2000; day++) {
				var list = of(BlackMarket.generate(day, seed, config), BlackMarket.Kind.BOOK);
				Set<String> ids = new HashSet<>();
				for (var o : list) {
					books++;
					assertEquals("minecraft:enchanted_book", o.itemId());
					assertEquals(1, o.enchants().size());
					var e = o.enchants().get(0);
					assertTrue(ids.add(e.enchantId()), "dupe book " + e);
					if (e.enchantId().equals("minecraft:mending")) {
						mending++;
						assertEquals(1, e.level());
						assertTrue(o.price() >= 1500);
						continue;
					}
					assertTrue(max.containsKey(e.enchantId()), e.toString());
					assertTrue(e.level() >= 1 && e.level() <= Math.max(1, max.get(e.enchantId()) - 1), e.toString());
					assertTrue(o.price() >= 1 && o.price() <= 1000, o.toString());
				}
			}
		}
		double frac = (double) mending / books;
		assertTrue(frac > 0.05 && frac < 0.12, "mending fraction " + frac);
	}

	@Test
	void crateFitsBundleAndIsWorthIt() {
		long value = 0, price = 0;
		int n = 0;
		for (long seed : SEEDS) {
			for (long day = 0; day <= 2000; day++) {
				for (var o : of(BlackMarket.generate(day, seed, config), BlackMarket.Kind.CRATE)) {
					n++;
					assertEquals("minecraft:bundle", o.itemId());
					assertTrue(o.contents().size() >= 3 && o.contents().size() <= 5, o.toString());
					int items = 0;
					for (var g : o.contents()) {
						assertTrue(g.count() >= 1);
						items += g.count();
					}
					assertTrue(items <= 48, "items " + items);
					assertEquals(o.contents().size(), o.contents().stream().map(BlackMarket.Good::itemId).distinct().count());
					value += BlackMarket.crateValue(o);
					price += o.lucky() ? o.price() * 2 : o.price();
				}
			}
		}
		assertTrue(n > 0);
		assertTrue(value > price, "avg value " + value / n + " vs avg price " + price / n);
		assertTrue(value < price * 3, "too generous: avg value " + value / n);
	}

	@Test
	void togglesRemoveKind() {
		var all = BlackMarket.generate(4, 4, richConfig(6, true));
		var noBooks = BlackMarket.generate(4, 4, richConfig(new ShopConfig.BlackMarketSettings(true, 6, false, true, true)));
		var noRare = BlackMarket.generate(4, 4, richConfig(new ShopConfig.BlackMarketSettings(true, 6, true, false, true)));
		var noCrate = BlackMarket.generate(4, 4, richConfig(new ShopConfig.BlackMarketSettings(true, 6, true, true, false)));
		var gearOnly = BlackMarket.generate(4, 4, richConfig(new ShopConfig.BlackMarketSettings(true, 6, false, false, false)));
		assertTrue(of(noBooks, BlackMarket.Kind.BOOK).isEmpty());
		assertTrue(of(noRare, BlackMarket.Kind.RARE).isEmpty());
		assertTrue(of(noCrate, BlackMarket.Kind.CRATE).isEmpty());
		assertEquals(6, gearOnly.size());
		assertEquals(all.size() - BlackMarket.BOOKS_PER_DAY, noBooks.size());
		// The other kinds keep their items when one kind is turned off.
		assertEquals(itemIds(of(all, BlackMarket.Kind.RARE)), itemIds(of(noBooks, BlackMarket.Kind.RARE)));
		assertEquals(itemIds(gear(all)), itemIds(gear(gearOnly)));
	}

	static List<String> itemIds(List<BlackMarket.Offer> offers) {
		return offers.stream().map(BlackMarket.Offer::itemId).toList();
	}

	@Test
	void oldConstructorDefaultsToPlainGear() {
		var o = new BlackMarket.Offer(2, "minecraft:iron_sword", 0.5, List.of(), 10);
		assertEquals(BlackMarket.Kind.GEAR, o.kind());
		assertEquals(1, o.count());
		assertFalse(o.lucky());
		assertTrue(o.contents().isEmpty());
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
