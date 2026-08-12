package com.github.aeddddd.ae2enhanced.part;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.settings.TickRates;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.PartModel;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.util.ConfigManager;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import appeng.util.prioritylist.PrecisePriorityList;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.platform.energy.EnergyAdapterRegistry;
import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.storage.channel.ChannelRegistrationManager;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.energy.ExternalEnergyMonitor;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEnergies;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 能源存储总线.
 * <p>
 * 逻辑完全对标 AE2 原版存储总线(PartStorageBus):将相邻能量容器
 * (Forge IEnergyStorage / 龙研 IExtendedRFStorage 等)通过 RF 能源存储通道
 * 暴露给 ME 网络,支持优先级、访问模式(读写/只读/只写)、白名单过滤
 * (仅接受 RF 假物品)与升级卡(双向过滤卡反转黑白名单、容量卡解锁过滤槽).
 * </p>
 * <p>
 * 龙之研究能量核心(龙球)通过 {@link EnergyAdapterRegistry} 的
 * DEEnergyAdapter 实现 long 级读取与存取,突破 Forge int 上限.
 * </p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PartEnergyStorageBus extends PartUpgradeable implements IGridTickable, ICellContainer,
        IMEMonitorHandlerReceiver, IPriorityHost {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AE2Enhanced.MOD_ID, "part/energy_storage_bus_base");

    @PartModels
    public static final ResourceLocation[] MODELS = new ResourceLocation[]{
            MODEL_BASE,
            new ResourceLocation("appliedenergistics2", "part/storage_bus_off"),
            new ResourceLocation("appliedenergistics2", "part/storage_bus_on"),
            new ResourceLocation("appliedenergistics2", "part/storage_bus_has_channel")
    };

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODELS[1]);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODELS[2]);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODELS[3]);

    private final IActionSource mySrc;
    private final AppEngInternalAEInventory config = new AppEngInternalAEInventory(this, 63);
    private int priority = 0;
    private boolean cached = false;
    private ExternalEnergyMonitor monitor = null;
    private MEInventoryHandler handler = null;
    private int handlerHash = 0;
    private boolean wasActive = false;
    private byte resetCacheLogic = 0;
    private boolean accessChanged;
    private boolean readOncePass;
    /** 上次向网络上报时的储量,用于轮询外部变化 */
    private long lastKnownStored = -1;

    public PartEnergyStorageBus(ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.mySrc = new MachineSource(this);
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(MENetworkPowerStatusChange c) {
        this.updateStatus();
    }

    private void updateStatus() {
        boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
                this.getHost().markForUpdate();
            } catch (GridAccessException ignored) {
            }
        }
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(MENetworkChannelsChanged changedChannels) {
        this.updateStatus();
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
        if (settingName.name().equals("ACCESS")) {
            this.accessChanged = true;
        }
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack, ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);
        if (inv == this.config) {
            this.resetCache(true);
        }
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        this.resetCache(true);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.config.readFromNBT(data, "config");
        this.priority = data.getInteger("priority");
        this.accessChanged = false;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.config.writeToNBT(data, "config");
        data.setInteger("priority", this.priority);
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if (name.equals("config")) {
            return this.config;
        }
        return super.getInventoryByName(name);
    }

    protected void resetCache(boolean fullReset) {
        if (this.getHost() == null || this.getHost().getTile() == null
                || this.getHost().getTile().getWorld() == null || this.getHost().getTile().getWorld().isRemote) {
            return;
        }
        if (fullReset) {
            this.resetCacheLogic = (byte) 2;
        } else if (this.resetCacheLogic < 2) {
            this.resetCacheLogic = 1;
        }
        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException ignored) {
        }
    }

    @Override
    public boolean isValid(Object verificationToken) {
        return this.handler == verificationToken;
    }

    @Override
    public void postChange(IBaseMonitor monitor, Iterable change, IActionSource source) {
        if (this.getProxy().isActive()) {
            // 同步轮询基线,避免自身操作被轮询重复上报
            if (this.monitor != null) {
                this.lastKnownStored = this.monitor.getStoredEnergy();
            }
            Iterable filteredChanges = this.filterChanges(change);
            AccessRestriction currentAccess = (AccessRestriction) ((ConfigManager) this.getConfigManager()).getSetting(Settings.ACCESS);
            if (this.readOncePass) {
                this.readOncePass = false;
                try {
                    this.getProxy().getStorage().postAlterationOfStoredItems(this.energyChannel(), filteredChanges, this.mySrc);
                } catch (GridAccessException ignored) {
                }
                return;
            }
            if (!currentAccess.hasPermission(AccessRestriction.READ)) {
                return;
            }
            try {
                this.getProxy().getStorage().postAlterationOfStoredItems(this.energyChannel(), filteredChanges, source);
            } catch (GridAccessException ignored) {
            }
        }
    }

    @Override
    public void onListUpdate() {
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(3.0, 3.0, 15.0, 13.0, 13.0, 16.0);
        bch.addBox(2.0, 2.0, 14.0, 14.0, 14.0, 15.0);
        bch.addBox(5.0, 5.0, 12.0, 11.0, 11.0, 14.0);
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(this.getSide().getFacing()).equals(neighbor)) {
            TileEntity te = w.getTileEntity(neighbor);
            if (te == null) {
                this.resetCache(true);
                this.resetCache();
            } else {
                this.resetCache(false);
            }
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4.0f;
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isServer()) {
            TileEntity te = this.getHost().getTile();
            int guiId = GuiHandler.GUI_ENERGY_STORAGE_BUS | (this.getSide().ordinal() << 8);
            player.openGui(AE2Enhanced.instance, guiId, te.getWorld(),
                    te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
        }
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.StorageBus.getMin(), TickRates.StorageBus.getMax(), this.monitor == null, true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.resetCacheLogic != 0) {
            this.resetCache();
        }
        if (this.monitor == null || !this.getProxy().isActive()) {
            return TickRateModulation.SLEEP;
        }
        AccessRestriction access = (AccessRestriction) ((ConfigManager) this.getConfigManager()).getSetting(Settings.ACCESS);
        if (!access.hasPermission(AccessRestriction.READ)) {
            this.lastKnownStored = -1;
            return TickRateModulation.SLOWER;
        }
        // 轮询外部途径(其它 mod 管道等)造成的能量变化并上报网络
        long stored = this.monitor.getStoredEnergy();
        if (this.lastKnownStored < 0) {
            this.lastKnownStored = stored;
            return TickRateModulation.SLOWER;
        }
        if (stored != this.lastKnownStored) {
            long delta = stored - this.lastKnownStored;
            this.lastKnownStored = stored;
            IAEStack change = EnergyChannelResolver.createStack(0);
            if (change == null) {
                return TickRateModulation.SLOWER;
            }
            change.setStackSize(delta);
            try {
                @SuppressWarnings("rawtypes")
                java.util.List changes = Collections.singletonList(change);
                this.getProxy().getStorage().postAlterationOfStoredItems(
                        this.energyChannel(), changes, this.mySrc);
            } catch (GridAccessException ignored) {
            }
            return TickRateModulation.FASTER;
        }
        return TickRateModulation.SLOWER;
    }

    protected void resetCache() {
        boolean fullReset = this.resetCacheLogic == 2;
        this.resetCacheLogic = 0;
        MEInventoryHandler in = this.getInternalHandler();
        IItemList before = this.energyChannel().createList();
        if (in != null) {
            if (this.accessChanged) {
                AccessRestriction currentAccess = (AccessRestriction) ((ConfigManager) this.getConfigManager()).getSetting(Settings.ACCESS);
                AccessRestriction oldAccess = (AccessRestriction) ((ConfigManager) this.getConfigManager()).getOldSetting(Settings.ACCESS);
                if (oldAccess.hasPermission(AccessRestriction.READ) && !currentAccess.hasPermission(AccessRestriction.READ)) {
                    this.readOncePass = true;
                }
                in.setBaseAccess(oldAccess);
                before = in.getAvailableItems(before);
                in.setBaseAccess(currentAccess);
                this.accessChanged = false;
            } else {
                before = in.getAvailableItems(before);
            }
        }
        this.cached = false;
        if (fullReset) {
            this.handlerHash = 0;
        }
        MEInventoryHandler out = this.getInternalHandler();
        IItemList after = this.energyChannel().createList();
        if (in != out) {
            if (out != null) {
                after = out.getAvailableItems(after);
            }
            Platform.postListChanges(before, after, this, this.mySrc);
        }
    }

    ExternalEnergyMonitor getInventoryWrapper(TileEntity target) {
        EnumFacing targetSide = this.getSide().getFacing().getOpposite();
        IEnergyStorage cap = target.getCapability(CapabilityEnergy.ENERGY, targetSide);
        IEnergyAdapter adapter = EnergyAdapterRegistry.findAdapter(getBlockId(target));
        if (!adapter.canHandleTile(target, cap)) {
            return null;
        }
        ExternalEnergyMonitor wrapper = new ExternalEnergyMonitor(target, adapter, cap);
        wrapper.setStorageFilter((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
        return wrapper;
    }

    int createHandlerHash(TileEntity target) {
        if (target == null) {
            return 0;
        }
        EnumFacing targetSide = this.getSide().getFacing().getOpposite();
        IEnergyStorage cap = target.getCapability(CapabilityEnergy.ENERGY, targetSide);
        IEnergyAdapter adapter = EnergyAdapterRegistry.findAdapter(getBlockId(target));
        if (!adapter.canHandleTile(target, cap)) {
            return 0;
        }
        return Objects.hash(target, cap, adapter.getClass());
    }

    private static String getBlockId(TileEntity target) {
        return target.getBlockType().getRegistryName() != null
                ? target.getBlockType().getRegistryName().toString() : "";
    }

    public MEInventoryHandler getInternalHandler() {
        if (this.cached) {
            return this.handler;
        }
        boolean wasSleeping = this.monitor == null;
        this.cached = true;
        TileEntity self = this.getHost().getTile();
        TileEntity target = self.getWorld().getTileEntity(self.getPos().offset(this.getSide().getFacing()));
        int newHandlerHash = this.createHandlerHash(target);
        if (newHandlerHash != 0 && newHandlerHash == this.handlerHash) {
            return this.handler;
        }
        this.handlerHash = newHandlerHash;
        this.handler = null;
        if (this.monitor != null) {
            this.monitor.removeListener(this);
        }
        this.monitor = null;
        this.lastKnownStored = -1;
        if (target != null) {
            ExternalEnergyMonitor inv = this.getInventoryWrapper(target);
            if (inv != null) {
                this.monitor = inv;
                this.handler = new MEInventoryHandler(inv, this.energyChannel());
                this.handler.setBaseAccess((AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS));
                this.handler.setWhitelist(this.getInstalledUpgrades(Upgrades.INVERTER) > 0
                        ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
                this.handler.setPriority(this.priority);
                this.handler.setStorageFilter((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
                IItemList priorityList = this.energyChannel().createList();
                int slotsToUse = 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
                for (int x = 0; x < this.config.getSlots() && x < slotsToUse; ++x) {
                    IAEItemStack is = this.config.getAEStackInSlot(x);
                    if (is == null) {
                        continue;
                    }
                    IAEEnergyStack energy = FakeEnergies.unpackEnergy(is);
                    if (energy != null) {
                        // 按当前生效通道重建堆叠(Flux 通道下必须为 FluxStack)
                        IAEStack channelStack = EnergyChannelResolver.createStack(energy.getStackSize());
                        if (channelStack != null) {
                            priorityList.add(channelStack);
                        }
                    }
                }
                this.handler.setPartitionList(new PrecisePriorityList<>(priorityList));
                if (((AccessRestriction) ((ConfigManager) this.getConfigManager()).getSetting(Settings.ACCESS))
                        .hasPermission(AccessRestriction.READ)) {
                    inv.addListener(this, this.handler);
                }
            }
        }
        if (wasSleeping != (this.monitor == null)) {
            try {
                ITickManager tm = this.getProxy().getTick();
                if (this.monitor == null) {
                    tm.sleepDevice(this.getProxy().getNode());
                } else {
                    tm.wakeDevice(this.getProxy().getNode());
                }
            } catch (GridAccessException ignored) {
            }
        }
        try {
            ((GridStorageCache) this.getProxy().getGrid().getCache(IStorageGrid.class)).cellUpdate(null);
        } catch (GridAccessException ignored) {
        }
        return this.handler;
    }

    @Override
    public List<IMEInventoryHandler> getCellArray(IStorageChannel channel) {
        MEInventoryHandler out;
        if (ChannelRegistrationManager.isEnergyChannel(channel) && (out = this.getInternalHandler()) != null) {
            return Collections.singletonList(out);
        }
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.resetCache(true);
    }

    @Override
    public void blinkCell(int slot) {
    }

    @Override
    public void saveChanges(ICellInventory<?> cellInventory) {
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        if (this.isPowered()) {
            return MODELS_ON;
        }
        return MODELS_OFF;
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return new ItemStack(PartRegistry.PART_ENERGY_STORAGE_BUS);
    }

    /**
     * 优先级 GUI 的返回按钮目标.返回 null 表示不显示返回按钮
     * (本总线 GUI 走项目自有 GuiHandler,无法通过 AE2 GuiBridge 回链).
     */
    @Override
    public appeng.core.sync.GuiBridge getGuiBridge() {
        return null;
    }

    protected Iterable filterChanges(Iterable change) {
        Enum<?> storageFilter = this.getConfigManager().getSetting(Settings.STORAGE_FILTER);
        if (storageFilter == StorageFilter.EXTRACTABLE_ONLY && this.handler != null) {
            ArrayList<IAEStack> filteredList = new ArrayList<>();
            for (Object o : change) {
                IAEStack stack = (IAEStack) o;
                if (!this.handler.passesBlackOrWhitelist(stack)) {
                    continue;
                }
                filteredList.add(stack);
            }
            return filteredList;
        }
        return change;
    }

    private IStorageChannel energyChannel() {
        return EnergyChannelResolver.getChannel();
    }
}
