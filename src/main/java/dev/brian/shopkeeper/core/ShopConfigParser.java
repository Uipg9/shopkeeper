package dev.brian.shopkeeper.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses and validates shop.json text. Never throws on bad data; returns errors instead. */
public final class ShopConfigParser {
	private ShopConfigParser() {}

	/** Matches ShopRouter.TAB_COUNT. */
	private static final int MAX_TIERS = ShopRouter.TAB_COUNT;
	private static final Set<String> AIR_IDS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

	/** config is non-null only when errors is empty. */
	public record Result(ShopConfig config, List<String> errors) {
		public boolean ok() {
			return config != null;
		}
	}

	private static Result fail(List<String> errors) {
		return new Result(null, List.copyOf(errors));
	}

	public static Result parse(String json) {
		List<String> errors = new ArrayList<>();
		if (json == null || json.isBlank()) {
			errors.add("config is empty");
			return fail(errors);
		}
		JsonElement rootEl;
		try {
			rootEl = JsonParser.parseString(json);
		} catch (RuntimeException ex) {
			errors.add("invalid JSON: " + ex.getMessage());
			return fail(errors);
		}
		if (rootEl == null || !rootEl.isJsonObject()) {
			errors.add("config root must be a JSON object");
			return fail(errors);
		}
		JsonObject root = rootEl.getAsJsonObject();

		Long starting = longField(root, "startingBalance", "config", errors, true, 0L);
		if (starting != null && starting < 0) {
			errors.add("startingBalance must not be negative");
		}

		double fraction = 0;
		JsonElement fe = root.get("sellFraction");
		if (fe == null || !fe.isJsonPrimitive() || !fe.getAsJsonPrimitive().isNumber()) {
			errors.add("sellFraction must be a number");
		} else {
			fraction = fe.getAsDouble();
			if (!(fraction >= ShopConfig.MIN_SELL_FRACTION && fraction <= ShopConfig.MAX_SELL_FRACTION)) {
				errors.add("sellFraction " + fraction + " must be within 0.25-0.40");
			}
		}

		// Tiers
		List<ShopConfig.Tier> tiers = new ArrayList<>();
		Set<String> tierIds = new HashSet<>();
		JsonArray tierArr = arrayField(root, "tiers", errors);
		if (tierArr != null) {
			if (tierArr.isEmpty()) errors.add("at least one tier is required");
			if (tierArr.size() > MAX_TIERS) errors.add("at most " + MAX_TIERS + " tiers are supported (found " + tierArr.size() + ")");
			int idx = 0;
			for (JsonElement te : tierArr) {
				String where = "tiers[" + idx + "]";
				if (!te.isJsonObject()) {
					errors.add(where + " must be an object");
					idx++;
					continue;
				}
				JsonObject t = te.getAsJsonObject();
				String id = stringField(t, "id", where, errors);
				String name = stringField(t, "name", where, errors);
				String icon = stringField(t, "icon", where, errors);
				Long price = longField(t, "unlockPrice", where, errors, false, 0L);
				if (price != null && price < 0) errors.add(where + " unlockPrice must not be negative");
				if (id != null && !tierIds.add(id)) errors.add("duplicate tier id '" + id + "'");
				if (idx == 0 && price != null && price != 0) errors.add("first tier must have unlockPrice 0");
				if (id != null && name != null && icon != null && price != null) {
					tiers.add(new ShopConfig.Tier(id, name, price, icon));
				}
				idx++;
			}
		}

		// Items
		List<ShopConfig.Item> items = new ArrayList<>();
		Set<String> itemIds = new HashSet<>();
		Map<String, Integer> perTier = new HashMap<>();
		JsonArray itemArr = arrayField(root, "items", errors);
		if (itemArr != null) {
			int idx = 0;
			for (JsonElement ie : itemArr) {
				String where = "items[" + idx + "]";
				idx++;
				if (!ie.isJsonObject()) {
					errors.add(where + " must be an object");
					continue;
				}
				JsonObject o = ie.getAsJsonObject();
				String id = stringField(o, "id", where, errors);
				String tier = stringField(o, "tier", where, errors);
				Long buy = longField(o, "buy", where, errors, false, 0L);
				Long sell = longField(o, "sell", where, errors, true, null);
				Long maxStackL = longField(o, "maxStack", where, errors, true, null);
				boolean good = id != null && tier != null && buy != null;
				if (id != null && !itemIds.add(id)) {
					errors.add("duplicate item id '" + id + "'");
					good = false;
				}
				if (id != null && AIR_IDS.contains(id)) {
					errors.add(where + " item '" + id + "' is air and cannot be sold or bought");
					good = false;
				}
				if (tier != null && !tierIds.contains(tier)) {
					errors.add(where + " unknown tier '" + tier + "'");
					good = false;
				}
				if (buy != null && buy <= 0) {
					errors.add(where + " buy must be greater than 0");
					good = false;
				}
				if (sell != null && sell < 0) {
					errors.add(where + " sell must not be negative");
					good = false;
				} else if (sell != null && buy != null && sell >= buy) {
					errors.add(where + " explicit sell must be less than buy");
					good = false;
				}
				Integer maxStack = null;
				if (maxStackL != null) {
					if (maxStackL < 1 || maxStackL > 99) {
						errors.add(where + " maxStack must be between 1 and 99");
						good = false;
					} else {
						maxStack = maxStackL.intValue();
					}
				}
				if (tier != null && tierIds.contains(tier)) {
					int n = perTier.merge(tier, 1, Integer::sum);
					if (n == ShopConfig.MAX_ITEMS_PER_TIER + 1) {
						errors.add("tier '" + tier + "' has more than " + ShopConfig.MAX_ITEMS_PER_TIER + " items");
					}
				}
				if (good && errors.isEmpty()) {
					items.add(ShopConfig.makeItem(id, tier, buy, sell, maxStack, fraction));
				}
			}
		}

		ShopConfig.BlackMarketSettings bm = parseBlackMarket(root, errors);

		if (!errors.isEmpty()) return fail(errors);
		return new Result(new ShopConfig(starting, fraction, tiers, items, bm), List.of());
	}

