package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEEventHandler;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.EMCInventoryHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;

/**
 * EMC 接口 TileEntity.
 *
 * <p>将绑定玩家的 ProjectE EMC 余额作为 AE 网络物品源.单向输出,不接收物品.</p>
 */
public class TileEMCInterface extends TileAENetworkBase implements ICellContainer, ITickable, IAEAppEngInventory {

    public static final int WHITELIST_PAGES = 20;
    public static final int WHITELIST_SLOTS_PER_PAGE = 102; // 17×6，与 3.png 顶部网格一致
    public static final int WHITELIST_SIZE = WHITELIST_PAGES * WHITELIST_SLOTS_PER_PAGE; // 2040

    /** 创造模式解锁阈值: ProjectE long EMC 上限 (≈9.2E18) */
    public static final java.math.BigInteger EMC_CAP = java.math.BigInteger.valueOf(Long.MAX_VALUE);

    private final EMCInventoryHandler handler = new EMCInventoryHandler(this);
    private final AppEngInternalAEInventory config;
    private final ItemStack[] whitelist = new ItemStack[WHITELIST_SIZE];
    private final Set<ItemDescriptor> whitelistSet = new HashSet<>();

    @Nullable
    private UUID ownerUUID;
    private String ownerName = "";

    // 创造模式: 提取不消耗 EMC, 显示数量 = EMC_CAP/物品EMC(封顶 4.6E18),
    // 空白名单时暴露全部已学知识. 持久化于 NBT, 不随玩家上下线变化
    private boolean creativeMode = false;

    private boolean registeredEvents = false;

    // 批量清空白名单时抑制 config 回调,避免逐槽 O(n) 重建导致的 O(n²) 开销
    private boolean suppressConfigCallback = false;

    // ProjectE 知识同步节流: 提取只改 EMC 余额不改知识列表,
    // 全量 sync(知识 NBT 序列化 + 发包)按 syncIntervalTicks 合并执行
    private boolean syncDirty = false;
    private long lastSyncTick = -100;

    // 知识 provider 缓存: 按解析时的在线状态区分(在线/离线 provider 实现不同)
    private Object cachedKnowledgeProvider = null;
    private boolean cachedProviderOnline = false;

    // 离线扣减冲刷节流
    private long lastOfflineFlushTick = -100;

    /**
     * 标记需要向绑定玩家同步 ProjectE 知识/EMC 数据,由 update() 节流冲刷.
     */
    public void markSyncDirty() {
        this.syncDirty = true;
    }

    public void invalidateHandlerCache() {
        handler.invalidateAvailableCache();
        // 知识变更可能伴随 provider 实例更换(尤其 PETeams 团队 provider),一并失效
        cachedKnowledgeProvider = null;
        // 知识/EMC remap 变化会改变可用物品集合,同步刷新网络存储视图
        notifyCellArrayUpdate();
    }

    public void invalidateEmcCache() {
        handler.invalidateEmcCache();
    }


