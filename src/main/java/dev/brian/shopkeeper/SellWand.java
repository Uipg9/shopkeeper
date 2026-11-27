package dev.brian.shopkeeper;

import dev.brian.shopkeeper.core.Economy;
import dev.brian.shopkeeper.core.ShopConfig;
import dev.brian.shopkeeper.core.WandTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Sell Wand: a vanilla blaze rod carrying wand data in CUSTOM_DATA (no new item is registered).
 * Right-clicking a container block sells everything sellable inside at the wand's rate.
 */
public final class SellWand {
	private SellWand() {}

	static final String KEY = "shopkeeper_wand";
	private static final int COOLDOWN_TICKS = 10;
	private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();

	public record Data(int level, int uses, long earned) {}

	public static void register() {
		UseBlockCallback.EVENT.register(SellWand::onUseBlock);
	}

	// ---- item data ----

	public static boolean isWand(ItemStack stack) {
		if (stack.isEmpty() || !stack.is(Items.BLAZE_ROD)) return false;
		CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
		return cd != null && cd.copyTag().getCompound(KEY).isPresent();
	}

	public static Data read(ItemStack stack) {
		CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompoundOrEmpty(KEY);
		int level = Math.max(1, Math.min(WandTier.MAX_LEVEL, t.getIntOr("tier", 1)));
		return new Data(level, t.getIntOr("uses", 0), t.getLongOr("earned", 0));
	}

