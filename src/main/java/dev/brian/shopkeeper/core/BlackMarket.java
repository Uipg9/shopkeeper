package dev.brian.shopkeeper.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure daily "black market": cheap used vanilla gear with low-to-medium durability and a few modest enchants.
 * Deterministic from (day, seed) and the config only. Only items already in the config are offered.
 *
 * <p>Price = clamp(round(base * (0.15 + 0.35 * durabilityLeft)) + 10 * sumOfEnchantLevels, 1, floor(base * 0.7)),
 * computed in integer math.
 */
public final class BlackMarket {
	private BlackMarket() {}

	public static final double MIN_DURABILITY = 0.10;
	public static final double MAX_DURABILITY = 0.65;
	/** Durability is drawn in whole percents between these bounds (inclusive). */
	static final int MIN_DURABILITY_PCT = 10;
	static final int MAX_DURABILITY_PCT = 65;
	/** Base price factor in percent: 15 + 35 * durability. */
	static final int PRICE_BASE_PCT = 15;
	static final int PRICE_DURABILITY_PCT = 35;
	static final long PRICE_PER_ENCHANT_LEVEL = 10;
	/** Hard cap: price never exceeds this percent of the new item's buy price. */
	static final int MAX_PRICE_PCT = 70;
	static final int MAX_ENCHANTS = 2;
	/** Percent chance that an offer gets at least one enchant. */
	static final int ENCHANTED_PCT = 70;
	/** Percent chance that an enchanted offer gets a second enchant. */
	static final int SECOND_ENCHANT_PCT = 40;
	static final int COMMON_WEIGHT = 4;
	static final int RARE_WEIGHT = 1;

	public record Ench(String enchantId, int level) {}

	public record Offer(int slot, String itemId, double durabilityLeft, List<Ench> enchants, long price) {
		public Offer {
			enchants = List.copyOf(enchants);
		}
	}

	/** One enchant candidate: id, max level, and whether it is rare (down-weighted). */
	record Option(String id, int maxLevel, boolean rare) {}

	private static final List<String> GOOD_MATERIALS =
			List.of("wooden", "stone", "golden", "chainmail", "iron", "diamond", "turtle");

	private static final Option UNBREAKING_2 = new Option("minecraft:unbreaking", 2, false);
	private static final Option UNBREAKING_3 = new Option("minecraft:unbreaking", 3, false);

	/** Gear type ("sword", "boots", "bow", ...) from an item id, or null when the item is not eligible. */
	static String gearType(String itemId) {
		if (!itemId.startsWith("minecraft:")) return null;
		String name = itemId.substring("minecraft:".length());
		switch (name) {
			case "bow", "crossbow", "fishing_rod", "shield":
				return name;
			default:
				break;
		}
		int us = name.indexOf('_');
		if (us <= 0) return null;
		if (!GOOD_MATERIALS.contains(name.substring(0, us))) return null;
		String type = name.substring(us + 1);
		return switch (type) {
			case "sword", "axe", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots" -> type;
			default -> null;
		};
	}

	static List<Option> optionsFor(String type) {
		return switch (type) {
			case "sword" -> List.of(new Option("minecraft:sharpness", 3, false), UNBREAKING_2,
					new Option("minecraft:knockback", 1, true), new Option("minecraft:looting", 1, true));
			case "axe", "hoe" -> List.of(new Option("minecraft:efficiency", 3, false), UNBREAKING_3);
			case "pickaxe", "shovel" -> List.of(new Option("minecraft:efficiency", 3, false), UNBREAKING_3,
					new Option("minecraft:fortune", 1, true));
			case "helmet", "chestplate", "leggings" -> List.of(new Option("minecraft:protection", 3, false), UNBREAKING_2,
					new Option("minecraft:thorns", 1, true));
			case "boots" -> List.of(new Option("minecraft:protection", 3, false), UNBREAKING_2,
					new Option("minecraft:feather_falling", 3, false), new Option("minecraft:thorns", 1, true));
			case "bow" -> List.of(new Option("minecraft:power", 3, false), UNBREAKING_2);
			case "crossbow" -> List.of(new Option("minecraft:quick_charge", 2, false), UNBREAKING_2);
			case "fishing_rod" -> List.of(new Option("minecraft:lure", 2, false), UNBREAKING_2);
			case "shield" -> List.of(UNBREAKING_2);
			default -> List.of();
		};
	}

	/** Price for an item with the given base buy price, durability and enchants (see class doc). */
	public static long price(long base, double durabilityLeft, List<Ench> enchants) {
		long pct = Math.round(durabilityLeft * 100);
		long scaled = Math.floorDiv(base * (PRICE_BASE_PCT * 100 + PRICE_DURABILITY_PCT * pct) + 5000, 10000);
		long levels = 0;
		for (Ench e : enchants) levels += e.level();
		long cap = Math.max(1, Math.floorDiv(base * MAX_PRICE_PCT, 100));
		return Math.max(1, Math.min(cap, scaled + PRICE_PER_ENCHANT_LEVEL * levels));
	}

	public static List<Offer> generate(long day, long seed, ShopConfig cfg) {
		if (!cfg.blackMarket().enabled()) return List.of();
		List<ShopConfig.Item> pool = new ArrayList<>();
		for (ShopConfig.Item it : cfg.items()) {
			if (gearType(it.id()) != null) pool.add(it);
		}
		Random rnd = new Random(seed * 0x9E3779B97F4A7C15L ^ (day * 0xD6E8FEB86659FD93L + 0x5DEECE66DL));
		Collections.shuffle(pool, rnd);
		int n = Math.min(cfg.blackMarket().slots(), pool.size());
		List<Offer> out = new ArrayList<>();
		for (int slot = 0; slot < n; slot++) {
			ShopConfig.Item it = pool.get(slot);
			double dur = (MIN_DURABILITY_PCT + rnd.nextInt(MAX_DURABILITY_PCT - MIN_DURABILITY_PCT + 1)) / 100.0;
			List<Ench> enchants = rollEnchants(gearType(it.id()), rnd);
			out.add(new Offer(slot, it.id(), dur, enchants, price(it.buy(), dur, enchants)));
		}
		return List.copyOf(out);
	}

	private static List<Ench> rollEnchants(String type, Random rnd) {
		List<Option> opts = new ArrayList<>(optionsFor(type));
		List<Ench> out = new ArrayList<>();
		if (rnd.nextInt(100) >= ENCHANTED_PCT) return out;
		int count = 1 + (rnd.nextInt(100) < SECOND_ENCHANT_PCT ? 1 : 0);
		for (int i = 0; i < count && !opts.isEmpty() && out.size() < MAX_ENCHANTS; i++) {
			Option o = pickWeighted(opts, rnd);
			opts.remove(o);
			out.add(new Ench(o.id(), 1 + rnd.nextInt(o.maxLevel())));
		}
		return out;
	}

	private static Option pickWeighted(List<Option> opts, Random rnd) {
		int total = 0;
		for (Option o : opts) total += o.rare() ? RARE_WEIGHT : COMMON_WEIGHT;
		int r = rnd.nextInt(total);
		for (Option o : opts) {
			r -= o.rare() ? RARE_WEIGHT : COMMON_WEIGHT;
			if (r < 0) return o;
		}
		return opts.get(opts.size() - 1);
	}
}
