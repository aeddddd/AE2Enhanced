package com.github.aeddddd.ae2enhanced.memorycard.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;
import com.github.aeddddd.ae2enhanced.memorycard.upgrade.IUpgradeProvider;

/**
 * 通用内存卡升级槽序列化与粘贴的公共辅助方法.
 *
 * <p>架构约定:
 * 1. 所有升级操作基于 IUpgradeProvider 抽象.
 * 2. tryPullFromNetwork 返回 NetworkPullResult 三元状态,区分直接提取 / 已请求合成 / 失败.
 * 3. 网络回退经绑定的无线访问点解析网格(1.12 为绑定无线频道发射器坐标).</p>
 */
public class MemoryCardUpgradeHelper {

    public enum NetworkPullResult {
        PULLED, // 所有物品已直接提取到账
        CRAFTING_REQUESTED, // 部分或全部物品已提交合成请求(尚未到账)
        FAILED // 无法获取(既无库存也无法合成)
    }

    // ================== IUpgradeProvider API ==================

    public static ListTag serializeUpgrades(IUpgradeProvider provider) {
        ListTag list = new ListTag();
        for (int i = 0; i < provider.getSlotCount(); i++) {
            ItemStack stack = provider.getStackInSlot(i);
            if (!stack.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Slot", i);
                stack.save(tag);
                list.add(tag);
            }
        }
        return list;
    }

    public static List<ItemStack> deserializeUpgrades(ListTag list) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * 基于 IUpgradeProvider 的统一升级应用流程.
     * 1. 统一验证(含网络回退)
     * 2. 弹出旧升级(返还玩家背包)
     * 3. 消耗新升级
     * 4. 放入新升级
     */
    public static PasteResult applyUpgrades(IUpgradeProvider provider, List<ItemStack> needed, Player player) {
        if (needed.isEmpty()) {
            provider.clearSlots();
            return PasteResult.SUCCESS;
        }

        // 1. 统一验证(含网络回退)
        if (!ensureAvailable(player, needed)) {
            return PasteResult.MISSING_UPGRADES;
        }

        // 2. 弹出旧升级
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < provider.getSlotCount(); i++) {
            ItemStack old = provider.getStackInSlot(i);
            if (!old.isEmpty()) {
                removed.add(old.copy());
            }
        }
        provider.clearSlots();

        for (ItemStack old : removed) {
            if (!player.getInventory().add(old)) {
                player.drop(old, false);
            }
        }

        // 3. 消耗新升级
        for (ItemStack need : needed) {
            consumeFromInventory(player, need);
        }

        // 4. 放入新升级
        for (int i = 0; i < needed.size() && i < provider.getSlotCount(); i++) {
            provider.setStackInSlot(i, needed.get(i).copy());
        }

