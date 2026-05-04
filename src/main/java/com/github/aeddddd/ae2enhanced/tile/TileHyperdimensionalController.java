package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.ICellInventory;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.storage.FluidStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.OptionalStorageManager;
import com.github.aeddddd.ae2enhanced.storage.SimpleMEMonitor;
import appeng.api.AEApi;
import appeng.api.storage.IMEInventoryHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 超维度仓储中枢核心控制器。
 * 实现 IGridProxyable + ICellContainer，AE2-UEL 通过 ICellContainer 发现存储。
 */
public class TileHyperdimensionalController extends TileEntity implements IGridProxyable, ICellContainer, ITickable {

    private boolean formed = false;
    private boolean needsReady = false;
    private AENetworkProxy proxy;
    private UUID nexusId;
    private HyperdimensionalStorageFile storageFile;
    private ItemStorageAdapter itemAdapter;
    private FluidStorageAdapter fluidAdapter;
    private OptionalStorageManager optionalStorage;
    private SimpleMEMonitor itemMonitor;

    private boolean networkActive = false;
    private boolean networkPowered = false;
    private boolean clientSafeMode = false;
    private int tickCounter = 0;
    private int cellArrayRetry = 0;

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

    public ItemStorageAdapter getItemAdapter() {
        return itemAdapter;
    }

    public FluidStorageAdapter getFluidAdapter() {
        return fluidAdapter;
    }

    public SimpleMEMonitor getItemMonitor() {
        return itemMonitor;
    }

    // ---- IGridProxyable / IGridHost ----

