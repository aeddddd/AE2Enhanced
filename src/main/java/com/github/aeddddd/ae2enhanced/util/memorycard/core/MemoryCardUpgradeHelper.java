package com.github.aeddddd.ae2enhanced.util.memorycard.core;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemHandlerUpgradeAdapter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.PlayerSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import com.google.common.collect.ImmutableSet;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 通用内存卡升级槽序列化与粘贴的公共辅助方法.
 *
 * 架构约定：
 * 1. 所有升级操作基于 IUpgradeProvider 抽象,不再直接依赖 IItemHandler.
 * 2. IItemHandler 的兼容通过 ItemHandlerUpgradeAdapter 桥接.
 * 3. tryPullFromNetwork 返回 NetworkPullResult 三元状态,区分直接提取 / 已请求合成 / 失败.
 */
public class MemoryCardUpgradeHelper {

    public enum NetworkPullResult {
        PULLED,              // 所有物品已直接提取到账
        CRAFTING_REQUESTED,  // 部分或全部物品已提交合成请求(尚未到账)
        FAILED               // 无法获取(既无库存也无法合成)
    }

    // ================== IUpgradeProvider API ==================

    public static NBTTagList serializeUpgrades(IUpgradeProvider provider) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < provider.getSlotCount(); i++) {
            ItemStack stack = provider.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Slot", i);
                stack.writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        return list;
    }

    public static List<ItemStack> deserializeUpgrades(NBTTagList list) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
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
    public static PasteResult applyUpgrades(IUpgradeProvider provider, List<ItemStack> needed, EntityPlayer player) {
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
            if (!player.addItemStackToInventory(old)) {
                player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, old));
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

    // ================== IItemHandler 兼容层 ==================

    public static NBTTagList serializeUpgrades(IItemHandler upgrades) {
        return serializeUpgrades(new ItemHandlerUpgradeAdapter(upgrades));
    }

    public static PasteResult applyUpgrades(IItemHandler upgrades, NBTTagList list, EntityPlayer player) {
        return applyUpgrades(new ItemHandlerUpgradeAdapter(upgrades), deserializeUpgrades(list), player);
    }

    // ================== 网络回退 ==================

    public static NetworkPullResult tryPullFromNetwork(EntityPlayer player, List<ItemStack> missing) {
        ItemStack handStack = player.getHeldItemMainhand();
        if (!(handStack.getItem() instanceof ItemUniversalMemoryCard)) return NetworkPullResult.FAILED;
        if (!ItemUniversalMemoryCard.hasBinding(handStack)) return NetworkPullResult.FAILED;

        NBTTagCompound binding = ItemUniversalMemoryCard.getBinding(handStack);
        BlockPos pos = BlockPos.fromLong(binding.getLong("pos"));
        int dim = binding.getInteger("dim");

        World world = player.getEntityWorld();
        if (world.provider.getDimension() != dim) return NetworkPullResult.FAILED;
        if (!world.isBlockLoaded(pos)) return NetworkPullResult.FAILED;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileWirelessChannelTransmitter)) return NetworkPullResult.FAILED;
        TileWirelessChannelTransmitter transmitter = (TileWirelessChannelTransmitter) te;

        try {
            appeng.api.networking.IGrid grid = transmitter.getProxy().getGrid();
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return NetworkPullResult.FAILED;
            IMEMonitor<IAEItemStack> inv = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            PlayerSource source = new PlayerSource(player, null);
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);

            List<ItemStack> stillMissing = new ArrayList<>();
            List<ItemStack> craftable = new ArrayList<>();
            List<ItemStack> directExtract = new ArrayList<>();

            for (ItemStack deficit : missing) {
                AEItemStack want = AEItemStack.fromItemStack(deficit);
                IAEItemStack sim = inv.extractItems(want, Actionable.SIMULATE, source);
                if (sim != null && sim.getStackSize() >= deficit.getCount()) {
                    directExtract.add(deficit.copy());
                    continue;
                }

                long available = sim != null ? sim.getStackSize() : 0;
                if (available > 0) {
                    ItemStack partial = deficit.copy();
                    partial.setCount((int) available);
                    directExtract.add(partial);
                }
                int needCount = deficit.getCount() - (int) available;
                if (needCount > 0) {
                    ItemStack need = deficit.copy();
                    need.setCount(needCount);

                    if (craftingGrid != null && craftingGrid.canEmitFor(AEItemStack.fromItemStack(need))) {
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
            if (!craftable.isEmpty() && craftingGrid != null) {
                craftingRequested = requestCrafting(player, world, grid, source, craftingGrid, craftable, transmitter);
            }

            for (ItemStack toExtract : directExtract) {
                AEItemStack want = AEItemStack.fromItemStack(toExtract);
                IAEItemStack extracted = inv.extractItems(want, Actionable.MODULATE, source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    ItemStack stack = extracted.createItemStack();
                    if (!player.addItemStackToInventory(stack)) {
                        player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                    }
                }
            }

            if (craftingRequested) {
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.crafting_requested"));
                return NetworkPullResult.CRAFTING_REQUESTED;
            }

            return NetworkPullResult.PULLED;
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.debug("[AE2E] UMC bound transmitter grid not accessible at {}", pos);
            return NetworkPullResult.FAILED;
        }
    }

    private static boolean requestCrafting(EntityPlayer player, World world, appeng.api.networking.IGrid grid,
                                           PlayerSource source, ICraftingGrid craftingGrid,
                                           List<ItemStack> toCraft, TileWirelessChannelTransmitter transmitter) {
        boolean anyRequested = false;

        ICraftingRequester requester = new ICraftingRequester() {
            @Override
            public IGridNode getActionableNode() {
                return transmitter.getProxy().getNode();
            }

            @Override
            public ImmutableSet<ICraftingLink> getRequestedJobs() {
                return ImmutableSet.of();
            }

            @Override
            public IAEItemStack injectCraftedItems(ICraftingLink link, IAEItemStack items, Actionable mode) {
                return items;
            }

            @Override
            public void jobStateChange(ICraftingLink link) {
            }
        };

        for (ItemStack stack : toCraft) {
            try {
                IAEItemStack want = AEItemStack.fromItemStack(stack);
                Future<ICraftingJob> future = craftingGrid.beginCraftingJob(world, grid, source, want, new ICraftingCallback() {
                    @Override
                    public void calculationComplete(ICraftingJob job) {
                        try {
                            craftingGrid.submitJob(job, requester, null, false, source);
                        } catch (Exception e) {
                            AE2Enhanced.LOGGER.debug("[AE2E] Failed to submit crafting job", e);
                        }
                    }
                });

                ICraftingJob job = future.get(200, TimeUnit.MILLISECONDS);
                if (job != null) {
                    ICraftingLink link = craftingGrid.submitJob(job, requester, null, false, source);
                    if (link != null) {
                        anyRequested = true;
                    }
                }
            } catch (java.util.concurrent.TimeoutException e) {
                anyRequested = true;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Crafting request failed for {}", stack.getDisplayName(), e);
            }
        }

        return anyRequested;
    }

    // ================== 背包操作 ==================

    public static int countInInventory(EntityPlayer player, ItemStack stack) {
        int count = 0;
        for (ItemStack invStack : player.inventory.mainInventory) {
            if (invStack.isEmpty()) continue;
            if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        for (ItemStack invStack : player.inventory.offHandInventory) {
            if (invStack.isEmpty()) continue;
            if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        return count;
    }

    /**
     * 确保玩家背包(含 ME 网络回退)中有足够的物品.
     * 如果网络拉取了物品,它们会被放入玩家背包.
     * @return true 表示所有物品都已在背包中可用(CRAFTING_REQUESTED 也视为 true,因为合成已启动)
     */
    public static boolean ensureAvailable(EntityPlayer player, List<ItemStack> needed) {
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack need : needed) {
            if (need.isEmpty()) continue;
            int available = countInInventory(player, need);
            if (available < need.getCount()) {
                ItemStack deficit = need.copy();
                deficit.setCount(need.getCount() - available);
                missing.add(deficit);
            }
        }
        if (missing.isEmpty()) return true;

        NetworkPullResult result = tryPullFromNetwork(player, missing);
        if (result == NetworkPullResult.FAILED) return false;

        // PULLED 或 CRAFTING_REQUESTED：再次验证库存
        for (ItemStack need : needed) {
            if (need.isEmpty()) continue;
            if (countInInventory(player, need) < need.getCount()) {
                return false;
            }
        }
        return true;
    }

    public static void consumeFromInventory(EntityPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = 0; i < player.inventory.mainInventory.size() && remaining > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory.get(i);
            if (invStack.isEmpty()) continue;
            if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                int take = Math.min(remaining, invStack.getCount());
                invStack.shrink(take);
                if (invStack.isEmpty()) {
                    player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
                remaining -= take;
            }
        }
        if (remaining > 0) {
            for (int i = 0; i < player.inventory.offHandInventory.size() && remaining > 0; i++) {
                ItemStack invStack = player.inventory.offHandInventory.get(i);
                if (invStack.isEmpty()) continue;
                if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                    int take = Math.min(remaining, invStack.getCount());
                    invStack.shrink(take);
                    if (invStack.isEmpty()) {
                        player.inventory.offHandInventory.set(i, ItemStack.EMPTY);
                    }
                    remaining -= take;
                }
            }
        }
    }
}
