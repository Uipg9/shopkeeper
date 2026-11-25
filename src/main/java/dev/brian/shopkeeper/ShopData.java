package dev.brian.shopkeeper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.brian.shopkeeper.core.DayStats;
import dev.brian.shopkeeper.core.Economy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent per-player shop state (keyed by UUID string). */
public class ShopData extends SavedData {
	static final Codec<DayStats> STATS_CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.LONG.optionalFieldOf("day", 0L).forGetter(DayStats::day),
		Codec.LONG.optionalFieldOf("earned", 0L).forGetter(DayStats::earned),
		Codec.LONG.optionalFieldOf("spent", 0L).forGetter(DayStats::spent),
		Codec.INT.optionalFieldOf("itemsSold", 0).forGetter(DayStats::itemsSold),
		Codec.INT.optionalFieldOf("itemsBought", 0).forGetter(DayStats::itemsBought),
		Codec.LONG.optionalFieldOf("bestSaleCoins", 0L).forGetter(DayStats::bestSaleCoins),
		Codec.STRING.optionalFieldOf("bestSaleItemId", "").forGetter(DayStats::bestSaleItemId)
	).apply(i, DayStats::new));

	public record Entry(long balance, List<String> unlockedTiers, boolean grantedStarting, Optional<DayStats> stats,
			Optional<DayStats> pendingReport, long bmDay, List<Integer> bmSlots) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("balance", 0L).forGetter(Entry::balance),
			Codec.STRING.listOf().optionalFieldOf("unlockedTiers", List.of()).forGetter(Entry::unlockedTiers),
			Codec.BOOL.optionalFieldOf("grantedStarting", false).forGetter(Entry::grantedStarting),
			STATS_CODEC.optionalFieldOf("stats").forGetter(Entry::stats),
			STATS_CODEC.optionalFieldOf("pendingReport").forGetter(Entry::pendingReport),
			Codec.LONG.optionalFieldOf("bmDay", 0L).forGetter(Entry::bmDay),
			Codec.INT.listOf().optionalFieldOf("bmSlots", List.of()).forGetter(Entry::bmSlots)
		).apply(i, Entry::new));
	}

	public Map<String, Entry> players = new HashMap<>();

	public static final Codec<ShopData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("players", Map.of()).forGetter(d -> d.players)
	).apply(i, m -> {
		ShopData d = new ShopData();
		d.players = new HashMap<>(m);
		return d;
	}));

	// dataFixType must be non-null (see docs/spike-findings.md).
	public static final SavedDataType<ShopData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath("shopkeeper", "shop"),
		ShopData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public static ShopData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Replaces stored state with the economy snapshot. Does not mark dirty. */
	public void capture(Map<UUID, Economy.PlayerSnapshot> snap) {
		Map<String, Entry> out = new HashMap<>();
		snap.forEach((k, s) -> out.put(k.toString(),
			new Entry(Math.max(0, s.balance()), new ArrayList<>(s.unlockedTiers()), s.grantedStarting(),
				Optional.ofNullable(s.stats()), Optional.ofNullable(s.pendingReport()), s.blackMarketDay(),
				new ArrayList<>(s.blackMarketSlots()))));
		players = out;
	}

	/** Stored state as an economy snapshot. Bad UUID keys are skipped; negative balances clamp to 0. */
	public Map<UUID, Economy.PlayerSnapshot> toSnapshot() {
		Map<UUID, Economy.PlayerSnapshot> out = new HashMap<>();
		players.forEach((k, e) -> {
			UUID id;
			try {
				id = UUID.fromString(k);
			} catch (IllegalArgumentException ex) {
				return;
			}
			out.put(id, new Economy.PlayerSnapshot(Math.max(0, e.balance()), new HashSet<>(e.unlockedTiers()), e.grantedStarting(),
				e.stats().orElse(null), e.pendingReport().orElse(null), e.bmDay(),
				e.bmSlots().stream().filter(x -> x >= 0).collect(Collectors.toSet())));
		});
		return out;
	}
}
