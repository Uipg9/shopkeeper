package dev.brian.shopkeeper.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure item lookup for /buy: maps free text onto a config item id. */
public final class ItemQuery {
	private ItemQuery() {}

	private static final int MAX_CANDIDATES = 5;
	private static final String NS = "minecraft:";

	public record Result(boolean ok, String id, String error, List<String> candidates) {
		static Result found(String id) { return new Result(true, id, null, List.of()); }
		static Result fail(String error, List<String> candidates) { return new Result(false, null, error, List.copyOf(candidates)); }
	}

	/** "minecraft:diamond" -> "diamond"; other namespaces are kept. */
	public static String shortId(String id) {
		return id.startsWith(NS) ? id.substring(NS.length()) : id;
	}

	public static String normalize(String text) {
		return text.trim().toLowerCase(Locale.ROOT).replaceAll("[\s-]+", "_");
	}

	public static Result resolve(String query, Iterable<String> ids) {
		String shown = query == null ? "" : query.trim();
		String q = query == null ? "" : normalize(query);
		if (q.isEmpty() || q.equals("minecraft:")) return Result.fail("No shop item matching " + shown, List.of());
		List<String> all = new ArrayList<>();
		for (String id : ids) all.add(id);

		for (String id : all) if (id.equals(q)) return Result.found(id);
		for (String id : all) if (shortId(id).equals(q)) return Result.found(id);
		if (q.startsWith(NS)) q = q.substring(NS.length());

		List<String> prefix = new ArrayList<>();
		List<String> contains = new ArrayList<>();
		for (String id : all) {
			String s = shortId(id);
			if (s.startsWith(q)) prefix.add(id);
			else if (s.contains(q)) contains.add(id);
		}
		if (prefix.size() == 1) return Result.found(prefix.get(0));
		if (prefix.size() > 1) return ambiguous(shown, prefix);
		if (contains.size() == 1) return Result.found(contains.get(0));
		if (contains.size() > 1) return ambiguous(shown, contains);
		return Result.fail("No shop item matching " + shown, List.of());
	}

	private static Result ambiguous(String shown, List<String> matches) {
		List<String> top = new ArrayList<>();
		for (String id : matches) {
			if (top.size() == MAX_CANDIDATES) break;
			top.add(shortId(id));
		}
		String more = matches.size() > top.size() ? ", ..." : "";
		return Result.fail("'" + shown + "' is ambiguous: " + String.join(", ", top) + more, top);
	}
}
