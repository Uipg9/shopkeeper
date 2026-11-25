package dev.brian.shopkeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShopkeeperTest {
	@Test
	void idUsesModNamespace() {
		var id = Shopkeeper.id("example");
		assertEquals(Shopkeeper.MOD_ID, id.getNamespace());
		assertEquals("example", id.getPath());
	}

	@Test
	void langKeyFollowsMinecraftConvention() {
		assertEquals("item.shopkeeper.example", Shopkeeper.langKey("item", "example"));
	}
}
