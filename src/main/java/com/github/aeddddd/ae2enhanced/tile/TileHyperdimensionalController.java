package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;
import com.github.aeddddd.ae2enhanced.storage.EnergyDescriptor;
import com.github.aeddddd.ae2enhanced.storage.FluidStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalEnergyStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.storage.ManaDescriptor;
import com.github.aeddddd.ae2enhanced.storage.ManaStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.StarlightStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.IStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.OptionalStorageManager;
import com.github.aeddddd.ae2enhanced.storage.SimpleMEMonitor;
import com.github.aeddddd.ae2enhanced.storage.channel.ChannelRegistrationManager;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.mana.ManaChannelResolver;
import appeng.api.AEApi;
import appeng.api.storage.IMEInventoryHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 超维度仓储中枢核心控制器.
 * 实现 IGridProxyable + ICellContainer,AE2-UEL 通过 ICellContainer 发现存储.
 */
public class TileHyperdimensionalController extends TileAENetworkBase implements ICellContainer, ITickable {

    private boolean formed = false;

    private UUID nexusId;
    private HyperdimensionalStorageFile storageFile;
    private ItemStorageAdapter itemAdapter;
    private FluidStorageAdapter fluidAdapter;
    private IStorageAdapter energyAdapter;
    private IStorageAdapter manaAdapter;
    private StarlightStorageAdapter starlightAdapter;
    private OptionalStorageManager optionalStorage;
    private SimpleMEMonitor itemMonitor;

    private boolean networkActive = false;
    private boolean networkPowered = false;
    private boolean clientSafeMode = false;
    private int tickCounter = 0;
    private int cellArrayRetry = 0;
    private int delayedNotifyTick = 0;

    // 缓存 MachineSource,避免每次 postAlteration 都重新创建
    private final appeng.me.helpers.MachineSource machineSource = new appeng.me.helpers.MachineSource(this);

    // 客户端同步的存储统计
    private int clientStorageTypes = 0;
    private String clientStorageTotal = "0";
    private String clientStorageTotalRaw = "0";

    public boolean isFormed() {
        return formed;
    }

    public UUID getNexusId() {
        return nexusId;
    }

    public void setNexusId(UUID nexusId) {
        this.nexusId = nexusId;
        initStorage();
        markDirty();
    }

    public ItemStorageAdapter getItemAdapter() {
        return itemAdapter;
    }

    public FluidStorageAdapter getFluidAdapter() {
        return fluidAdapter;
    }

    public SimpleMEMonitor getItemMonitor() {
        return itemMonitor;
    }

