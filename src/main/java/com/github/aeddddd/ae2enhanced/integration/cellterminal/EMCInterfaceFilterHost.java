package com.github.aeddddd.ae2enhanced.integration.cellterminal;

import appeng.api.AEApi;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.cells.api.IInterfaceHost;
import com.cells.api.ResourcePreviewEntry;
import com.cells.api.ResourceType;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * EMC 接口白名单向元件终端(Cell Terminal)暴露的过滤器视图.
 *
 * <p>实现 CELLS API 的 {@link IInterfaceHost},使 Cell Terminal 的存储总线页
 * 可以显示并编辑 EMC 接口的白名单.槽位与白名单数组一一对应,
 * 仅暴露"已用槽位 + 额外一行空槽"(受客户端 63 槽上限截断).</p>
 *
 * <p>编辑操作经 {@link CellTerminalActor} 记录的执行者做 canManage 权限校验.</p>
 *
 * <p>本类引用 cellterminal/CELLS API 类,仅允许在 cellterminal 存在时加载.</p>
 */
public class EMCInterfaceFilterHost implements IInterfaceHost {

    /** 每行槽数,与 AE2/终端惯例一致 */
    public static final int ROW_SLOTS = 9;

    /**
     * 客户端可交互的最大槽数.
     * Cell Terminal 客户端硬上限 MAX_STORAGE_BUS_PARTITION_SLOTS = 63.
     */
    public static final int MAX_EXPOSED_SLOTS = 63;

    private final TileEMCInterface tile;

    public EMCInterfaceFilterHost(TileEMCInterface tile) {
        this.tile = tile;
    }

    public TileEMCInterface getTile() {
        return tile;
    }

    /**
     * 计算对外暴露的槽位数: 最后一个已用槽所在行 + 额外一行空槽,
     * 按 {@link #MAX_EXPOSED_SLOTS} 截断.超出部分的白名单标记在终端中不可见,
     * 但语义不受影响(去重/提取仍按完整白名单工作).
     */
    public static int getExposedSlots(@Nonnull TileEMCInterface tile) {
        ItemStack[] whitelist = tile.getWhitelist();
        int lastUsed = -1;
        int scanLimit = Math.min(whitelist.length, MAX_EXPOSED_SLOTS);
        for (int i = 0; i < scanLimit; i++) {
            if (!whitelist[i].isEmpty()) lastUsed = i;
        }
        int rows = lastUsed < 0 ? 1 : (lastUsed / ROW_SLOTS) + 2;
        return Math.min(MAX_EXPOSED_SLOTS, rows * ROW_SLOTS);
    }

    // ---- IFilterHost ----

    @Override
    public int getFilterSlots() {
        return getExposedSlots(tile);
    }

    @Nonnull
    @Override
    public ItemStack getFilter(int slot) {
        if (slot < 0 || slot >= TileEMCInterface.WHITELIST_SIZE) return ItemStack.EMPTY;
        return tile.getWhitelistSlot(slot);
    }

    @Override
    public void setFilter(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= TileEMCInterface.WHITELIST_SIZE) return;
        if (!checkPermission()) return;
        tile.setWhitelistSlot(slot, stack);
    }

    @Override
    public void clearFilters() {
        if (!checkPermission()) return;
        tile.clearWhitelist();
    }

    /**
     * 校验当前执行者是否有权管理此接口.
     * 执行者未知(非终端操作路径)时放行,保持与方块自身 GUI 一致的行为.
     */
    private boolean checkPermission() {
        EntityPlayer actor = CellTerminalActor.get();
        if (actor == null) return true;
        if (tile.canManage(actor)) return true;
        actor.sendMessage(new TextComponentTranslation("chat.ae2enhanced.emc_interface.no_permission"));
        return false;
    }

    // ---- IInterfaceHost ----

    @Nonnull
    @Override
    public ResourceType getResourceType() {
        return ResourceType.ITEM;
    }

    @Override
    public boolean isExport() {
        // EMC 接口是网络的单向物品源,语义等同"从外部读入网络"
        return false;
    }

    @Override
    public boolean isDirectionalView() {
        return false;
    }

    @Nonnull
    @Override
    public EnumFacing getPrimaryFacing() {
        return EnumFacing.NORTH;
    }

    @Nonnull
    @Override
    public Collection<EnumFacing> getTargetFacings() {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public List<ResourcePreviewEntry> getPreviewEntries(int limit) {
        return collectPreviewEntries(limit);
    }

    @Nonnull
    @Override
    public List<ResourcePreviewEntry> getPreviewEntries(@Nonnull EnumFacing facing, int limit) {
        return collectPreviewEntries(limit);
    }

    /**
     * 内容预览 = 当前向网络暴露的物品(白名单 ∩ 已学知识 ∩ 余额可负担),
     * 直接复用 EMCInventoryHandler 的缓存视图.
     */
    @Nonnull
    private List<ResourcePreviewEntry> collectPreviewEntries(int limit) {
        List<ResourcePreviewEntry> entries = new java.util.ArrayList<>();
        if (!tile.isBound()) return entries;

        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        for (IMEInventoryHandler<?> handler : tile.getCellArray(channel)) {
            @SuppressWarnings("unchecked")
            IMEInventoryHandler<IAEItemStack> itemHandler = (IMEInventoryHandler<IAEItemStack>) handler;
            IItemList<IAEItemStack> available = itemHandler.getAvailableItems(channel.createList());
            for (IAEItemStack aeStack : available) {
                if (aeStack.getStackSize() <= 0) continue;
                ItemStack display = aeStack.getDefinition();
                if (display.isEmpty()) continue;
                entries.add(new ResourcePreviewEntry(ResourceType.ITEM, display, aeStack.getStackSize()));
                if (limit > 0 && entries.size() >= limit) return entries;
            }
        }
        return entries;
    }

    @Nullable
    @Override
    public IItemHandler getUpgradeInventory() {
        return null;
    }
}