    public TileEMCInterface() {
        this.config = new AppEngInternalAEInventory(this, WHITELIST_SIZE);
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            whitelist[i] = ItemStack.EMPTY;
        }
    }

    @Override
    protected String getProxyName() {
        return "emc_interface";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.EMC_INTERFACE);
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {
        // 安全破坏时不掉落,仅解绑
        setOwner(null);
    }

    // ---- 玩家绑定 ----

    public boolean isBound() {
        return ownerUUID != null && ProjectEHelper.isAvailable();
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(@Nullable EntityPlayer player) {
        // 换绑前先把旧 owner 的待冲刷离线扣减结清,避免挂到错误账户
        handler.flushPendingOfflineEmc();
        // 创造模式与解锁时点的所有者成就绑定,换绑/解绑后需重新达成上限解锁
        this.creativeMode = false;
        if (player == null) {
            this.ownerUUID = null;
            this.ownerName = "";
        } else {
            this.ownerUUID = player.getUniqueID();
            this.ownerName = player.getName();
        }
        cachedKnowledgeProvider = null;
        handler.invalidateAvailableCache();
        markDirty();
        notifyCellArrayUpdate();
        syncToClient();
    }

    /**
     * 玩家是否有权管理(重新绑定/打开 GUI/编辑白名单)此接口.
     * 未绑定时任何人可认领;已绑定时仅所有者或 OP(权限等级 2).
     */
    public boolean canManage(@Nonnull EntityPlayer player) {
        if (ownerUUID == null) return true;
        if (player.getUniqueID().equals(ownerUUID)) return true;
        return player.canUseCommand(2, "");
    }

    /**
     * 绑定玩家当前是否在线.
     * 离线时 ProjectE 返回的 TransmutationOffline 包装 provider 为只读快照,
     * 读取走快照,扣减 EMC 由 ProjectEHelper.subtractEmcOffline 直接改写存档数据.
     */
    public boolean isOwnerOnline() {
        if (ownerUUID == null) return false;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null && server.getPlayerList().getPlayerByUUID(ownerUUID) != null;
    }

    // ---- 创造模式 ----

    public boolean isCreativeMode() {
        return creativeMode;
    }

    /**
     * 切换创造模式. 开启前结清普通模式遗留的待冲刷离线扣减,
     * 随后刷新网络存储视图并同步客户端. 解锁校验(余额≥{@link #EMC_CAP})由调用方完成.
     */
    public void setCreativeMode(boolean creative) {
        if (this.creativeMode == creative) return;
        if (creative) {
            handler.flushPendingOfflineEmc();
        }
        this.creativeMode = creative;
        handler.invalidateAvailableCache();
        markDirty();
        notifyCellArrayUpdate();
        syncToClient();
    }

    /**
     * 规范化白名单物品: count=1, 且对齐 ProjectE 学习知识时的 NBT 修剪规则
     * (非 NBTWhitelist 物品剥离 NBT),保证与已学知识列表可匹配.
     */
    @Nonnull
    private static ItemStack normalizeWhitelistStack(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        if (copy.hasTagCompound() && !ProjectEHelper.shouldDupeWithNBT(copy)) {
            copy.setTagCompound(null);
        }
        return copy;
    }

    private void syncToClient() {
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Nullable
    public Object getKnowledgeProvider() {
        if (ownerUUID == null) return null;
        // PETeams 下 getKnowledgeProviderFor 会触发团队知识 NBT 反序列化,
        // 不能每次提取/终端扫描都重新解析. 按在线状态区分缓存
        // (ProjectE 对在线/离线玩家返回不同 provider 实现),状态切换时自动重建
        boolean online = isOwnerOnline();
        if (cachedKnowledgeProvider != null && cachedProviderOnline == online) {
            return cachedKnowledgeProvider;
        }
        cachedKnowledgeProvider = ProjectEHelper.getKnowledgeProvider(ownerUUID);
        cachedProviderOnline = online;
        return cachedKnowledgeProvider;
    }

    // ---- 白名单 ----

    public AppEngInternalAEInventory getConfig() {
        return config;
    }

    public ItemStack getWhitelistSlot(int index) {
        return whitelist[index].copy();
    }

    public void setWhitelistSlot(int index, @Nonnull ItemStack stack) {
        whitelist[index] = normalizeWhitelistStack(stack);
        config.setStackInSlot(index, whitelist[index]);
        rebuildWhitelistSet();
        handler.invalidateAvailableCache();
        markDirty();
        notifyCellArrayUpdate();
    }

    public ItemStack[] getWhitelist() {
        return whitelist;
    }

    public boolean isWhitelisted(@Nonnull ItemStack stack) {
        if (whitelistSet.isEmpty()) return false; // 空白名单 = 不暴露任何物品
        return whitelistSet.contains(new ItemDescriptor(stack));
    }

    public boolean isWhitelistActive() {
        return !whitelistSet.isEmpty();
    }

    private void rebuildWhitelistSet() {
        whitelistSet.clear();
        for (ItemStack stack : whitelist) {
            if (!stack.isEmpty()) {
                whitelistSet.add(new ItemDescriptor(stack));
            }
        }
    }

    /**
     * 清空全部白名单标记(供元件终端等外部编辑器批量操作).
     * 批量执行期间抑制 config 逐槽回调,结束后一次性重建状态并通知网络.
     */
    public void clearWhitelist() {
        suppressConfigCallback = true;
        try {
            for (int i = 0; i < WHITELIST_SIZE; i++) {
                if (!config.getStackInSlot(i).isEmpty()) {
                    config.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        } finally {
            suppressConfigCallback = false;
        }
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            whitelist[i] = ItemStack.EMPTY;
        }
        rebuildWhitelistSet();
        handler.invalidateAvailableCache();
        markDirty();
        notifyCellArrayUpdate();
    }

    /**
     * 去除与指定槽位相同的其它标记(保留新标记,清除旧标记).
     * 清除通过 config.setStackInSlot 进行,会回调 onChangeInventory 完成状态同步.
     */
    private void dedupeWhitelist(int keepSlot) {
        ItemStack keep = whitelist[keepSlot];
        if (keep.isEmpty()) return;
        ItemDescriptor desc = new ItemDescriptor(keep);
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            if (i == keepSlot || whitelist[i].isEmpty()) continue;
            if (desc.equals(new ItemDescriptor(whitelist[i]))) {
                config.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    // ---- ICellContainer ----

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        if (!isBound()) return Collections.emptyList();
        if (channel instanceof IItemStorageChannel) {
            return Collections.singletonList((IMEInventoryHandler) handler);
        }
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void blinkCell(int slot) {
    }

    @Override
    public void saveChanges(ICellInventory<?> inv) {
    }

    // ---- 生命周期 ----

    @Override
    public void validate() {
        super.validate();
        if (world != null && !world.isRemote) {
            registerProjectEEvents();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // 卸载前尽力结清待冲刷的离线扣减,缩小崩溃/卸载丢失窗口
        if (world != null && !world.isRemote) {
            handler.flushPendingOfflineEmc();
        }
        unregisterProjectEEvents();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        unregisterProjectEEvents();
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady()) {
            clearNeedsReady();
            getProxy().setIdlePowerUsage(AE2EnhancedConfig.emcInterface.idlePower);
            getProxy().onReady();
        }

        if (creativeMode) return; // 创造模式无 EMC 扣减/同步开销

        flushSyncIfNeeded();
        flushOfflineEmcIfNeeded();
    }

    /**
     * 按配置间隔冲刷累计的离线 EMC 扣减,避免每次提取都读写并压缩 playerdata 存档.
     */
    private void flushOfflineEmcIfNeeded() {
        long now = world.getTotalWorldTime();
        if (now - lastOfflineFlushTick < AE2EnhancedConfig.emcInterface.syncIntervalTicks) return;
        if (!handler.hasPendingOfflineEmc()) return;
        lastOfflineFlushTick = now;
        handler.flushPendingOfflineEmc();
    }

    /**
     * 按配置间隔合并冲刷 ProjectE 同步请求,避免每次提取都全量序列化知识并发包.
     */
    private void flushSyncIfNeeded() {
        if (!syncDirty || ownerUUID == null) return;
        long now = world.getTotalWorldTime();
        if (now - lastSyncTick < AE2EnhancedConfig.emcInterface.syncIntervalTicks) return;
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        net.minecraft.entity.player.EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(ownerUUID);
        if (player == null) return; // 玩家离线,保留脏标记待其上线后同步
        syncDirty = false;
        lastSyncTick = now;
        ProjectEHelper.sync(getKnowledgeProvider(), player);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasUniqueId("OwnerUUID")) {
            ownerUUID = compound.getUniqueId("OwnerUUID");
        } else {
            ownerUUID = null;
        }
        ownerName = compound.getString("OwnerName");
        creativeMode = compound.getBoolean("CreativeMode");

        NBTTagList list = compound.getTagList("Whitelist", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            whitelist[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < list.tagCount() && i < WHITELIST_SIZE; i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getShort("Slot") & 0xFFFF;
            if (slot < WHITELIST_SIZE) {
                whitelist[slot] = new ItemStack(tag);
            }
        }
        // 去除历史遗留的重复标记(保留首次出现)
        Set<ItemDescriptor> seen = new HashSet<>();
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            if (whitelist[i].isEmpty()) continue;
            if (!seen.add(new ItemDescriptor(whitelist[i]))) {
                whitelist[i] = ItemStack.EMPTY;
            }
        }
        rebuildWhitelistSet();
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            config.setStackInSlot(i, whitelist[i]);
        }
        handler.invalidateAvailableCache();
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (ownerUUID != null) {
            compound.setUniqueId("OwnerUUID", ownerUUID);
        }
        compound.setString("OwnerName", ownerName);
        if (creativeMode) {
            compound.setBoolean("CreativeMode", true);
        }

        NBTTagList list = new NBTTagList();
        for (int i = 0; i < WHITELIST_SIZE; i++) {
            if (!whitelist[i].isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setShort("Slot", (short) i);
                whitelist[i].writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        compound.setTag("Whitelist", list);
        return compound;
    }

    // ---- 客户端同步 ----

    @Override
    @Nonnull
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        if (ownerUUID != null) {
            tag.setUniqueId("OwnerUUID", ownerUUID);
        }
        tag.setString("OwnerName", ownerName);
        tag.setBoolean("CreativeMode", creativeMode);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound tag = pkt.getNbtCompound();
        if (tag.hasUniqueId("OwnerUUID")) {
            ownerUUID = tag.getUniqueId("OwnerUUID");
        } else {
            ownerUUID = null;
        }
        ownerName = tag.getString("OwnerName");
        creativeMode = tag.getBoolean("CreativeMode");
    }

    // ---- 内部辅助 ----

    private void notifyCellArrayUpdate() {
        try {
            IGrid grid = getProxy().getGrid();
            if (grid != null) {
                grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
            }
        } catch (GridAccessException e) {
            // grid 尚未就绪
        }
    }

    private void registerProjectEEvents() {
        if (registeredEvents || !ProjectEHelper.isAvailable()) return;
        registeredEvents = true;
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEEventHandler");
            clazz.getMethod("registerTile", TileEMCInterface.class).invoke(null, this);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to register ProjectE event listeners", e);
        }
    }

    private void unregisterProjectEEvents() {
        if (!registeredEvents) return;
        registeredEvents = false;
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEEventHandler");
            clazz.getMethod("unregisterTile", TileEMCInterface.class).invoke(null, this);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to unregister ProjectE event tile", e);
        }
    }

    @Override
    public void disassemble() {
        // EMC 接口无结构,解绑即可
        ownerUUID = null;
        ownerName = "";
        creativeMode = false;
        handler.invalidateAvailableCache();
        markDirty();
    }

    // ---- IAEAppEngInventory ----

    @Override
    public void saveChanges() {
        markDirty();
    }

    @Override
    public void onChangeInventory(net.minecraftforge.items.IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (suppressConfigCallback) return;
        if (inv == config && slot >= 0 && slot < WHITELIST_SIZE) {
            ItemStack normalized = normalizeWhitelistStack(added);
            if (!ItemStack.areItemStacksEqual(added, normalized)) {
                // 回写规范化后的物品; 再次触发本回调时 added 已规范化,不再进入此分支
                config.setStackInSlot(slot, normalized);
                return;
            }
            whitelist[slot] = normalized;
            dedupeWhitelist(slot);
            rebuildWhitelistSet();
            handler.invalidateAvailableCache();
            markDirty();
            notifyCellArrayUpdate();
        }
    }

}
