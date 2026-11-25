package dev.brian.shopkeeper.core;

import java.util.function.UnaryOperator;

/** Pure formatter for the daily report chat line. */
public final class ReportFormat {
	private ReportFormat() {}

	/** Uses the raw item id with namespace stripped and underscores as spaces. */
	public static String format(DayStats s) {
		return format(s, ReportFormat::plainName);
	}

	public static String format(DayStats s, UnaryOperator<String> itemName) {
		long net = s.earned() - s.spent();
		StringBuilder sb = new StringBuilder("Shop report, day ").append(s.day())
			.append(": earned ").append(s.earned())
			.append(", spent ").append(s.spent())
			.append(", net ").append(net > 0 ? "+" : "").append(net)
			.append(" (sold ").append(s.itemsSold()).append(s.itemsSold() == 1 ? " item" : " items")
			.append(", bought ").append(s.itemsBought()).append(").");
		if (s.bestSaleCoins() > 0 && !s.bestSaleItemId().isEmpty()) {
			sb.append(" Best sale: ").append(s.bestSaleCoins()).append(s.bestSaleCoins() == 1 ? " coin" : " coins")
				.append(" for ").append(itemName.apply(s.bestSaleItemId())).append('.');
		}
		return sb.append(' ').append(verdict(net)).toString();
	}

	public static String verdict(long net) {
		if (net >= 200) return "Great day!";
		if (net > 0) return "Decent day.";
		if (net == 0) return "Quiet day.";
		return "Rough day - you spent more than you earned.";
	}

	static String plainName(String id) {
		int c = id.indexOf(':');
		return (c >= 0 ? id.substring(c + 1) : id).replace('_', ' ');
	}
}
