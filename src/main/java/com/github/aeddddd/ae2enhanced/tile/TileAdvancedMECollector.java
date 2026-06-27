package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import appeng.parts.automation.StackUpgradeInventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * 先进 ME 收集器的 TileEntity.
 *
 * <p>接入 AE 网络,维护一个 63 槽的过滤库存(7×9)和 5 个升级槽(容量卡扩展过滤行),
 * 以及一个 27 槽内部缓冲区(每槽 4096).</p>
 *
 * <p>核心设计:在 EntityItem 加入世界之前就尝试把物品注入 AE 网络;网络吃不下时,
 * 物品暂存到内部缓冲区,每 tick 继续注入.只有在缓冲区也放不下时才生成实体.</p>
 */
public class TileAdvancedMECollector extends TileAENetworkBase
        implements ITickable, IAEAppEngInventory, appeng.api.implementations.IUpgradeableHost,
        appeng.api.networking.security.IActionHost, appeng.util.IConfigManagerHost {

    public static final int CONFIG_SIZE = 63;
    public static final int UPGRADE_SLOTS = 5;
    public static final int BUFFER_SIZE = 27;
    public static final int BUFFER_STACK_LIMIT = 4096;

    private final AppEngInternalAEInventory config;
    private StackUpgradeInventory upgrades;
    private final ItemStackHandler buffer;

    private int range = AE2EnhancedConfig.collector.defaultRange;
    private int tickCounter = 0;
    private int clientFlags = 0;
    private boolean lastPowered = false;
    private boolean lastActive = false;

    // 性能统计(调试用)
    private long statItemsInjected = 0;
    private long statEntitiesPrevented = 0;
    private long statItemsBuffered = 0;
    private long statBufferOverflows = 0;
    private int statLogCounter = 0;

    public TileAdvancedMECollector() {
        this.config = new AppEngInternalAEInventory(this, CONFIG_SIZE);
        this.buffer = new ItemStackHandler(BUFFER_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return BUFFER_STACK_LIMIT;
            }

            @Override
            protected void onContentsChanged(int slot) {
                TileAdvancedMECollector.this.markDirty();
            }
        };
    }

    // ---- TileAENetworkBase ----

    @Override
    protected String getProxyName() {
        return "advanced_me_collector";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.ADVANCED_ME_COLLECTOR);
    }

    @Override
    public void disassemble() {
        if (world != null && !world.isRemote) {
            dropContents();
        }
    }

    @Override
    public void securityBreak() {
        if (world != null && !world.isRemote) {
            world.destroyBlock(pos, false);
        }
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return getProxy().getNode();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // ---- 生命周期 ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            CollectorRegistry.register(this);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        CollectorRegistry.unregister(this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        CollectorRegistry.unregister(this);
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady()) {
            clearNeedsReady();
            getProxy().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL);
            getProxy().setIdlePowerUsage(AE2EnhancedConfig.collector.idlePower);
            getProxy().onReady();
        }

        syncClientState();

        // 每 tick 尝试把缓冲区物品注入网络
        if (isActive()) {
            flushBuffer();
        }

        // 兜底扫描:每 5 tick 一次,清理漏网之鱼
        if (++tickCounter >= 5) {
            tickCounter = 0;
            collectExistingItems();
        }

        // 每 200 tick(10 秒)输出一次统计日志
        if (++statLogCounter >= 200) {
            statLogCounter = 0;
            logStats();
        }
    }

    private void collectExistingItems() {
        if (!isActive()) return;
        AxisAlignedBB area = getCollectionArea();
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            tryCollect(item);
        }
    }

    /**
     * 将缓冲区物品尽量注入网络.
     */
    private void flushBuffer() {
        boolean changed = false;
        for (int i = 0; i < this.buffer.getSlots(); i++) {
            ItemStack stack = this.buffer.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack remaining = injectToNetwork(stack, Actionable.MODULATE);
            if (remaining.isEmpty()) {
                this.buffer.setStackInSlot(i, ItemStack.EMPTY);
                changed = true;
            } else if (remaining.getCount() < stack.getCount()) {
                this.buffer.setStackInSlot(i, remaining);
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    private void syncClientState() {
        boolean powered = isPowered();
        boolean active = isActive();
        if (powered != lastPowered || active != lastActive) {
            lastPowered = powered;
            lastActive = active;
            int flags = 0;
            if (powered) flags |= 1;
            if (active) flags |= 2;
            this.clientFlags = flags;
            if (world != null) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 2);
            }
        }
    }

    // ---- AE 网络事件 ----

    @MENetworkEventSubscribe
    public void chanRender(MENetworkChannelsChanged c) {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    @MENetworkEventSubscribe
    public void powerRender(MENetworkPowerStatusChange c) {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    // ---- 状态访问 ----

    public boolean isPowered() {
        if (world != null && world.isRemote) {
            return (this.clientFlags & 1) == 1;
        }
        try {
            return getProxy().getEnergy().isNetworkPowered();
        } catch (GridAccessException e) {
            return false;
        }
    }

    public boolean isActive() {
        if (world != null && world.isRemote) {
            return isPowered() && (this.clientFlags & 2) == 2;
        }
        try {
            return getProxy().isActive();
        } catch (Exception e) {
            return false;
        }
    }

    public int getRange() {
        return this.range;
    }

    public void setRange(int range) {
        int max = AE2EnhancedConfig.collector.maxRange;
        int newRange = Math.max(0, Math.min(range, max));
        if (newRange != this.range) {
            this.range = newRange;
            markDirty();
            if (world != null && !world.isRemote) {
                net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                world.notifyBlockUpdate(pos, state, state, 2);
            }
        }
    }

    public int getActualSideLength() {
        return this.range * 2 + 1;
    }

    public AxisAlignedBB getCollectionArea() {
        int r = this.range;
        return new AxisAlignedBB(
                pos.getX() - r, pos.getY() - r, pos.getZ() - r,
                pos.getX() + r + 1, pos.getY() + r + 1, pos.getZ() + r + 1
        );
    }

    // ---- 过滤库存与升级槽 ----

    public AppEngInternalAEInventory getConfig() {
        return this.config;
    }

    public ItemStackHandler getBuffer() {
        return this.buffer;
    }

    private StackUpgradeInventory getUpgrades() {
        if (this.upgrades == null) {
            this.upgrades = new StackUpgradeInventory(getProxy().getMachineRepresentation(), this, UPGRADE_SLOTS);
        }
        return this.upgrades;
    }

    @Override
    public int getInstalledUpgrades(Upgrades u) {
        return getUpgrades().getInstalledUpgrades(u);
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        if ("upgrades".equals(name)) {
            return getUpgrades();
        }
        return null;
    }

    @Override
    public net.minecraft.tileentity.TileEntity getTile() {
        return this;
    }

    public net.minecraft.tileentity.TileEntity getTileEntity() {
        return this;
    }

    private appeng.util.ConfigManager configManager;

    @Override
    public appeng.api.util.IConfigManager getConfigManager() {
        if (this.configManager == null) {
            this.configManager = new appeng.util.ConfigManager(this);
            this.configManager.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
            this.configManager.registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
            this.configManager.registerSetting(Settings.CRAFT_ONLY, YesNo.NO);
        }
        return this.configManager;
    }

    @Override
    public void updateSetting(appeng.api.util.IConfigManager manager, Enum settingName, Enum newValue) {
        this.markDirty();
    }

    @Override
    public appeng.api.networking.IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public void saveChanges() {
        this.markDirty();
    }

    // ---- 收集逻辑 ----

    /**
     * 尝试收集单个已存在的物品实体.成功时销毁实体并返回 true.
     */
    public boolean tryCollect(EntityItem entityItem) {
        if (world == null || world.isRemote) return false;
        if (!isActive()) return false;

        ItemStack stack = entityItem.getItem();
        if (stack.isEmpty()) return false;
        if (!matchesFilter(stack)) return false;

        ItemStack remaining = tryCollectStack(stack);
        if (remaining.isEmpty()) {
            entityItem.setDead();
            this.statEntitiesPrevented++;
            return true;
        }

        if (remaining.getCount() < stack.getCount()) {
            entityItem.setItem(remaining);
            this.statEntitiesPrevented++;
            return false; // 实体仍存在,但数量减少
        }

        return false;
    }

    /**
     * 尝试收集一个 ItemStack.
     * 返回 ItemStack.EMPTY 表示全部处理(进网或进缓冲区).
     * 返回非空表示缓冲区也放不下,只能生成实体.
     */
    public ItemStack tryCollectStack(ItemStack stack) {
        if (world == null || world.isRemote) return stack;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!matchesFilter(stack)) return stack;

        // 第一步:尝试注入网络
        ItemStack remaining = injectToNetwork(stack, Actionable.MODULATE);
        if (remaining.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // 第二步:剩余部分放入内部缓冲区
        if (isActive()) {
            ItemStack bufferRemain = insertToBuffer(remaining);
            if (bufferRemain.isEmpty()) {
                return ItemStack.EMPTY;
            }
            // 缓冲区部分能放下:记录统计
            this.statItemsBuffered += remaining.getCount() - bufferRemain.getCount();
            return bufferRemain;
        }

        return remaining;
    }

    /**
     * 强制收集:范围内有收集器且匹配过滤时,绝不生成实体.
     * 返回剩余物品(仅当缓冲区满时).
     */
    public ItemStack tryCollectStackForced(ItemStack stack) {
        if (world == null || world.isRemote) return stack;
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!matchesFilter(stack)) return stack;

        // 尝试注入网络
        ItemStack remaining = injectToNetwork(stack, Actionable.MODULATE);
        if (remaining.isEmpty()) {
            this.statEntitiesPrevented++;
            return ItemStack.EMPTY;
        }

        // 剩余部分放入缓冲区(即使网络离线也先存起来)
        ItemStack bufferRemain = insertToBuffer(remaining);
        if (bufferRemain.isEmpty()) {
            this.statEntitiesPrevented++;
            return ItemStack.EMPTY;
        }

        this.statItemsBuffered += remaining.getCount() - bufferRemain.getCount();
        if (bufferRemain.getCount() < remaining.getCount()) {
            this.statEntitiesPrevented++;
        } else {
            this.statBufferOverflows++;
        }
        return bufferRemain;
    }

    private ItemStack injectToNetwork(ItemStack stack, Actionable mode) {
        try {
            IItemStorageChannel channel = appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            IAEItemStack toInsert = channel.createStack(stack);
            if (toInsert == null) return stack;

            IAEItemStack remaining = Platform.poweredInsert(
                    getProxy().getEnergy(),
                    getProxy().getStorage().getInventory(channel),
                    toInsert,
                    new MachineSource(this)
            );

            if (remaining != null && remaining.getStackSize() > 0) {
                return remaining.createItemStack();
            }
            this.statItemsInjected += stack.getCount();
            return ItemStack.EMPTY;
        } catch (GridAccessException e) {
            return stack;
        }
    }

    private ItemStack insertToBuffer(ItemStack stack) {
        ItemStack current = stack.copy();
        for (int i = 0; i < this.buffer.getSlots() && !current.isEmpty(); i++) {
            current = this.buffer.insertItem(i, current, false);
        }
        return current;
    }

    /**
     * 判断物品是否匹配过滤槽.过滤槽为空时收集所有物品.
     */
    public boolean matchesFilter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        int activeSlots = getAvailableFilterSlots();
        boolean hasAnyFilter = false;
        for (int i = 0; i < activeSlots; i++) {
            IAEItemStack filter = this.config.getAEStackInSlot(i);
            if (filter == null) continue;
            hasAnyFilter = true;
            if (isFilterMatch(filter, stack)) {
                return true;
            }
        }
        return !hasAnyFilter;
    }

    private boolean isFilterMatch(IAEItemStack filter, ItemStack stack) {
        if (filter == null) return false;
        ItemStack filterStack = filter.createItemStack();
        if (filterStack.isEmpty()) return false;

        if (filterStack.getItem() != stack.getItem()) return false;

        if (filterStack.getHasSubtypes() && filterStack.getMetadata() != stack.getMetadata()) {
            return false;
        }

        if (filterStack.hasTagCompound()) {
            if (!filterStack.getTagCompound().equals(stack.getTagCompound())) {
                return false;
            }
        }

        return true;
    }

    public int getAvailableFilterSlots() {
        int capacityUpgrades = getInstalledUpgrades(Upgrades.CAPACITY);
        return Math.min(18 + capacityUpgrades * 9, CONFIG_SIZE);
    }

    // ---- 统计 ----

    private void logStats() {
        if (statItemsInjected == 0 && statEntitiesPrevented == 0 && statItemsBuffered == 0 && statBufferOverflows == 0) {
            return;
        }
        AE2Enhanced.LOGGER.debug("[AE2E] Collector at {} stats: injected={}, prevented={}, buffered={}, overflows={}",
                this.pos, this.statItemsInjected, this.statEntitiesPrevented, this.statItemsBuffered, this.statBufferOverflows);
    }

    // ---- 物品栏变化回调 ----

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        this.markDirty();
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.range = compound.getInteger("range");
        this.config.readFromNBT(compound, "config");
        getUpgrades().readFromNBT(compound, "upgrades");
        this.clientFlags = compound.getInteger("clientFlags");
        if (compound.hasKey("buffer")) {
            this.buffer.deserializeNBT(compound.getCompoundTag("buffer"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("range", this.range);
        this.config.writeToNBT(compound, "config");
        getUpgrades().writeToNBT(compound, "upgrades");
        compound.setInteger("clientFlags", this.clientFlags);
        compound.setTag("buffer", this.buffer.serializeNBT());
        return compound;
    }

    // ---- 客户端同步 ----

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("range", this.range);
        tag.setInteger("clientFlags", this.clientFlags);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        this.range = tag.getInteger("range");
        this.clientFlags = tag.getInteger("clientFlags");
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    // ---- 掉落物 ----

    public void dropContents() {
        if (world == null || world.isRemote) return;
        dropInventory(this.config, world, pos);
        dropInventory(getUpgrades(), world, pos);
        dropInventory(this.buffer, world, pos);
    }

    private static void dropInventory(IItemHandler inv, World world, BlockPos pos) {
        if (inv == null) return;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, java.util.Collections.singletonList(stack));
            }
        }
    }
}
