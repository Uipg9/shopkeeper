package dev.brian.shopkeeper.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReportFormatTest {
	private static DayStats s(long earned, long spent, int sold, int bought, long best, String id) {
		return new DayStats(12, earned, spent, sold, bought, best, id);
	}

	@Test
	void fullExample() {
		assertEquals("Shop report, day 12: earned 340, spent 120, net +220 (sold 31 items, bought 6). Best sale: 90 coins for diamond. Great day!",
			ReportFormat.format(s(340, 120, 31, 6, 90, "minecraft:diamond")));
	}

	@Test
	void verdictBranches() {
		assertTrue(ReportFormat.format(s(200, 0, 1, 0, 5, "a:b")).endsWith("Great day!"));
		assertTrue(ReportFormat.format(s(199, 0, 1, 0, 5, "a:b")).endsWith("Decent day."));
		assertTrue(ReportFormat.format(s(1, 0, 1, 0, 1, "a:b")).endsWith("Decent day."));
		assertTrue(ReportFormat.format(s(0, 0, 0, 0, 0, "")).endsWith("Quiet day."));
		assertTrue(ReportFormat.format(s(5, 5, 1, 1, 5, "a:b")).endsWith("Quiet day."));
		assertTrue(ReportFormat.format(s(0, 30, 0, 2, 0, "")).endsWith("Rough day - you spent more than you earned."));
		assertTrue(ReportFormat.format(s(0, 30, 0, 2, 0, "")).contains("net -30"));
	}

	@Test
	void singularAndPlural() {
		assertTrue(ReportFormat.format(s(1, 0, 1, 0, 1, "a:b")).contains("sold 1 item,"));
		assertTrue(ReportFormat.format(s(1, 0, 1, 0, 1, "a:b")).contains("1 coin for"));
		assertTrue(ReportFormat.format(s(2, 0, 2, 0, 2, "a:b")).contains("sold 2 items,"));
		assertTrue(ReportFormat.format(s(0, 0, 0, 0, 0, "")).contains("sold 0 items,"));
	}

	@Test
	void missingBestSaleOmitted() {
		assertFalse(ReportFormat.format(s(0, 10, 0, 1, 0, "")).contains("Best sale"));
		assertFalse(ReportFormat.format(s(10, 0, 1, 0, 10, "")).contains("Best sale"));
	}

	@Test
	void nameFunctionUsed() {
		assertTrue(ReportFormat.format(s(10, 0, 1, 0, 10, "minecraft:oak_log"), id -> "Oak Log").contains("for Oak Log."));
		assertTrue(ReportFormat.format(s(10, 0, 1, 0, 10, "minecraft:oak_log")).contains("for oak log."));
	}
}