        return PasteResult.SUCCESS;
    }

    // ================== 网络回退 ==================

    public static NetworkPullResult tryPullFromNetwork(Player player, List<ItemStack> missing) {
        ItemStack handStack = player.getMainHandItem();
        if (!(handStack.getItem() instanceof UniversalMemoryCardItem)) {
            return NetworkPullResult.FAILED;
        }
        if (!UMCNetworkLink.isLinked(handStack)) {
            return NetworkPullResult.FAILED;
        }

        Level level = player.level();
        IGrid grid = UMCNetworkLink.getLinkedGrid(handStack, level);
        if (grid == null) {
            return NetworkPullResult.FAILED;
        }

        try {
            MEStorage inv = grid.getStorageService().getInventory();
            IActionSource source = IActionSource.ofPlayer(player);
            ICraftingService craftingService = grid.getCraftingService();

            List<ItemStack> stillMissing = new ArrayList<>();
            List<ItemStack> craftable = new ArrayList<>();
            List<ItemStack> directExtract = new ArrayList<>();

            for (ItemStack deficit : missing) {
                AEItemKey want = AEItemKey.of(deficit);
                if (want == null) {
                    stillMissing.add(deficit);
                    continue;
                }
                long available = inv.extract(want, deficit.getCount(), Actionable.SIMULATE, source);
                if (available >= deficit.getCount()) {
                    directExtract.add(deficit.copy());
                    continue;
                }

                if (available > 0) {
                    ItemStack partial = deficit.copy();
                    partial.setCount((int) available);
                    directExtract.add(partial);
                }
                int needCount = deficit.getCount() - (int) available;
                if (needCount > 0) {
                    ItemStack need = deficit.copy();
                    need.setCount(needCount);

                    if (craftingService.isCraftable(want)) {
                        craftable.add(need);
                    } else {
                        stillMissing.add(need);
                    }
                }
            }

            if (!stillMissing.isEmpty()) {
                return NetworkPullResult.FAILED;
            }

            boolean craftingRequested = false;
            if (!craftable.isEmpty()) {
                craftingRequested = requestCrafting(player, level, source, craftingService, craftable);
            }

            for (ItemStack toExtract : directExtract) {
                AEItemKey want = AEItemKey.of(toExtract);
                long extracted = inv.extract(want, toExtract.getCount(), Actionable.MODULATE, source);
                if (extracted > 0) {
                    ItemStack stack = want.toStack((int) extracted);
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            }

            if (craftingRequested) {
                player.displayClientMessage(
                        Component.translatable("gui.ae2enhanced.umc.msg.crafting_requested"), false);
                return NetworkPullResult.CRAFTING_REQUESTED;
            }

            return NetworkPullResult.PULLED;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] UMC bound access point grid not accessible", e);
            return NetworkPullResult.FAILED;
        }
    }

    private static boolean requestCrafting(Player player, Level level, IActionSource source,
            ICraftingService craftingService, List<ItemStack> toCraft) {
        boolean anyRequested = false;

        for (ItemStack stack : toCraft) {
            try {
                AEItemKey want = AEItemKey.of(stack);
                Future<ICraftingPlan> future = craftingService.beginCraftingCalculation(
                        level, () -> source, want, stack.getCount(), CalculationStrategy.CRAFT_LESS);

                // 升级卡等简单物品的合成计算通常立即完成;超时则取消本次计算
                ICraftingPlan plan = future.get(1, TimeUnit.SECONDS);
                if (plan != null && !plan.simulation()) {
                    ICraftingSubmitResult result = craftingService.submitJob(plan, null, null, false, source);
                    if (result.successful()) {
                        anyRequested = true;
                    }
                }
            } catch (java.util.concurrent.TimeoutException e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Crafting calculation timed out for {}", stack.getHoverName());
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Crafting request failed for {}", stack.getHoverName(), e);
            }
        }

        return anyRequested;
    }

    // ================== 背包操作 ==================

    public static int countInInventory(Player player, ItemStack stack) {
        int count = 0;
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty() && ItemStack.isSameItemSameTags(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        for (ItemStack invStack : player.getInventory().offhand) {
            if (!invStack.isEmpty() && ItemStack.isSameItemSameTags(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        return count;
    }

    /**
     * 确保玩家背包(含 ME 网络回退)中有足够的物品.
     * 如果网络拉取了物品,它们会被放入玩家背包.
     * @return true 表示所有物品都已在背包中可用
     */
    public static boolean ensureAvailable(Player player, List<ItemStack> needed) {
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack need : needed) {
            if (need.isEmpty()) {
                continue;
            }
            int available = countInInventory(player, need);
            if (available < need.getCount()) {
                ItemStack deficit = need.copy();
                deficit.setCount(need.getCount() - available);
                missing.add(deficit);
            }
        }
        if (missing.isEmpty()) {
            return true;
        }

        NetworkPullResult result = tryPullFromNetwork(player, missing);
        if (result == NetworkPullResult.FAILED) {
            return false;
        }

        // PULLED 或 CRAFTING_REQUESTED:再次验证库存
        for (ItemStack need : needed) {
            if (need.isEmpty()) {
                continue;
            }
            if (countInInventory(player, need) < need.getCount()) {
                return false;
            }
        }
        return true;
    }

    public static void consumeFromInventory(Player player, ItemStack stack) {
        int remaining = stack.getCount();
        var items = player.getInventory().items;
        for (int i = 0; i < items.size() && remaining > 0; i++) {
            ItemStack invStack = items.get(i);
            if (!invStack.isEmpty() && ItemStack.isSameItemSameTags(stack, invStack)) {
                int take = Math.min(remaining, invStack.getCount());
                invStack.shrink(take);
                if (invStack.isEmpty()) {
                    items.set(i, ItemStack.EMPTY);
                }
                remaining -= take;
            }
        }
        var offhand = player.getInventory().offhand;
        for (int i = 0; i < offhand.size() && remaining > 0; i++) {
            ItemStack invStack = offhand.get(i);
            if (!invStack.isEmpty() && ItemStack.isSameItemSameTags(stack, invStack)) {
                int take = Math.min(remaining, invStack.getCount());
                invStack.shrink(take);
                if (invStack.isEmpty()) {
                    offhand.set(i, ItemStack.EMPTY);
                }
                remaining -= take;
            }
        }
    }
}
