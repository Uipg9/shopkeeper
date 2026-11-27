package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ShopConfigParserTest {
	static String json(String fraction, String tiers, String items) {
		return "{\"startingBalance\":100,\"sellFraction\":" + fraction + ",\"tiers\":" + tiers + ",\"items\":" + items + "}";
	}

	static final String TIERS = "[{\"id\":\"basics\",\"name\":\"Basics\",\"unlockPrice\":0,\"icon\":\"minecraft:oak_log\"},"
			+ "{\"id\":\"adv\",\"name\":\"Adv\",\"unlockPrice\":500,\"icon\":\"minecraft:gold_ingot\"}]";

	static void assertError(ShopConfigParser.Result r, String fragment) {
		assertFalse(r.ok(), "expected failure");
		assertNull(r.config());
		assertTrue(r.errors().stream().anyMatch(e -> e.contains(fragment)), "errors " + r.errors() + " lack '" + fragment + "'");
	}

	@Test
	void parsesValidConfig() {
		var r = ShopConfigParser.parse(json("0.3", TIERS,
				"[{\"id\":\"minecraft:potato\",\"tier\":\"basics\",\"buy\":10,\"sell\":null,\"maxStack\":null},"
						+ "{\"id\":\"minecraft:gold_ingot\",\"tier\":\"adv\",\"buy\":50,\"sell\":20,\"maxStack\":16}]"));
		assertTrue(r.ok(), r.errors().toString());
		var c = r.config();
		assertEquals(100, c.startingBalance());
		assertEquals(2, c.tiers().size());
		var potato = c.item("minecraft:potato");
		assertEquals(3, potato.sell());
		assertTrue(potato.sellable());
		assertNull(potato.maxStack());
		assertEquals(20, c.item("minecraft:gold_ingot").sell());
		assertEquals(16, c.item("minecraft:gold_ingot").maxStack());
	}

	@Test
	void fractionBounds() {
		String items = "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":10}]";
		assertTrue(ShopConfigParser.parse(json("0.25", TIERS, items)).ok());
		assertTrue(ShopConfigParser.parse(json("0.4", TIERS, items)).ok());
		assertError(ShopConfigParser.parse(json("0.24", TIERS, items)), "sellFraction");
		assertError(ShopConfigParser.parse(json("0.41", TIERS, items)), "sellFraction");
		assertError(ShopConfigParser.parse(json("1.0", TIERS, items)), "sellFraction");
	}

	@Test
	void onlyBuyOneIsUnsellable() {
		var r = ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:cheap\",\"tier\":\"basics\",\"buy\":1}]"));
		assertTrue(r.ok());
		var it = r.config().item("a:cheap");
		assertFalse(it.sellable());
		assertEquals(0, it.sell());
	}

	@Test
	void cheapItemsSellForAtLeastOne() {
		var r = ShopConfigParser.parse(json("0.25", TIERS, "[{\"id\":\"a:two\",\"tier\":\"basics\",\"buy\":2},"
				+ "{\"id\":\"a:three\",\"tier\":\"basics\",\"buy\":3},{\"id\":\"a:four\",\"tier\":\"basics\",\"buy\":4}]"));
		assertTrue(r.ok(), r.errors().toString());
		for (String id : new String[] {"a:two", "a:three", "a:four"}) {
			var it = r.config().item(id);
			assertTrue(it.sellable(), id);
			assertEquals(1, it.sell(), id);
		}
	}

	@Test
	void rejections() {
		assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"nope\",\"buy\":10}]")), "unknown tier");
		assertError(ShopConfigParser.parse(json("0.3", TIERS,
				"[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":10},{\"id\":\"a:b\",\"tier\":\"adv\",\"buy\":10}]")), "duplicate item");
		assertError(ShopConfigParser.parse(json("0.3", TIERS.replace("\"adv\"", "\"basics\""), "[]")), "duplicate tier");
		assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":-5}]")), "buy");
		assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":0}]")), "buy");
		assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":10,\"sell\":-1}]")), "sell");
		assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":10,\"sell\":10}]")), "sell");
		assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":10,\"sell\":11}]")), "sell");
		assertError(ShopConfigParser.parse(json("0.3", "[]", "[]")), "tier");
		assertError(ShopConfigParser.parse(json("0.3",
				"[{\"id\":\"x\",\"name\":\"X\",\"unlockPrice\":5,\"icon\":\"a:b\"}]", "[]")), "first tier");
		assertError(ShopConfigParser.parse(json("0.3",
				"[{\"id\":\"x\",\"name\":\"X\",\"unlockPrice\":0,\"icon\":\"a:b\"},{\"id\":\"y\",\"name\":\"Y\",\"unlockPrice\":-1,\"icon\":\"a:b\"}]", "[]")), "unlockPrice");
	}

	@Test
	void max450ItemsPerTier() {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < 451; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"id\":\"a:i").append(i).append("\",\"tier\":\"basics\",\"buy\":10}");
		}
		String items451 = sb.append(']').toString();
		assertError(ShopConfigParser.parse(json("0.3", TIERS, items451)), "tier 'basics' has more than 450 items");
		String items450 = items451.substring(0, items451.lastIndexOf(",{")) + "]";
		assertTrue(ShopConfigParser.parse(json("0.3", TIERS, items450)).ok());
	}

	@Test
	void rejectsAirItems() {
		for (String air : new String[] {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}) {
			assertError(ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"" + air + "\",\"tier\":\"basics\",\"buy\":10}]")), "air");
		}
	}

	@Test
	void maxFourTiers() {
		String t = "{\"id\":\"%s\",\"name\":\"N\",\"unlockPrice\":%d,\"icon\":\"a:b\"}";
		StringBuilder four = new StringBuilder("[");
		for (int i = 0; i < 4; i++) four.append(i > 0 ? "," : "").append(t.formatted("t" + i, i * 10));
		String four4 = four.append(']').toString();
		assertTrue(ShopConfigParser.parse(json("0.3", four4, "[]")).ok());
		String five = four4.substring(0, four4.length() - 1) + "," + t.formatted("t4", 50) + "]";
		assertError(ShopConfigParser.parse(json("0.3", five, "[]")), "4 tiers");
	}

	static String withBlackMarket(String block) {
		String base = json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":10}]");
		return base.substring(0, base.length() - 1) + ",\"blackMarket\":" + block + "}";
	}

	@Test
	void blackMarketDefaultsWhenAbsent() {
		var r = ShopConfigParser.parse(json("0.3", TIERS, "[]"));
		assertTrue(r.ok());
		assertEquals(new ShopConfig.BlackMarketSettings(true, 6), r.config().blackMarket());
	}

	@Test
	void blackMarketParsed() {
		var r = ShopConfigParser.parse(withBlackMarket("{\"enabled\":false,\"slots\":9}"));
		assertTrue(r.ok(), r.errors().toString());
		assertEquals(new ShopConfig.BlackMarketSettings(false, 9), r.config().blackMarket());
		r = ShopConfigParser.parse(withBlackMarket("{\"slots\":1}"));
		assertEquals(new ShopConfig.BlackMarketSettings(true, 1), r.config().blackMarket());
		r = ShopConfigParser.parse(withBlackMarket("{}"));
		assertEquals(new ShopConfig.BlackMarketSettings(true, 6), r.config().blackMarket());
		r = ShopConfigParser.parse(withBlackMarket("{\"slots\":18}"));
		assertEquals(new ShopConfig.BlackMarketSettings(true, 18), r.config().blackMarket());
	}

	@Test
	void blackMarketKindToggles() {
		var r = ShopConfigParser.parse(withBlackMarket("{\"enabled\":true,\"slots\":6}"));
		assertTrue(r.ok(), r.errors().toString());
		var bm = r.config().blackMarket();
		assertTrue(bm.books() && bm.rare() && bm.crate());
		r = ShopConfigParser.parse(withBlackMarket("{\"books\":false,\"rare\":true,\"crate\":false}"));
		assertTrue(r.ok(), r.errors().toString());
		assertEquals(new ShopConfig.BlackMarketSettings(true, 6, false, true, false), r.config().blackMarket());
		r = ShopConfigParser.parse(withBlackMarket("{\"rare\":false,\"books\":null}"));
		assertEquals(new ShopConfig.BlackMarketSettings(true, 6, true, false, true), r.config().blackMarket());
	}

	@Test
	void blackMarketRejectsBadValues() {
		assertError(ShopConfigParser.parse(withBlackMarket("{\"slots\":0}")), "slots");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"slots\":19}")), "slots");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"books\":1}")), "books");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"rare\":\"no\"}")), "rare");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"crate\":[]}")), "crate");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"slots\":2.5}")), "slots");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"slots\":\"six\"}")), "slots");
		assertError(ShopConfigParser.parse(withBlackMarket("{\"enabled\":\"yes\"}")), "enabled");
		assertError(ShopConfigParser.parse(withBlackMarket("5")), "blackMarket");
		assertError(ShopConfigParser.parse(withBlackMarket("[]")), "blackMarket");
	}

	@Test
	void badDataNeverThrows() {
		String[] bads = { "", "not json", "[]", "null", "{}", "{\"tiers\":5}", "{\"tiers\":[1],\"items\":[2]}",
				"{\"sellFraction\":\"x\",\"tiers\":[{}],\"items\":[{}]}",
				json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":1.5}]"),
				json("0.3", TIERS, "[{\"id\":\"a:b\",\"tier\":\"basics\",\"buy\":99999999999999999999999}]") };
		for (String bad : bads) {
			var r = assertDoesNotThrow(() -> ShopConfigParser.parse(bad), bad);
			assertFalse(r.ok(), bad);
			assertFalse(r.errors().isEmpty());
		}
		assertDoesNotThrow(() -> ShopConfigParser.parse(null));
	}
}
