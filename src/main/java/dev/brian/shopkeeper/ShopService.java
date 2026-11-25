package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.Daily;
import dev.brian.shopkeeper.core.Economy;
import dev.brian.shopkeeper.core.ShopConfig;
import dev.brian.shopkeeper.core.ShopConfigParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns the loaded config, the live economy and its persistence. Server thread only. */
public final class ShopService {
	private static final Logger LOG = LoggerFactory.getLogger("shopkeeper");
	private static final String DEFAULT_RESOURCE = "/shopkeeper/default_shop.json";
	private static ShopService instance;

	/** usedDefault: the user text was invalid (errors says why) and the bundled default was parsed instead. */
	record Loaded(ShopConfig config, List<String> errors, boolean usedDefault) {}

	/** Pure: parse user text, else fall back to the default text. config is null only if the default is broken too. */
	static Loaded resolveConfig(String userText, String defaultText) {
		ShopConfigParser.Result user = ShopConfigParser.parse(userText);
		if (user.ok()) return new Loaded(user.config(), List.of(), false);
		ShopConfigParser.Result def = ShopConfigParser.parse(defaultText);
		List<String> errors = new ArrayList<>(user.errors());
		if (!def.ok()) errors.addAll(def.errors().stream().map(e -> "bundled default: " + e).toList());
		return new Loaded(def.config(), List.copyOf(errors), true);
	}

	private final Path configPath;
	private final ShopData data;
	private ShopConfig config;
	private Economy economy;
	private MinecraftServer server;

	private ShopService(ShopData data, Path configPath, ShopConfig config) {
		this.data = data;
		this.configPath = configPath;
		this.config = config;
		this.economy = new Economy(config);
		this.economy.restore(data.toSnapshot());
	}

	public static ShopService get() {
		return instance;
	}

	public static void init(MinecraftServer server) {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("shopkeeper.json");
		String def = readDefault();
		String user = null;
		try {
			if (Files.notExists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, def, StandardCharsets.UTF_8);
			}
			user = Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			LOG.error("Could not read/write {}: {}", path, ex.toString());
		}
		Loaded loaded = resolveConfig(user, def);
		if (loaded.usedDefault()) {
			LOG.error("{} is invalid, using the bundled default (file left untouched):", path);
			loaded.errors().forEach(e -> LOG.error("  {}", e));
		}
		if (loaded.config() == null) throw new IllegalStateException("bundled default shop config is invalid");
		instance = new ShopService(ShopData.get(server), path, loaded.config());
		instance.server = server;
		instance.refreshDeal();
		instance.unknownItemIds().forEach(id -> LOG.warn("Config item '{}' is not a known item id", id));
	}

	public static void shutdown() {
		if (instance != null) {
			instance.save();
			instance = null;
		}
	}

	private static String readDefault() {
		try (InputStream in = ShopService.class.getResourceAsStream(DEFAULT_RESOURCE)) {
			if (in == null) throw new IllegalStateException("missing " + DEFAULT_RESOURCE);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public ShopConfig config() {
		return config;
	}

	public Economy economy() {
		return economy;
	}

	/** Recomputes today's deal from the overworld day and seed. Never persisted. */
	public void refreshDeal() {
		if (server == null) return;
		var level = server.overworld();
		refreshDeal(level.getOverworldClockTime() / 24000L, level.getSeed());
	}

	public void refreshDeal(long day, long seed) {
		economy.setDeal(Daily.pick(day, seed, config));
	}

	public void save() {
		data.capture(economy.snapshot());
		data.setDirty();
	}

	/** Empty list = success. On any error the old config and economy stay in place. */
	public List<String> reload() {
		String text;
		try {
			text = Files.readString(configPath, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			return List.of("could not read " + configPath + ": " + ex.getMessage());
		}
		ShopConfigParser.Result r = ShopConfigParser.parse(text);
		if (!r.ok()) return r.errors();
		Economy next = new Economy(r.config());
		next.restore(economy.snapshot());
		config = r.config();
		economy = next;
		refreshDeal();
		unknownItemIds().forEach(id -> LOG.warn("Config item '{}' is not a known item id", id));
		return List.of();
	}

	public List<String> unknownItemIds() {
		List<String> out = new ArrayList<>();
		for (ShopConfig.Item item : config.items()) {
			Identifier id = Identifier.tryParse(item.id());
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) out.add(item.id());
		}
		return out;
	}
}