    @Override
    protected String getProxyName() {
        return "hyperdimensional_controller";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER);
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        disassemble();
    }

    // ---- IActionHost (via ICellContainer) ----

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    // ---- ICellProvider (via ICellContainer) ----

    @Override
    public List<appeng.api.storage.IMEInventoryHandler> getCellArray(appeng.api.storage.IStorageChannel<?> channel) {
        if (!formed) return Collections.emptyList();
        if (channel instanceof appeng.api.storage.channels.IItemStorageChannel && itemAdapter != null) {
            return Collections.singletonList(itemAdapter);
        }
        if (channel instanceof appeng.api.storage.channels.IFluidStorageChannel && fluidAdapter != null) {
            return Collections.singletonList(fluidAdapter);
        }
        if (ChannelRegistrationManager.isEnergyChannel(channel) && energyAdapter != null) {
            return Collections.singletonList((IMEInventoryHandler) energyAdapter);
        }
        if (ChannelRegistrationManager.isManaChannel(channel) && manaAdapter != null) {
            return Collections.singletonList((IMEInventoryHandler) manaAdapter);
        }
        if (ChannelRegistrationManager.isStarlightChannel(channel) && starlightAdapter != null) {
            return Collections.singletonList(starlightAdapter);
        }
        if (optionalStorage != null) {
            List<IMEInventoryHandler> optional = optionalStorage.getHandlers(channel);
            if (!optional.isEmpty()) {
                return optional;
            }
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

    // ---- ISaveProvider (via ICellContainer) ----

    @Override
    public void saveChanges(ICellInventory<?> inv) {
        // 我们的存储不是 cell-based,由 ItemStorageAdapter 自行管理持久化
    }

    // ---- Lifecycle ----

    @Override
    protected void onProxyInvalidate() {
        closeStorage();
    }

    @Override
    protected void onProxyChunkUnload() {
        closeStorage();
    }

    public void assemble() {
        if (!formed) {
            formed = true;
            if (nexusId == null) {
                nexusId = UUID.randomUUID();
            }
            initStorage();
            markDirty();
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            }
            getProxy().onReady();
            cellArrayRetry = 5;
            notifyMeInterfacesOfStateChange();
            updateCableConnections();
            // 强制触发 GridStorageCache 重新扫描 handlers,确保物品/流体都被正确注册
            try {
                appeng.api.networking.IGrid grid = getProxy().getGrid();
                if (grid != null) {
                    grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
                }
            } catch (appeng.me.GridAccessException e) {
                // grid 尚未就绪,下次 tick 的 update() 会重试
            }
        }
    }

    public void disassemble() {
        if (formed) {
            formed = false;
            markDirty();
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            }
            if (proxy != null) {
                proxy.invalidate();
            }
            closeStorage();
        }
    }

    private void initStorage() {
        if (world == null || world.isRemote) return;
        if (storageFile == null) {
            storageFile = new HyperdimensionalStorageFile(world, nexusId);
            itemAdapter = new ItemStorageAdapter(storageFile);
            storageFile.setStorageRef(itemAdapter.getStorageMap());
            itemAdapter.setOnChangeCallback(null);
            itemAdapter.setPostChangeCallback(this::postItemAlteration);
            itemMonitor = new SimpleMEMonitor(itemAdapter);

            fluidAdapter = new FluidStorageAdapter(storageFile);
            storageFile.setFluidStorageRef(fluidAdapter.getStorageMap());
            fluidAdapter.setOnChangeCallback(null);
            fluidAdapter.setPostChangeCallback(this::postFluidAlteration);

            energyAdapter = createEnergyAdapter();
            storageFile.setEnergyStorageRef((java.util.Map<EnergyDescriptor, java.math.BigInteger>) (java.util.Map<?, ?>) energyAdapter.getStorageMap());

            manaAdapter = createManaAdapter();
            if (manaAdapter != null) {
                storageFile.setManaStorageRef(manaAdapter.getStorageMap());
            }

            if (net.minecraftforge.fml.common.Loader.isModLoaded("astralsorcery")) {
                starlightAdapter = new StarlightStorageAdapter(storageFile);
                storageFile.setStarlightStorageRef(starlightAdapter.getStorageMap());
                starlightAdapter.setOnChangeCallback(null);
                starlightAdapter.setPostChangeCallback(this::postStarlightAlteration);
            }

            optionalStorage = new OptionalStorageManager();
            optionalStorage.init(storageFile);

            Object gasAdapter = optionalStorage.getGasAdapter();
            if (gasAdapter != null) {
                try {
                    Object map = gasAdapter.getClass().getMethod("getStorageMap").invoke(gasAdapter);
                    if (map instanceof java.util.Map) storageFile.setGasStorageRef((java.util.Map) map);
                    java.util.function.BiConsumer<Object, appeng.api.networking.security.IActionSource> gasCallback = this::postGasAlteration;
                    gasAdapter.getClass().getMethod("setOnChangeCallback", Runnable.class).invoke(gasAdapter, (Runnable) null);
                    gasAdapter.getClass().getMethod("setPostChangeCallback", java.util.function.BiConsumer.class).invoke(gasAdapter, gasCallback);
                } catch (Exception e) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to setup gas adapter callbacks", e);
                }
            }
            Object essentiaAdapter = optionalStorage.getEssentiaAdapter();
            if (essentiaAdapter != null) {
                try {
                    Object map = essentiaAdapter.getClass().getMethod("getStorageMap").invoke(essentiaAdapter);
                    if (map instanceof java.util.Map) storageFile.setEssentiaStorageRef((java.util.Map) map);
                    java.util.function.BiConsumer<Object, appeng.api.networking.security.IActionSource> essentiaCallback = this::postEssentiaAlteration;
                    essentiaAdapter.getClass().getMethod("setOnChangeCallback", Runnable.class).invoke(essentiaAdapter, (Runnable) null);
                    essentiaAdapter.getClass().getMethod("setPostChangeCallback", java.util.function.BiConsumer.class).invoke(essentiaAdapter, essentiaCallback);
                } catch (Exception e) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to setup essentia adapter callbacks", e);
                }
            }
        }
    }

    /**
     * 创建能量适配器:优先使用 Flux_Applied 外部通道,否则回退到 AE2E 自有通道.
     */
    private IStorageAdapter createEnergyAdapter() {
        if (FluxAppliedCompat.isFluxStorageChannelAvailable()) {
            ExternalStorageAdapter<EnergyDescriptor> adapter = new ExternalStorageAdapter<>(
                    storageFile,
                    FluxAppliedCompat.getFluxStorageChannelInstance(),
                    "fe",
                    EnergyDescriptor.INSTANCE
            );
            storageFile.loadEnergy((java.util.Map<EnergyDescriptor, java.math.BigInteger>) (java.util.Map<?, ?>) adapter.getStorageMap());
            adapter.recalcTotal();
            adapter.setOnChangeCallback(null);
            adapter.setPostChangeCallback(this::postEnergyAlteration);
            return adapter;
        }
        HyperdimensionalEnergyStorageAdapter adapter = new HyperdimensionalEnergyStorageAdapter(storageFile);
        adapter.setOnChangeCallback(null);
        adapter.setPostChangeCallback(this::postEnergyAlteration);
        return adapter;
    }

    /**
     * 创建 Mana 适配器:优先使用 Botania_Applie 外部通道,否则在 Botania 存在时回退到 AE2E 自有通道.
     */
    private IStorageAdapter createManaAdapter() {
        if (BotaniaApplieCompat.isManaStorageChannelAvailable()) {
            ExternalStorageAdapter<ManaDescriptor> adapter = new ExternalStorageAdapter<>(
                    storageFile,
                    BotaniaApplieCompat.getManaStorageChannelInstance(),
                    "mana",
                    ManaDescriptor.INSTANCE
            );
            storageFile.loadMana((java.util.Map<ManaDescriptor, java.math.BigInteger>) (java.util.Map<?, ?>) adapter.getStorageMap());
            adapter.recalcTotal();
            adapter.setOnChangeCallback(null);
            adapter.setPostChangeCallback(this::postManaAlteration);
            return adapter;
        } else if (net.minecraftforge.fml.common.Loader.isModLoaded("botania")) {
            ManaStorageAdapter adapter = new ManaStorageAdapter(storageFile);
            adapter.setOnChangeCallback(null);
            adapter.setPostChangeCallback(this::postManaAlteration);
            return adapter;
        }
        return null;
    }

    // 缓存 AE2 NetworkMonitor 反射,避免高频 IO 时重复反射查找
    private static final java.lang.reflect.Field FORCE_UPDATE_FIELD;
    private static final java.lang.reflect.Method FORCE_UPDATE_METHOD;
    private static final java.lang.reflect.Field SEND_EVENT_FIELD;
    private static final java.lang.reflect.Method ON_TICK_METHOD;

    // 缓存可选存储通道的 Class 与实例,避免每次变更都反射查找
    private static final Class<?> GAS_STORAGE_CHANNEL_CLASS = optionalChannelClass("com.mekeng.github.common.me.storage.IGasStorageChannel");
    private static final Object GAS_STORAGE_CHANNEL = optionalChannelInstance(GAS_STORAGE_CHANNEL_CLASS);
    private static final Class<?> ESSENTIA_STORAGE_CHANNEL_CLASS = optionalChannelClass("thaumicenergistics.api.storage.IEssentiaStorageChannel");
    private static final Object ESSENTIA_STORAGE_CHANNEL = optionalChannelInstance(ESSENTIA_STORAGE_CHANNEL_CLASS);


    static {
        java.lang.reflect.Field f = null;
        java.lang.reflect.Method m = null;
        java.lang.reflect.Field se = null;
        java.lang.reflect.Method ot = null;
        try {
            Class<?> clazz = Class.forName("appeng.me.cache.NetworkMonitor");
            f = clazz.getDeclaredField("forceUpdate");
            f.setAccessible(true);
            m = clazz.getDeclaredMethod("forceUpdate");
            m.setAccessible(true);
            se = clazz.getDeclaredField("sendEvent");
            se.setAccessible(true);
            ot = clazz.getDeclaredMethod("onTick");
            ot.setAccessible(true);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                "[AE2E] Failed to cache NetworkMonitor reflection. ME terminal refresh will not work.", e);
        }
        FORCE_UPDATE_FIELD = f;
        FORCE_UPDATE_METHOD = m;
        SEND_EVENT_FIELD = se;
        ON_TICK_METHOD = ot;
    }

    private static Class<?> optionalChannelClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object optionalChannelInstance(Class<?> clazz) {
        if (clazz == null) return null;
        try {
            return appeng.api.AEApi.instance().storage().getStorageChannel((Class) clazz);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get optional storage channel for {}", clazz.getName(), e);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void postOptionalAlteration(appeng.api.networking.storage.IStorageGrid storageGrid,
                                               Object channel, Object change,
                                               appeng.api.networking.security.IActionSource src) {
        if (channel == null) return;
        java.lang.Iterable list = java.util.Collections.singletonList(change);
        storageGrid.postAlterationOfStoredItems((appeng.api.storage.IStorageChannel) channel, list, src);
    }

    /**
     * 兜底全量刷新 AE2 NetworkMonitor 缓存.
     *
     * <p>正常流程下,{@code postAlterationOfStoredItems} 已通过增量更新
     * 实时维护了 NetworkMonitor 的 {@code cachedList} 与 {@code sendEvent},
     * 终端在下一次 {@code onTick()} 即可收到 {@code MENetworkStorageEvent}.</p>
     *
     * <p>此方法仅在配置的 {@code monitorFullScanIntervalTicks} 间隔触发,
     * 作为安全网纠正极端边缘场景(chunk 加载竞争、网络分裂等)下可能产生的
     * 缓存漂移.日常高频读写不会调用它.</p>
     */
    private void refreshNetworkMonitor() {
        if (FORCE_UPDATE_FIELD == null || FORCE_UPDATE_METHOD == null) return;
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;

            refreshSingleMonitor(storageGrid.getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class)
            ));
            refreshSingleMonitor(storageGrid.getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IFluidStorageChannel.class)
            ));
            refreshSingleMonitor(storageGrid.getInventory(EnergyChannelResolver.getChannel()));

            if (manaAdapter != null) {
                refreshSingleMonitor(storageGrid.getInventory(ManaChannelResolver.getChannel()));
            }
            if (starlightAdapter != null) {
                refreshOptionalMonitor(storageGrid, starlightAdapter,
                        "com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel", "starlight");
            }
            if (optionalStorage != null) {
                refreshOptionalMonitor(storageGrid, optionalStorage.getGasAdapter(),
                        "com.mekeng.github.common.me.storage.IGasStorageChannel", "gas");
                refreshOptionalMonitor(storageGrid, optionalStorage.getEssentiaAdapter(),
                        "thaumicenergistics.api.storage.IEssentiaStorageChannel", "essentia");
            }
        } catch (RuntimeException | appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to refresh NetworkMonitor cache", e);
        }
    }

    private void refreshSingleMonitor(Object monitor) {
        if (monitor == null) return;
        try {
            FORCE_UPDATE_FIELD.setBoolean(monitor, true);
            if (SEND_EVENT_FIELD != null) SEND_EVENT_FIELD.setBoolean(monitor, true);
            if (ON_TICK_METHOD != null) {
                ON_TICK_METHOD.invoke(monitor);
            } else {
                FORCE_UPDATE_METHOD.invoke(monitor);
            }
        } catch (ReflectiveOperationException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to refresh monitor", e);
        }
    }

    private void refreshOptionalMonitor(appeng.api.networking.storage.IStorageGrid storageGrid,
                                         Object adapter, String channelClassName, String typeName) {
        if (adapter == null) return;
        try {
            Class<?> channelClass = Class.forName(channelClassName);
            java.lang.reflect.Method getChannel = AEApi.instance().storage().getClass().getMethod("getStorageChannel", Class.class);
            Object channel = getChannel.invoke(AEApi.instance().storage(), channelClass);
            java.lang.reflect.Method getInventory = storageGrid.getClass().getMethod("getInventory", appeng.api.storage.IStorageChannel.class);
            Object monitor = getInventory.invoke(storageGrid, channel);
            refreshSingleMonitor(monitor);
        } catch (ReflectiveOperationException | RuntimeException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to refresh {} monitor", typeName, e);
        }
    }

    /**
     * 通过 IStorageGrid.postAlterationOfStoredItems 通知 AE2 网络物品发生变化.
     * 这是 AE2 标准的增量更新路径,能正确处理物品完全消失的情况(forceUpdate 全量扫描无法处理).
     */
    private void postItemAlteration(appeng.api.storage.data.IAEItemStack change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            // 使用 MachineSource 替代原始 src，避免 CELLS Subnet Proxy 因 origin grid 判定而过滤通知
            storageGrid.postAlterationOfStoredItems(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class),
                java.util.Collections.singletonList(change), machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                "[AE2E] Failed to post item alteration", e);
        }
    }

    private void postEnergyAlteration(appeng.api.storage.data.IAEStack<?> change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                EnergyChannelResolver.getChannel(),
                java.util.Collections.singletonList(change), machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                "[AE2E] Failed to post energy alteration", e);
        }
    }

    private void postManaAlteration(appeng.api.storage.data.IAEStack<?> change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                ManaChannelResolver.getChannel(),
                java.util.Collections.singletonList(change), machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to post mana alteration", e);
        }
    }

    private void postStarlightAlteration(com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                AEApi.instance().storage().getStorageChannel(com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel.class),
                java.util.Collections.singletonList(change), machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to post starlight alteration", e);
        }
    }

    private void postFluidAlteration(appeng.api.storage.data.IAEFluidStack change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IFluidStorageChannel.class),
                java.util.Collections.singletonList(change), machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                "[AE2E] Failed to post fluid alteration", e);
        }
    }

    private void postGasAlteration(Object change, appeng.api.networking.security.IActionSource src) {
        if (GAS_STORAGE_CHANNEL == null) return;
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            postOptionalAlteration(storageGrid, GAS_STORAGE_CHANNEL, change, machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to post gas alteration", e);
        }
    }

    private void postEssentiaAlteration(Object change, appeng.api.networking.security.IActionSource src) {
        if (ESSENTIA_STORAGE_CHANNEL == null) return;
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            postOptionalAlteration(storageGrid, ESSENTIA_STORAGE_CHANNEL, change, machineSource);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to post essentia alteration", e);
        }
    }

    private void notifyMeInterfacesOfStateChange() {
        if (world == null || world.isRemote) return;
        IBlockState controllerState = world.getBlockState(pos);
        if (!(controllerState.getBlock() instanceof BlockHyperdimensionalController)) return;
        EnumFacing facing = controllerState.getValue(BlockHyperdimensionalController.FACING);
        for (net.minecraft.util.math.BlockPos rel : com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure.ME_INTERFACE_SET) {
            net.minecraft.util.math.BlockPos actual = pos.add(com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure.rotate(rel, facing));
            IBlockState state = world.getBlockState(actual);
            world.notifyBlockUpdate(actual, state, state, 2);
            world.notifyNeighborsOfStateChange(actual, state.getBlock(), false);
        }
    }

    /**
     * 直接刷新 ME 接口相邻位置的 AE2 线缆连接.
     * ComputationCore 使用相同机制(调用 CableBusContainer.updateConnections),
     * 比依赖 Minecraft 的 neighbor notification 更可靠,能确保时序不一致时仍能正确连接.
     */
    private void updateCableConnections() {
        if (world == null || world.isRemote) return;
        IBlockState controllerState = world.getBlockState(pos);
        if (!(controllerState.getBlock() instanceof BlockHyperdimensionalController)) return;
        EnumFacing facing = controllerState.getValue(BlockHyperdimensionalController.FACING);
        for (net.minecraft.util.math.BlockPos rel : com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure.ME_INTERFACE_SET) {
            net.minecraft.util.math.BlockPos actual = pos.add(com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure.rotate(rel, facing));
            for (EnumFacing dir : EnumFacing.values()) {
                net.minecraft.util.math.BlockPos neighborPos = actual.offset(dir);
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(neighborPos);
                if (te instanceof appeng.tile.networking.TileCableBus) {
                    appeng.parts.CableBusContainer cbc = ((appeng.tile.networking.TileCableBus) te).getCableBus();
                    if (cbc != null) {
                        cbc.updateConnections();
                    }
                }
            }
        }
        // 当网络活跃时,强制触发 GridStorageCache 重新扫描存储,修复因时序导致 storageNetworks 未构建的问题
        if (itemAdapter != null) {
            try {
                appeng.api.networking.IGrid grid = getProxy().getGrid();
                if (grid != null) {
                    grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
                }
            } catch (appeng.me.GridAccessException e) {
                // ignore
            }
        }
    }

    private void closeStorage() {
        if (storageFile != null) {
            storageFile.close();
            storageFile = null;
            itemAdapter = null;
            fluidAdapter = null;
            energyAdapter = null;
            manaAdapter = null;
            starlightAdapter = null;
            itemMonitor = null;
        }
        if (optionalStorage != null) {
            optionalStorage.close();
            optionalStorage = null;
        }
    }

    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            // Client-side: energy flow particles converging toward the multiblock center
            if (formed && networkActive && world.rand.nextInt(6) == 0) {
                EnumFacing facing = EnumFacing.NORTH;
                if (world.getBlockState(pos).getBlock() instanceof BlockHyperdimensionalController) {
                    facing = world.getBlockState(pos).getValue(BlockHyperdimensionalController.FACING);
                }
                double offX, offZ;
                switch (facing) {
                    case SOUTH: offX = 0; offZ = -2.0; break;
                    case EAST:  offX = -2.0; offZ = 0; break;
                    case WEST:  offX = 2.0; offZ = 0; break;
                    default:    offX = 0; offZ = 2.0; break;
                }
                double cx = pos.getX() + 0.5 + offX;
                double cy = pos.getY() + 1.5;
                double cz = pos.getZ() + 0.5 + offZ;
                double px = cx + (world.rand.nextDouble() - 0.5) * 4.0;
                double py = cy + (world.rand.nextDouble() - 0.5) * 2.0;
                double pz = cz + (world.rand.nextDouble() - 0.5) * 4.0;
                world.spawnParticle(net.minecraft.util.EnumParticleTypes.ENCHANTMENT_TABLE,
                    px, py, pz,
                    (cx - px) * 0.05,
                    (cy - py) * 0.05,
                    (cz - pz) * 0.05);
            }
            return;
        }

        if (needsReady && formed) {
            needsReady = false;
            initStorage();
            getProxy().onReady();
            cellArrayRetry = 5; // 5 秒内重试发送 CellArrayUpdate
            notifyMeInterfacesOfStateChange();
            updateCableConnections();
            delayedNotifyTick = 5;
        }

        if (delayedNotifyTick > 0) {
            delayedNotifyTick--;
            if (delayedNotifyTick == 0 && formed) {
                notifyMeInterfacesOfStateChange();
            }
        }

        if (cellArrayRetry > 0) {
            cellArrayRetry--;
            try {
                appeng.api.networking.IGrid grid = getProxy().getGrid();
                if (grid != null) {
                    grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
                    cellArrayRetry = 0;
                }
            } catch (appeng.me.GridAccessException e) {
                // 网格尚未就绪,继续重试
            }
        }

        tickCounter++;

        // 兜底全量扫描：由配置控制间隔,日常高频读写不会触发
        if (formed && AE2EnhancedConfig.storage.monitorFullScanIntervalTicks > 0
                && tickCounter % AE2EnhancedConfig.storage.monitorFullScanIntervalTicks == 0) {
            refreshNetworkMonitor();
        }

        if (formed && world.getTotalWorldTime() % 20 == 0) {
            updateCableConnections();
        }
        if (tickCounter % 20 == 0) {
            boolean newActive = false;
            boolean newPowered = false;
            if (formed) {
                AENetworkProxy p = getProxy();
                if (p != null) {
                    newActive = p.isActive();
                    newPowered = p.isPowered();
                }
            }

            boolean needUpdate = newActive != networkActive || newPowered != networkPowered;
            networkActive = newActive;
            networkPowered = newPowered;

            // 更新存储统计并同步到客户端(物品 + 流体 + 可选存储)
            int newTypes = 0;
            java.math.BigInteger newTotal = java.math.BigInteger.ZERO;
            if (itemAdapter != null) {
                newTypes += itemAdapter.getStorageMap().size();
                newTotal = newTotal.add(itemAdapter.getTotalCount());
            }
            if (fluidAdapter != null) {
                newTypes += fluidAdapter.getStorageMap().size();
                newTotal = newTotal.add(fluidAdapter.getTotalCount());
            }
            if (energyAdapter != null) {
                newTypes += energyAdapter.getStorageMap().size();
                newTotal = newTotal.add(energyAdapter.getTotalCount());
            }
            if (optionalStorage != null) {
                newTypes += optionalStorage.getTotalTypeCount();
                newTotal = newTotal.add(optionalStorage.getTotalCount());
            }
            String newTotalStr = formatBigNumber(newTotal);
            String newTotalRaw = newTotal.toString();
            if (newTypes != clientStorageTypes || !newTotalStr.equals(clientStorageTotal) || !newTotalRaw.equals(clientStorageTotalRaw)) {
                clientStorageTypes = newTypes;
                clientStorageTotal = newTotalStr;
                clientStorageTotalRaw = newTotalRaw;
                needUpdate = true;
            }

            boolean newSafeMode = isSafeMode();
            if (newSafeMode != clientSafeMode) {
                clientSafeMode = newSafeMode;
                needUpdate = true;
            }
            if (needUpdate) {
                markDirty();
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            }
        }
    }

    public boolean isNetworkActive() {
        return networkActive;
    }

    public boolean isNetworkPowered() {
        return networkPowered;
    }

    public int getClientStorageTypes() {
        return clientStorageTypes;
    }

    public String getClientStorageTotal() {
        return clientStorageTotal;
    }

    public String getClientStorageTotalRaw() {
        return clientStorageTotalRaw;
    }

    public boolean isSafeMode() {
        return (itemAdapter != null && itemAdapter.isSafeMode())
            || (fluidAdapter != null && fluidAdapter.isSafeMode())
            || (energyAdapter != null && energyAdapter.isSafeMode())
            || (manaAdapter != null && manaAdapter.isSafeMode())
            || (starlightAdapter != null && starlightAdapter.isSafeMode())
            || (optionalStorage != null && optionalStorage.isSafeMode());
    }

    public boolean getClientSafeMode() {
        return clientSafeMode;
    }

    private static final String[] NUMBER_UNITS = {"", "K", "M", "G", "T", "P", "E", "Z", "Y"};
    private static final String[] NUMBER_UNIT_NAMES = {"", "Thousand", "Million", "Billion", "Trillion", "Quadrillion", "Quintillion", "Sextillion", "Septillion"};
    private static final java.math.BigInteger ONE_E27 = new java.math.BigInteger("1000000000000000000000000000");

    public static String toScientificNotation(java.math.BigInteger num) {
        String s = num.toString();
        int len = s.length();
        if (len <= 1) return s;
        StringBuilder sb = new StringBuilder();
        sb.append(s.charAt(0));
        if (len > 1) {
            sb.append('.');
            int end = Math.min(len, 4);
            sb.append(s.substring(1, end));
            while (sb.charAt(sb.length() - 1) == '0') {
                sb.setLength(sb.length() - 1);
            }
            if (sb.charAt(sb.length() - 1) == '.') {
                sb.setLength(sb.length() - 1);
            }
        }
        sb.append(" × 10^").append(len - 1);
        return sb.toString();
    }

    private static String formatBigNumber(java.math.BigInteger num) {
        if (num.compareTo(ONE_E27) >= 0) {
            return toScientificNotation(num);
        }
        if (num.compareTo(java.math.BigInteger.valueOf(1000)) < 0) {
            return num.toString();
        }
        java.math.BigInteger thousand = java.math.BigInteger.valueOf(1000);
        int unitIndex = 0;
        java.math.BigInteger whole = num;
        java.math.BigInteger frac = java.math.BigInteger.ZERO;
        while (whole.compareTo(thousand) >= 0 && unitIndex < NUMBER_UNITS.length - 1) {
            frac = whole.mod(thousand);
            whole = whole.divide(thousand);
            unitIndex++;
        }
        // 保留一位小数(如 1.2K),当整数部分小于 100 时
        if (whole.compareTo(java.math.BigInteger.valueOf(100)) < 0 && unitIndex > 0) {
            int decimal = frac.multiply(java.math.BigInteger.valueOf(10)).divide(thousand).intValue();
            return whole + "." + decimal + NUMBER_UNITS[unitIndex];
        }
        return whole + NUMBER_UNITS[unitIndex];
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        formed = compound.getBoolean("formed");
        if (compound.hasUniqueId("nexusId")) {
            nexusId = compound.getUniqueId("nexusId");
        }
        getProxy().readFromNBT(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        if (nexusId != null) {
            compound.setUniqueId("nexusId", nexusId);
        }
        getProxy().writeToNBT(compound);
        return compound;
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Nullable
    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        // 确保在客户端请求同步前,存储已初始化(避免重载后信息面板显示 0)
        if (formed && itemAdapter == null && nexusId != null && world != null && !world.isRemote) {
            initStorage();
        }
        NBTTagCompound tag = super.getUpdateTag();
        tag.setBoolean("formed", formed);
        tag.setBoolean("networkActive", networkActive);
        tag.setBoolean("networkPowered", networkPowered);
        tag.setInteger("storageTypes", clientStorageTypes);
        tag.setString("storageTotal", clientStorageTotal);
        tag.setString("storageTotalRaw", clientStorageTotalRaw);
        tag.setBoolean("safeMode", clientSafeMode);
        if (nexusId != null) {
            tag.setUniqueId("nexusId", nexusId);
        }
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        NBTTagCompound tag = pkt.getNbtCompound();
        formed = tag.getBoolean("formed");
        networkActive = tag.getBoolean("networkActive");
        networkPowered = tag.getBoolean("networkPowered");
        clientStorageTypes = tag.getInteger("storageTypes");
        clientStorageTotal = tag.getString("storageTotal");
        clientStorageTotalRaw = tag.getString("storageTotalRaw");
        clientSafeMode = tag.getBoolean("safeMode");
        if (tag.hasUniqueId("nexusId")) {
            nexusId = tag.getUniqueId("nexusId");
        }
    }

    @Override
    @Nonnull
    public AxisAlignedBB getRenderBoundingBox() {
        if (world == null) return super.getRenderBoundingBox();
        if (!(world.getBlockState(pos).getBlock() instanceof BlockHyperdimensionalController)) {
            return super.getRenderBoundingBox();
        }
        EnumFacing facing = world.getBlockState(pos).getValue(BlockHyperdimensionalController.FACING);
        double offX, offZ;
        switch (facing) {
            case SOUTH: offX = 0; offZ = -2.0; break;
            case EAST:  offX = -2.0; offZ = 0; break;
            case WEST:  offX = 2.0; offZ = 0; break;
            default:    offX = 0; offZ = 2.0; break;
        }
        double cx = pos.getX() + 0.5 + offX;
        double cy = pos.getY() + 4.0;
        double cz = pos.getZ() + 0.5 + offZ;
        double r = 5.5; // 覆盖超立方体全部渲染范围(半径约 4.5,留余量)
        return new AxisAlignedBB(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0; // 256 格渲染距离
    }
}
