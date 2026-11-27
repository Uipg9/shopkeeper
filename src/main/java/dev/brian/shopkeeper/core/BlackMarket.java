package dev.brian.shopkeeper.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure daily "black market": cheap used vanilla gear with low-to-medium durability and a few modest enchants.
 * Deterministic from (day, seed) and the config only. Only items already in the config are offered as gear.
 *
 * <p>Price = clamp(round(base * (0.15 + 0.35 * durabilityLeft)) + 10 * sumOfEnchantLevels, 1, floor(base * 0.7)),
 * computed in integer math.
 *
 * <p>After the gear offers come fixed daily extras (each toggleable in the config): {@link #BOOKS_PER_DAY} enchanted
 * books, {@link #RARE_PER_DAY} rare goods and {@link #CRATES_PER_DAY} "Mystery Bundle". These use their own Randoms
 * (salted from day/seed), so the gear sequence is unchanged and toggling one kind does not reshuffle another.
 * Exactly one offer per day is "lucky": half price (min 1). Slots are contiguous from 0.
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

	public enum Kind { GEAR, BOOK, RARE, CRATE }

	/** One stack inside a crate. */
	public record Good(String itemId, int count) {}

	/**
	 * One daily offer. durabilityLeft is 1.0 for undamaged goods; enchants hold the stored enchant for BOOK;
	 * count is the stack size (RARE can be more than 1); contents are the bundle stacks for CRATE (empty otherwise).
	 */
	public record Offer(int slot, String itemId, double durabilityLeft, List<Ench> enchants, long price, Kind kind,
			int count, boolean lucky, List<Good> contents) {
		public Offer {
			enchants = List.copyOf(enchants);
			contents = List.copyOf(contents);
			if (kind == null) kind = Kind.GEAR;
			if (count < 1) count = 1;
		}

		/** A plain, non-lucky gear offer. */
		public Offer(int slot, String itemId, double durabilityLeft, List<Ench> enchants, long price) {
			this(slot, itemId, durabilityLeft, enchants, price, Kind.GEAR, 1, false, List.of());
		}

		Offer asLucky() {
			return new Offer(slot, itemId, durabilityLeft, enchants, Math.max(1, price / 2), kind, count, true, contents);
		}
	}

	public static final int BOOKS_PER_DAY = 3;
	public static final int RARE_PER_DAY = 3;
	public static final int CRATES_PER_DAY = 1;
	/** Percent chance per book roll that it is Mending (at most one per day). */
	static final int MENDING_PCT = 10;
	static final String MENDING = "minecraft:mending";
	static final long MENDING_PRICE = 3000;
	static final String BOOK_ID = "minecraft:enchanted_book";
	static final String CRATE_ID = "minecraft:bundle";
	static final long CRATE_PRICE = 650;
	/** Crate items in total; all crate goods stack to 64, so this stays under the bundle's weight limit of 64. */
	static final int CRATE_MAX_ITEMS = 48;
	static final int CRATE_MIN_STACKS = 3;
	static final int CRATE_MAX_STACKS = 5;

	/** Book enchant: vanilla max level (books roll 1..max-1, min 1) and price per level. */
	record BookOption(String id, int maxLevel, long pricePerLevel) {
		int cap() {
			return Math.max(1, maxLevel - 1);
		}
	}

	static final List<BookOption> BOOK_OPTIONS = List.of(
			new BookOption("minecraft:efficiency", 5, 200),
			new BookOption("minecraft:unbreaking", 3, 220),
			new BookOption("minecraft:protection", 4, 220),
			new BookOption("minecraft:sharpness", 5, 200),
			new BookOption("minecraft:power", 5, 180),
			new BookOption("minecraft:feather_falling", 4, 180),
			new BookOption("minecraft:fortune", 3, 450),
			new BookOption("minecraft:looting", 3, 400),
			new BookOption("minecraft:silk_touch", 1, 600),
			new BookOption("minecraft:respiration", 3, 150),
			new BookOption("minecraft:depth_strider", 3, 180),
			new BookOption("minecraft:aqua_affinity", 1, 250),
			new BookOption("minecraft:smite", 5, 120),
			new BookOption("minecraft:fire_aspect", 2, 300),
			new BookOption("minecraft:quick_charge", 3, 150),
			new BookOption("minecraft:lure", 3, 130),
			new BookOption("minecraft:luck_of_the_sea", 3, 150));

	/**
	 * Rare good: one of ids (uniform), pick weight, price per item and count range. damagedBase above 0 means it is
	 * sold used (gear durability range) and priced with {@link #price} from that base instead.
	 */
	record Rare(List<String> ids, int weight, long unitPrice, int minCount, int maxCount, long damagedBase) {
		Rare(String id, int weight, long unitPrice) {
			this(List.of(id), weight, unitPrice, 1, 1, 0);
		}
	}

	static final List<Rare> RARE_GOODS = List.of(
			new Rare("minecraft:heart_of_the_sea", 5, 4800),
			new Rare(List.of("minecraft:nautilus_shell"), 10, 350, 2, 4, 0),
			new Rare(List.of("minecraft:music_disc_13", "minecraft:music_disc_cat", "minecraft:music_disc_blocks",
					"minecraft:music_disc_chirp", "minecraft:music_disc_far", "minecraft:music_disc_mall",
					"minecraft:music_disc_mellohi", "minecraft:music_disc_stal", "minecraft:music_disc_strad",
					"minecraft:music_disc_ward", "minecraft:music_disc_11", "minecraft:music_disc_wait"), 10, 350, 1, 1, 0),
			new Rare(List.of("minecraft:music_disc_pigstep", "minecraft:music_disc_otherside", "minecraft:music_disc_relic",
					"minecraft:music_disc_precipice", "minecraft:music_disc_creator"), 3, 1800, 1, 1, 0),
			new Rare("minecraft:name_tag", 10, 150),
			new Rare("minecraft:saddle", 8, 180),
			new Rare("minecraft:iron_horse_armor", 6, 240),
			new Rare("minecraft:golden_horse_armor", 5, 360),
			new Rare("minecraft:diamond_horse_armor", 3, 900),
			new Rare("minecraft:trial_key", 8, 180),
			new Rare("minecraft:ominous_trial_key", 4, 1200),
			new Rare("minecraft:totem_of_undying", 2, 7200),
			new Rare(List.of("minecraft:trident"), 3, 0, 1, 1, 15000),
			new Rare(List.of("minecraft:netherite_sword", "minecraft:netherite_pickaxe", "minecraft:netherite_axe",
					"minecraft:netherite_shovel", "minecraft:netherite_helmet", "minecraft:netherite_chestplate",
					"minecraft:netherite_leggings", "minecraft:netherite_boots"), 1, 0, 1, 1, 45000));

	/** Crate good: id, pick weight, count range, and its rough shop value per item. All stack to 64. */
	record CrateGood(String id, int weight, int minCount, int maxCount, long value) {}

	static final List<CrateGood> CRATE_GOODS = List.of(
			new CrateGood("minecraft:iron_ingot", 10, 4, 10, 30),
			new CrateGood("minecraft:gold_ingot", 8, 2, 5, 85),
			new CrateGood("minecraft:emerald", 6, 1, 4, 150),
			new CrateGood("minecraft:redstone", 8, 8, 14, 14),
			new CrateGood("minecraft:lapis_lazuli", 8, 6, 12, 16),
			new CrateGood("minecraft:bread", 8, 4, 8, 28),
			new CrateGood("minecraft:cooked_beef", 8, 4, 8, 24),
			new CrateGood("minecraft:golden_carrot", 5, 2, 5, 100),
			new CrateGood("minecraft:arrow", 8, 8, 16, 4),
			new CrateGood("minecraft:experience_bottle", 4, 1, 3, 300),
			new CrateGood("minecraft:coal", 8, 8, 12, 12),
			new CrateGood("minecraft:quartz", 6, 4, 10, 40),
			new CrateGood("minecraft:amethyst_shard", 5, 4, 8, 20),
			new CrateGood("minecraft:copper_ingot", 6, 8, 12, 10),
			new CrateGood("minecraft:diamond", 2, 1, 2, 400));

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
			double dur = rollDurability(rnd);
			List<Ench> enchants = rollEnchants(gearType(it.id()), rnd);
			out.add(new Offer(slot, it.id(), dur, enchants, price(it.buy(), dur, enchants)));
		}
		ShopConfig.BlackMarketSettings bm = cfg.blackMarket();
		if (bm.books()) addBooks(out, salted(day, seed, 1));
		if (bm.rare()) addRare(out, salted(day, seed, 2));
		if (bm.crate()) addCrate(out, salted(day, seed, 3));
		if (!out.isEmpty()) {
			int lucky = salted(day, seed, 4).nextInt(out.size());
			out.set(lucky, out.get(lucky).asLucky());
		}
		return List.copyOf(out);
	}

	/** Independent Random per extra category, so the gear sequence and the other kinds are unaffected. */
	private static Random salted(long day, long seed, long salt) {
		return new Random((seed + salt * 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL
				^ (day * 0x165667B19E3779F9L + salt * 0x27D4EB2F165667C5L));
	}

	private static double rollDurability(Random rnd) {
		return (MIN_DURABILITY_PCT + rnd.nextInt(MAX_DURABILITY_PCT - MIN_DURABILITY_PCT + 1)) / 100.0;
	}

	private static void addBooks(List<Offer> out, Random rnd) {
		List<BookOption> opts = new ArrayList<>(BOOK_OPTIONS);
		boolean mending = false;
		for (int i = 0; i < BOOKS_PER_DAY; i++) {
			if (!mending && rnd.nextInt(100) < MENDING_PCT) {
				mending = true;
				out.add(new Offer(out.size(), BOOK_ID, 1.0, List.of(new Ench(MENDING, 1)), MENDING_PRICE, Kind.BOOK, 1,
						false, List.of()));
				continue;
			}
			BookOption o = opts.remove(rnd.nextInt(opts.size()));
			int level = 1 + rnd.nextInt(o.cap());
			out.add(new Offer(out.size(), BOOK_ID, 1.0, List.of(new Ench(o.id(), level)), o.pricePerLevel() * level,
					Kind.BOOK, 1, false, List.of()));
		}
	}

	private static void addRare(List<Offer> out, Random rnd) {
		List<Rare> opts = new ArrayList<>(RARE_GOODS);
		for (int i = 0; i < RARE_PER_DAY && !opts.isEmpty(); i++) {
			int total = 0;
			for (Rare r : opts) total += r.weight();
			int roll = rnd.nextInt(total);
			Rare pick = opts.get(opts.size() - 1);
			for (Rare r : opts) {
				roll -= r.weight();
				if (roll < 0) {
					pick = r;
					break;
				}
			}
			opts.remove(pick);
			String id = pick.ids().get(rnd.nextInt(pick.ids().size()));
			int count = pick.minCount() + rnd.nextInt(pick.maxCount() - pick.minCount() + 1);
			double dur = 1.0;
			long price;
			if (pick.damagedBase() > 0) {
				dur = rollDurability(rnd);
				price = price(pick.damagedBase(), dur, List.of());
			} else {
				price = pick.unitPrice() * count;
			}
			out.add(new Offer(out.size(), id, dur, List.of(), price, Kind.RARE, count, false, List.of()));
		}
	}

	private static void addCrate(List<Offer> out, Random rnd) {
		for (int c = 0; c < CRATES_PER_DAY; c++) {
			List<CrateGood> opts = new ArrayList<>(CRATE_GOODS);
			int stacks = CRATE_MIN_STACKS + rnd.nextInt(CRATE_MAX_STACKS - CRATE_MIN_STACKS + 1);
			int left = CRATE_MAX_ITEMS;
			List<Good> goods = new ArrayList<>();
			for (int i = 0; i < stacks && !opts.isEmpty() && left > 0; i++) {
				int total = 0;
				for (CrateGood g : opts) total += g.weight();
				int roll = rnd.nextInt(total);
				CrateGood pick = opts.get(opts.size() - 1);
				for (CrateGood g : opts) {
					roll -= g.weight();
					if (roll < 0) {
						pick = g;
						break;
					}
				}
				opts.remove(pick);
				int count = Math.min(left, pick.minCount() + rnd.nextInt(pick.maxCount() - pick.minCount() + 1));
				left -= count;
				goods.add(new Good(pick.id(), count));
			}
			out.add(new Offer(out.size(), CRATE_ID, 1.0, List.of(), CRATE_PRICE, Kind.CRATE, 1, false, goods));
		}
	}

	/** Rough shop value of a crate's contents (for balancing and tests). */
	static long crateValue(Offer offer) {
		long v = 0;
		for (Good g : offer.contents()) {
			for (CrateGood c : CRATE_GOODS) if (c.id().equals(g.itemId())) v += c.value() * g.count();
		}
		return v;
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