	private static ShopConfig.BlackMarketSettings parseBlackMarket(JsonObject root, List<String> errors) {
		JsonElement e = root.get("blackMarket");
		if (e == null || e.isJsonNull()) return ShopConfig.BlackMarketSettings.DEFAULT;
		if (!e.isJsonObject()) {
			errors.add("'blackMarket' must be an object");
			return null;
		}
		JsonObject o = e.getAsJsonObject();
		boolean enabled = blackMarketFlag(o, "enabled", errors);
		boolean books = blackMarketFlag(o, "books", errors);
		boolean rare = blackMarketFlag(o, "rare", errors);
		boolean crate = blackMarketFlag(o, "crate", errors);
		int slots = ShopConfig.BlackMarketSettings.DEFAULT.slots();
		Long sl = longField(o, "slots", "blackMarket", errors, true, null);
		if (sl != null) {
			if (sl < ShopConfig.MIN_BLACK_MARKET_SLOTS || sl > ShopConfig.MAX_BLACK_MARKET_SLOTS) {
				errors.add("blackMarket 'slots' must be between " + ShopConfig.MIN_BLACK_MARKET_SLOTS + " and "
						+ ShopConfig.MAX_BLACK_MARKET_SLOTS);
			} else {
				slots = sl.intValue();
			}
		}
		return new ShopConfig.BlackMarketSettings(enabled, slots, books, rare, crate);
	}

	/** Optional boolean in the blackMarket block; absent/null means true. */
	private static boolean blackMarketFlag(JsonObject o, String key, List<String> errors) {
		JsonElement en = o.get(key);
		if (en == null || en.isJsonNull()) return true;
		if (!en.isJsonPrimitive() || !en.getAsJsonPrimitive().isBoolean()) {
			errors.add("blackMarket '" + key + "' must be true or false");
			return true;
		}
		return en.getAsBoolean();
	}

	private static JsonArray arrayField(JsonObject o, String key, List<String> errors) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonArray()) {
			errors.add("'" + key + "' must be an array");
			return null;
		}
		return e.getAsJsonArray();
	}

	private static String stringField(JsonObject o, String key, String where, List<String> errors) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isEmpty()) {
			errors.add(where + " '" + key + "' must be a non-empty string");
			return null;
		}
		return e.getAsString();
	}

	/** Reads an integer field. Absent/null yields def when allowed (nullable), otherwise an error. */
	private static Long longField(JsonObject o, String key, String where, List<String> errors, boolean nullable, Long def) {
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull()) {
			if (nullable) return def;
			errors.add(where + " '" + key + "' is required");
			return null;
		}
		if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			errors.add(where + " '" + key + "' must be an integer");
			return null;
		}
		try {
			return new BigDecimal(e.getAsString()).longValueExact();
		} catch (ArithmeticException | NumberFormatException ex) {
			errors.add(where + " '" + key + "' must be an integer that fits in 64 bits");
			return null;
		}
	}
}