	/** Writes wand data plus the name, glint and lore that show it. */
	public static void write(ItemStack stack, Data d) {
		CompoundTag t = new CompoundTag();
		t.putInt("tier", d.level());
		t.putInt("uses", d.uses());
		t.putLong("earned", d.earned());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(KEY, t));
		WandTier.Tier tier = WandTier.of(d.level());
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Sell Wand " + tier.name())
			.withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GOLD)));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.LORE, new ItemLore(lore(d)));
	}

	static List<Component> lore(Data d) {
		WandTier.Tier tier = WandTier.of(d.level());
		List<Component> out = new ArrayList<>();
		out.add(line("Right-click a chest to sell its contents", ChatFormatting.GRAY));
		out.add(line("Sell rate: " + tier.ratePct() + "%", ChatFormatting.YELLOW));
		out.add(line("Uses: " + usesText(d), ChatFormatting.YELLOW));
		out.add(line("Earned: " + d.earned() + " coins", ChatFormatting.GRAY));
		out.add(line("Upgrade in /shop > Sell Wand", ChatFormatting.DARK_GRAY));
		return out;
	}

	static String usesText(Data d) {
		WandTier.Tier tier = WandTier.of(d.level());
		return tier.unlimited() ? "unlimited" : d.uses() + " / " + tier.uses();
	}

	public static ItemStack create(int level) {
		ItemStack stack = new ItemStack(Items.BLAZE_ROD);
		WandTier.Tier tier = WandTier.of(level);
		write(stack, new Data(tier.level(), tier.unlimited() ? 0 : tier.uses(), 0));
		return stack;
	}

	/** The player's wand: main hand, then offhand, then the rest of the inventory; EMPTY if none. */
	public static ItemStack find(Player player) {
		if (isWand(player.getMainHandItem())) return player.getMainHandItem();
		if (isWand(player.getOffhandItem())) return player.getOffhandItem();
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (isWand(s)) return s;
		}
		return ItemStack.EMPTY;
	}

	private static Component line(String s, ChatFormatting color) {
		return Component.literal(s).withStyle(Style.EMPTY.withItalic(false).withColor(color));
	}

	// ---- shop actions (wand tab / commands); return null on success or an error message ----

	public static String buy(ShopService svc, ServerPlayer p) {
		if (!find(p).isEmpty()) return "You already have a Sell Wand.";
		Economy eco = svc.economy();
		eco.ensurePlayer(p.getUUID());
		long cost = WandTier.of(1).cost();
		if (eco.balance(p.getUUID()) < cost) return "Not enough coins (" + cost + " needed).";
		Economy.Result r = eco.spend(p.getUUID(), cost);
		if (!r.ok()) return "Not enough coins.";
		ItemStack wand = create(1);
		if (!p.getInventory().add(wand)) p.drop(wand, false, net.minecraft.util.Prediction.SERVER_ONLY);
		svc.save();
		return null;
	}

	public static String upgrade(ShopService svc, ServerPlayer p) {
		ItemStack wand = find(p);
		if (wand.isEmpty()) return "You don't have a Sell Wand.";
		Data d = read(wand);
		var next = WandTier.next(d.level());
		if (next.isEmpty()) return "Your wand is already max tier.";
		Economy.Result r = svc.economy().spend(p.getUUID(), next.get().cost());
		if (!r.ok()) return "Not enough coins (" + next.get().cost() + " needed).";
		WandTier.Tier t = next.get();
		write(wand, new Data(t.level(), t.unlimited() ? 0 : t.uses(), d.earned()));
		svc.save();
		return null;
	}

	public static String recharge(ShopService svc, ServerPlayer p) {
		ItemStack wand = find(p);
		if (wand.isEmpty()) return "You don't have a Sell Wand.";
		Data d = read(wand);
		WandTier.Tier t = WandTier.of(d.level());
		if (t.unlimited()) return "Your wand never runs out.";
		if (d.uses() >= t.uses()) return "Your wand is already full.";
		Economy.Result r = svc.economy().spend(p.getUUID(), t.rechargeCost());
		if (!r.ok()) return "Not enough coins (" + t.rechargeCost() + " needed).";
		write(wand, new Data(d.level(), t.uses(), d.earned()));
		svc.save();
		return null;
	}

	// ---- using it ----

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!isWand(held) || player.isSpectator()) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof Container)) return InteractionResult.PASS;
		// Client: claim the click so the chest does not open; the server does the selling.
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		try {
			sellContainer(sp, level, pos, be, held);
		} catch (RuntimeException ex) {
			Shopkeeper.LOGGER.error("Sell Wand failed", ex);
		}
		return InteractionResult.SUCCESS;
	}

	private static void sellContainer(ServerPlayer p, Level level, BlockPos pos, BlockEntity be, ItemStack wand) {
		long now = level.getGameTime();
		Long last = LAST_USE.get(p.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS && now >= last) return;
		LAST_USE.put(p.getUUID(), now);

		ShopService svc = ShopService.get();
		if (svc == null) {
			fail(p, "The shop is not ready.");
			return;
		}
		svc.refreshDeal();
		Economy eco = svc.economy();
		eco.ensurePlayer(p.getUUID());
		Data d = read(wand);
		WandTier.Tier tier = WandTier.of(d.level());
		if (!tier.unlimited() && d.uses() <= 0) {
			fail(p, "Your Sell Wand is out of uses - recharge it in /shop > Sell Wand.");
			return;
		}
		Container container = (Container) be;
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock chest) {
			Container both = ChestBlock.getContainer(chest, state, level, pos, true);
			if (both != null) container = both;
		}
		long coins = 0;
		long items = 0;
		boolean locked = false;
		ShopConfig cfg = svc.config();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (!ShopSell.isPlain(s)) continue;
			ShopConfig.Item it = cfg.item(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
			if (it == null || !it.sellable()) continue;
			Economy.Result r = eco.sell(p.getUUID(), it.id(), s.getCount(), tier.ratePct());
			if (!r.ok()) {
				if ("tier locked".equals(r.error())) locked = true;
				continue;
			}
			s.shrink(r.removed());
			coins += r.credited();
			items += r.removed();
		}
		if (items == 0) {
			fail(p, locked ? "Nothing sellable - some items need a locked tier." : "Nothing in there the shop buys.");
			return;
		}
		container.setChanged();
		write(wand, new Data(d.level(), tier.unlimited() ? 0 : d.uses() - 1, d.earned() + coins));
		svc.save();
		p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
		String usesLeft = tier.unlimited() ? "" : " (" + (d.uses() - 1) + " uses left)";
		p.sendSystemMessage(Component.literal("Sold " + items + " items for " + coins + " coins" + usesLeft)
			.withStyle(ChatFormatting.GOLD), true);
	}

	private static void fail(ServerPlayer p, String msg) {
		p.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 1.0f);
		p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.RED), true);
	}
}
