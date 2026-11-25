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
	void unsellableWhenSellRoundsOutOfRange() {
		var r = ShopConfigParser.parse(json("0.3", TIERS, "[{\"id\":\"a:cheap\",\"tier\":\"basics\",\"buy\":1}]"));
		assertTrue(r.ok());
		var it = r.config().item("a:cheap");
		assertFalse(it.sellable());
		assertEquals(0, it.sell());
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
	void maxFortyFiveItemsPerTier() {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < 46; i++) {
			if (i > 0) sb.append(',');
			sb.append("{\"id\":\"a:i").append(i).append("\",\"tier\":\"basics\",\"buy\":10}");
		}
		String items46 = sb.append(']').toString();
		assertError(ShopConfigParser.parse(json("0.3", TIERS, items46)), "45");
		String items45 = items46.substring(0, items46.lastIndexOf(",{")) + "]";
		assertTrue(ShopConfigParser.parse(json("0.3", TIERS, items45)).ok());
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
