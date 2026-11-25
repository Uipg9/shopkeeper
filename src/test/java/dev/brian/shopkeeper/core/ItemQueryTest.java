package dev.brian.shopkeeper.core;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemQueryTest {
	private static final List<String> IDS = List.of(
		"minecraft:diamond", "minecraft:diamond_pickaxe", "minecraft:diamond_sword",
		"minecraft:iron_ingot", "minecraft:oak_log", "minecraft:oak_planks", "minecraft:stick", "minecraft:glow_ink_sac");

	@Test void exactFullId() {
		assertEquals("minecraft:diamond", ItemQuery.resolve("minecraft:diamond", IDS).id());
	}

	@Test void exactShortIdBeatsPrefixes() {
		assertEquals("minecraft:diamond", ItemQuery.resolve("diamond", IDS).id());
	}

	@Test void normalizesCaseSpacesHyphens() {
		assertEquals("minecraft:iron_ingot", ItemQuery.resolve("Iron Ingot", IDS).id());
		assertEquals("minecraft:iron_ingot", ItemQuery.resolve("  IRON-ingot ", IDS).id());
		assertEquals("minecraft:diamond_pickaxe", ItemQuery.resolve("Minecraft:Diamond Pickaxe", IDS).id());
	}

	@Test void uniquePrefix() {
		assertEquals("minecraft:iron_ingot", ItemQuery.resolve("iron_i", IDS).id());
	}

	@Test void uniqueSubstring() {
		assertEquals("minecraft:glow_ink_sac", ItemQuery.resolve("ink", IDS).id());
	}

	@Test void prefixBeatsSubstring() {
		// "stick" is a prefix of stick; "glow_ink_sac" never matters. "sti" prefixes only stick.
		List<String> ids = List.of("minecraft:stick", "minecraft:lipstick");
		assertEquals("minecraft:stick", ItemQuery.resolve("sti", ids).id());
	}

	@Test void ambiguousListsUpToFiveCandidates() {
		ItemQuery.Result r = ItemQuery.resolve("oak", IDS);
		assertFalse(r.ok());
		assertEquals(List.of("oak_log", "oak_planks"), r.candidates());
		assertTrue(r.error().contains("oak_log") && r.error().contains("oak_planks"));
		List<String> many = List.of("minecraft:a1", "minecraft:a2", "minecraft:a3", "minecraft:a4",
			"minecraft:a5", "minecraft:a6", "minecraft:a7");
		ItemQuery.Result r2 = ItemQuery.resolve("a", many);
		assertFalse(r2.ok());
		assertEquals(5, r2.candidates().size());
	}

	@Test void noMatch() {
		ItemQuery.Result r = ItemQuery.resolve("netherite", IDS);
		assertFalse(r.ok());
		assertEquals("No shop item matching netherite", r.error());
	}

	@Test void oddInput() {
		assertFalse(ItemQuery.resolve(null, IDS).ok());
		assertFalse(ItemQuery.resolve("", IDS).ok());
		assertFalse(ItemQuery.resolve("   ", IDS).ok());
		assertFalse(ItemQuery.resolve("diamond", List.of()).ok());
		assertFalse(ItemQuery.resolve("minecraft:", IDS).ok());
		assertFalse(ItemQuery.resolve("other:diamond", IDS).ok());
	}

	@Test void nonMinecraftNamespaceExactAndShortId() {
		List<String> ids = List.of("mod:ruby", "minecraft:ruby_block");
		assertEquals("mod:ruby", ItemQuery.resolve("mod:ruby", ids).id());
		assertEquals("minecraft:ruby_block", ItemQuery.resolve("ruby_b", ids).id());
	}

	@Test void shortIdStripsMinecraftOnly() {
		assertEquals("diamond", ItemQuery.shortId("minecraft:diamond"));
		assertEquals("mod:ruby", ItemQuery.shortId("mod:ruby"));
	}
}
