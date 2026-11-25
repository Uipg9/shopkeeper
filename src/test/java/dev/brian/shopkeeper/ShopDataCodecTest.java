package dev.brian.shopkeeper;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.brian.shopkeeper.core.Economy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopDataCodecTest {
	private static ShopData decode(String json) {
		return ShopData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	private static String readDefault() {
		try {
			return new String(ShopService.class.getResourceAsStream("/shopkeeper/default_shop.json").readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	@Test
	void emptyRoundTrip() {
		JsonElement j = ShopData.CODEC.encodeStart(JsonOps.INSTANCE, new ShopData()).getOrThrow();
		assertTrue(decode(j.toString()).players.isEmpty());
	}

	@Test
	void populatedRoundTrip() {
		UUID id = UUID.randomUUID();
		ShopData d = new ShopData();
		d.capture(Map.of(id, new Economy.PlayerSnapshot(123, Set.of("a", "b"), true)));
		JsonElement j = ShopData.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
		Map<UUID, Economy.PlayerSnapshot> back = decode(j.toString()).toSnapshot();
		assertEquals(Set.of(id), back.keySet());
		assertEquals(123, back.get(id).balance());
		assertEquals(Set.of("a", "b"), back.get(id).unlockedTiers());
		assertTrue(back.get(id).grantedStarting());
	}

	@Test
	void badValuesAreSanitised() {
		UUID id = UUID.randomUUID();
		ShopData d = decode("{\"players\":{\"not-a-uuid\":{\"balance\":5},\"" + id + "\":{\"balance\":-9}}}");
		Map<UUID, Economy.PlayerSnapshot> s = d.toSnapshot();
		assertEquals(Set.of(id), s.keySet());
		assertEquals(0, s.get(id).balance());
		assertTrue(s.get(id).unlockedTiers().isEmpty());
		assertFalse(s.get(id).grantedStarting());
	}

	@Test
	void missingPlayersFieldIsEmpty() {
		assertTrue(decode("{}").players.isEmpty());
	}

	@Test
	void unknownTiersPreservedThroughRestoreAndSave() {
		Economy e = new Economy(ShopService.resolveConfig(null, readDefault()).config());
		UUID id = UUID.randomUUID();
		ShopData d = new ShopData();
		d.capture(Map.of(id, new Economy.PlayerSnapshot(7, Set.of("nope"), true)));
		e.restore(d.toSnapshot());
		assertEquals(7, e.balance(id));
		ShopData d2 = new ShopData();
		d2.capture(e.snapshot());
		assertTrue(d2.toSnapshot().get(id).unlockedTiers().contains("nope"));
	}

	@Test
	void resolveConfigValidUserWins() {
		String def = readDefault();
		ShopService.Loaded l = ShopService.resolveConfig(def, def);
		assertFalse(l.usedDefault());
		assertTrue(l.errors().isEmpty());
		assertNotNull(l.config());
	}

	@Test
	void resolveConfigFallsBackOnInvalidOrMissing() {
		String def = readDefault();
		for (String bad : new String[] {null, "", "{not json", "{}"}) {
			ShopService.Loaded l = ShopService.resolveConfig(bad, def);
			assertTrue(l.usedDefault(), String.valueOf(bad));
			assertFalse(l.errors().isEmpty());
			assertNotNull(l.config());
		}
	}

	@Test
	void resolveConfigBrokenDefaultGivesNullConfig() {
		ShopService.Loaded l = ShopService.resolveConfig("x", "y");
		assertNull(l.config());
		assertFalse(l.errors().isEmpty());
	}
}