    private AENetworkProxy createProxy() {
        AENetworkProxy p = new AENetworkProxy(this, "hyperdimensional_controller",
            new net.minecraft.item.ItemStack(ModBlocks.HYPERDIMENSIONAL_CONTROLLER), true);
        p.setValidSides(java.util.EnumSet.allOf(EnumFacing.class));
        return p;
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = createProxy();
        }
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return getProxy().getNode();
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        // 控制器本身不允许线缆直接连接，AE 接入必须通过 ME 接口
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
        List<IMEInventoryHandler> optional = optionalStorage.getHandlers(channel);
        if (!optional.isEmpty()) return optional;
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
        // 我们的存储不是 cell-based，由 ItemStorageAdapter 自行管理持久化
    }

    // ---- Lifecycle ----

    @Override
    public void validate() {
        super.validate();
        needsReady = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (proxy != null) {
            proxy.invalidate();
        }
        closeStorage();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (proxy != null) {
            proxy.onChunkUnload();
        }
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
            // 强制触发 GridStorageCache 重新扫描 handlers，确保物品/流体都被正确注册
            try {
                appeng.api.networking.IGrid grid = getProxy().getGrid();
                if (grid != null) {
                    grid.postEvent(new appeng.api.networking.events.MENetworkCellArrayUpdate());
                }
            } catch (appeng.me.GridAccessException e) {
                // grid 尚未就绪，下次 tick 的 update() 会重试
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
            itemAdapter.setOnChangeCallback(this::refreshNetworkMonitor);
            itemAdapter.setPostChangeCallback(this::postItemAlteration);
            itemMonitor = new SimpleMEMonitor(itemAdapter);

            fluidAdapter = new FluidStorageAdapter(storageFile);
            storageFile.setFluidStorageRef(fluidAdapter.getStorageMap());
            fluidAdapter.setOnChangeCallback(this::refreshNetworkMonitor);
            fluidAdapter.setPostChangeCallback(this::postFluidAlteration);

            optionalStorage = new OptionalStorageManager();
            optionalStorage.init(storageFile);
            if (optionalStorage.getGasAdapter() != null) {
                storageFile.setGasStorageRef(optionalStorage.getGasAdapter().getStorageMap());
                optionalStorage.getGasAdapter().setOnChangeCallback(this::refreshNetworkMonitor);
                optionalStorage.getGasAdapter().setPostChangeCallback(this::postGasAlteration);
            }
            if (optionalStorage.getEssentiaAdapter() != null) {
                storageFile.setEssentiaStorageRef(optionalStorage.getEssentiaAdapter().getStorageMap());
                optionalStorage.getEssentiaAdapter().setOnChangeCallback(this::refreshNetworkMonitor);
                optionalStorage.getEssentiaAdapter().setPostChangeCallback(this::postEssentiaAlteration);
            }
        }
    }

    // 缓存 AE2 NetworkMonitor 反射，避免高频 IO 时重复反射查找
    private static final java.lang.reflect.Field FORCE_UPDATE_FIELD;
    private static final java.lang.reflect.Method FORCE_UPDATE_METHOD;
    private static final java.lang.reflect.Field SEND_EVENT_FIELD;
    private static final java.lang.reflect.Method ON_TICK_METHOD;
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

    /**
     * 强制刷新 AE2 NetworkMonitor 缓存，使终端立即显示最新存储内容。
     * AE2-UEL 的 forceUpdate() 只刷新服务端缓存，不会触发客户端同步事件。
     * 必须同时设置 sendEvent=true 并调用 onTick()，让 NetworkMonitor 发送
     * MENetworkStorageEvent 到客户端，终端才会实时刷新。
     */
    private void refreshNetworkMonitor() {
        if (FORCE_UPDATE_FIELD == null || FORCE_UPDATE_METHOD == null) return;
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;

            // 刷新物品 monitor
            appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEItemStack> itemMonitor = storageGrid.getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class)
            );
            if (itemMonitor != null) {
                FORCE_UPDATE_FIELD.setBoolean(itemMonitor, true);
                if (SEND_EVENT_FIELD != null) SEND_EVENT_FIELD.setBoolean(itemMonitor, true);
                if (ON_TICK_METHOD != null) {
                    ON_TICK_METHOD.invoke(itemMonitor);
                } else {
                    FORCE_UPDATE_METHOD.invoke(itemMonitor);
                }
            }

            // 刷新流体 monitor
            appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEFluidStack> fluidMonitor = storageGrid.getInventory(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IFluidStorageChannel.class)
            );
            if (fluidMonitor != null) {
                FORCE_UPDATE_FIELD.setBoolean(fluidMonitor, true);
                if (SEND_EVENT_FIELD != null) SEND_EVENT_FIELD.setBoolean(fluidMonitor, true);
                if (ON_TICK_METHOD != null) {
                    ON_TICK_METHOD.invoke(fluidMonitor);
                } else {
                    FORCE_UPDATE_METHOD.invoke(fluidMonitor);
                }
            }

            // 刷新可选存储 monitor
            if (optionalStorage != null) {
                try {
                    if (optionalStorage.getGasAdapter() != null) {
                        Object gasMonitor = storageGrid.getInventory(
                            AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class)
                        );
                        if (gasMonitor != null) {
                            FORCE_UPDATE_FIELD.setBoolean(gasMonitor, true);
                            if (SEND_EVENT_FIELD != null) SEND_EVENT_FIELD.setBoolean(gasMonitor, true);
                            if (ON_TICK_METHOD != null) ON_TICK_METHOD.invoke(gasMonitor);
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to refresh gas monitor", e);
                }
                try {
                    if (optionalStorage.getEssentiaAdapter() != null) {
                        Object essentiaMonitor = storageGrid.getInventory(
                            AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class)
                        );
                        if (essentiaMonitor != null) {
                            FORCE_UPDATE_FIELD.setBoolean(essentiaMonitor, true);
                            if (SEND_EVENT_FIELD != null) SEND_EVENT_FIELD.setBoolean(essentiaMonitor, true);
                            if (ON_TICK_METHOD != null) ON_TICK_METHOD.invoke(essentiaMonitor);
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to refresh essentia monitor", e);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | appeng.me.GridAccessException e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                "[AE2E] Failed to refresh NetworkMonitor cache", e);
        }
    }

    /**
     * 通过 IStorageGrid.postAlterationOfStoredItems 通知 AE2 网络物品发生变化。
     * 这是 AE2 标准的增量更新路径，能正确处理物品完全消失的情况（forceUpdate 全量扫描无法处理）。
     */
    private void postItemAlteration(appeng.api.storage.data.IAEItemStack change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class),
                java.util.Collections.singletonList(change), src);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                "[AE2E] Failed to post item alteration", e);
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
                java.util.Collections.singletonList(change), src);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                "[AE2E] Failed to post fluid alteration", e);
        }
    }

    private void postGasAlteration(com.mekeng.github.common.me.data.IAEGasStack change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                appeng.api.AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class),
                java.util.Collections.singletonList(change), src);
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to post gas alteration", e);
        }
    }

    private void postEssentiaAlteration(thaumicenergistics.api.storage.IAEEssentiaStack change, appeng.api.networking.security.IActionSource src) {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return;
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return;
            storageGrid.postAlterationOfStoredItems(
                appeng.api.AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class),
                java.util.Collections.singletonList(change), src);
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
            // 同时触发 neighbor change，让 AE2 线缆重新检查连接
            world.notifyNeighborsOfStateChange(actual, state.getBlock(), false);
        }
    }

    private void closeStorage() {
        if (storageFile != null) {
            storageFile.close();
            storageFile = null;
            itemAdapter = null;
            fluidAdapter = null;
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
            // 通知 ME 接口位置方块更新，触发 AE2 重新检查节点连接
            notifyMeInterfacesOfStateChange();
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
                // 网格尚未就绪，继续重试
            }
        }

        tickCounter++;
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

            // 更新存储统计并同步到客户端（物品 + 流体 + 可选存储）
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
        // 保留一位小数（如 1.2K），当整数部分小于 100 时
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
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        if (nexusId != null) {
            compound.setUniqueId("nexusId", nexusId);
        }
        return compound;
    }

    @Nullable
    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
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
}
