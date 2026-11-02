package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.fluids.util.AEFluidStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.display.ChartType;
import com.github.aeddddd.ae2enhanced.display.DisplayTheme;
import com.github.aeddddd.ae2enhanced.display.TimeRange;
import com.github.aeddddd.ae2enhanced.display.TrendBuffer;
import com.github.aeddddd.ae2enhanced.display.YAxisMode;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayAction;
import com.github.aeddddd.ae2enhanced.display.packet.PacketDisplayTick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 趋势显示幕墙面板 TileEntity.
 *
 * <p>多方块:同朝向面板组成的实心矩形(宽 2~16,高 2~9),边框为装饰.
 * 每个面板都有 TE;成型后左下角(按 y,x,z 字典序最小)面板当选为 master,
 * 承担 AE 网络接入(不占频道,按面积耗能)、每秒采样与客户端同步.</p>
 *
 * <p>数据:三档降采样环形缓冲,NBT 持久化;chunk 同步走 update tag 全量,
 * 运行中每秒发一次 {@link PacketDisplayTick} 增量包.</p>
 */
public class TileDisplayPanel extends TileEntity implements ITickable, IGridProxyable {

    public static final int MIN_W = 2, MAX_W = 16;
    public static final int MIN_H = 2, MAX_H = 9;
    public static final int MAX_TRACKED = PacketDisplayTick.TRACKED_SLOTS;

    /** 结构校验/主节点心跳间隔(tick) */
    private static final int RESCAN_INTERVAL = 40;
    /** 增量数据包发送半径(格) */
    private static final double SYNC_RANGE = 64.0;

    // ---- 所有面板共有 ----
    private boolean formed = false;
    private boolean isMaster = false;
    private BlockPos masterPos = null;
    private int rectW = 0, rectH = 0;
    private boolean rescanQueued = true;

    // ---- 仅 master 有效 ----
    private AENetworkProxy proxy;
    private boolean proxyReady = false;
    private DisplayTheme theme = DisplayTheme.DARK;
    private ChartType chartType = ChartType.LINE;
    private TimeRange timeRange = TimeRange.M30;
    private YAxisMode yMode = YAxisMode.AUTO;
    private long fixedMax = 1000;
    private boolean powered = false;
    /** 采样序号:服务端每次采样递增,随 NBT/增量包同步,用于客户端缓冲对齐 */
    private long sampleTotal = 0;
    private final int[] slotColors = new int[MAX_TRACKED];
    private final boolean[] slotVisible = new boolean[MAX_TRACKED];
    private final TrendBuffer[] buffers = new TrendBuffer[MAX_TRACKED];
    private int tickCounter = 0;

    /** 客户端渲染用:AUTO 量程平滑过渡的当前上限(不持久化). */
    public float clientSmoothMax = -1;
    /** 客户端渲染用:最近一个采样包到达时的世界时间(用于平滑滚动插值). */
    private long clientLastTickTime = -1;

    /** 客户端渲染包围盒缓存(master 为整块屏幕的 AABB). */
    private AxisAlignedBB renderBB;

    /**
     * 渲染包围盒:master 必须覆盖整块屏幕,
     * 否则视锥剔除按 master 单方块 AABB 判定,master 移出视野时整屏消失.
     */
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        if (!isMasterRole()) {
            return super.getRenderBoundingBox();
        }
        if (renderBB == null) {
            EnumFacing f = getFacing();
            renderBB = f.getAxis() == EnumFacing.Axis.Z
                    ? new AxisAlignedBB(pos, pos.add(rectW, rectH, 1))
                    : new AxisAlignedBB(pos, pos.add(1, rectH, rectW));
        }
        return renderBB;
    }

    /**
     * 从 start 出发扫描同朝向面板矩形.
     * BFS 只经过面板方块(边框不可行走,保证相邻两块屏幕被边框正确隔离).
     */
    public static ScanResult scanRectangle(net.minecraft.world.World w, BlockPos start) {
        IBlockState startState = w.getBlockState(start);
        if (!TileBlockStates.is(startState, TileBlockStates.DISPLAY_PANEL)) return null;
        EnumFacing facing = TileBlockStates.facing(startState);
        // 平面内方向:水平轴 + Y 轴
        EnumFacing h1 = facing.getAxis() == EnumFacing.Axis.X ? EnumFacing.NORTH : EnumFacing.EAST;
        EnumFacing h2 = h1.getOpposite();

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.toImmutable());
        queue.add(start.toImmutable());
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (EnumFacing dir : new EnumFacing[]{h1, h2, EnumFacing.UP, EnumFacing.DOWN}) {
                BlockPos next = cur.offset(dir);
                if (visited.contains(next)) continue;
                IBlockState ns = w.getBlockState(next);
                if (TileBlockStates.is(ns, TileBlockStates.DISPLAY_PANEL)
                        && TileBlockStates.facing(ns) == facing) {
                    visited.add(next.toImmutable());
                    queue.add(next.toImmutable());
                }
            }
            if (visited.size() > MAX_W * MAX_H + 8) return null; // 防爆
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : visited) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int width = facing.getAxis() == EnumFacing.Axis.Z ? maxX - minX + 1 : maxZ - minZ + 1;
        int height = maxY - minY + 1;
        if (width < MIN_W || width > MAX_W || height < MIN_H || height > MAX_H) return null;

        // 矩形必须无空洞且全部为同朝向面板
        for (int y = minY; y <= maxY; y++) {
            for (int a = 0; a < width; a++) {
                BlockPos p = facing.getAxis() == EnumFacing.Axis.Z
                        ? new BlockPos(minX + a, y, minZ)
                        : new BlockPos(minX, y, minZ + a);
                if (!visited.contains(p)) return null;
            }
        }

        // master:y→x→z 字典序最小(即 rectMin 角)
        BlockPos master = new BlockPos(minX, minY, minZ);
        return new ScanResult(new ArrayList<>(visited), master, width, height);
    }

    /** 距上一个采样点的进度(0~1),渲染器据此平滑滚动图表. */
    public float getScrollShift(float partialTicks) {
        if (world == null || clientLastTickTime < 0) return 0;
        float elapsed = (world.getTotalWorldTime() - clientLastTickTime) + partialTicks;
        return Math.min(1f, elapsed / 20f);
    }

    private final ItemStackHandler configInv = new ItemStackHandler(MAX_TRACKED) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (world != null && !world.isRemote && isMaster) {
                // 监控目标变化:清空对应缓冲,避免旧数据张冠李戴
                buffers[slot].clear();
                markDirty();
                syncToClient();
            }
        }
    };

    public TileDisplayPanel() {
        for (int i = 0; i < MAX_TRACKED; i++) {
            buffers[i] = new TrendBuffer();
            slotColors[i] = i;
            slotVisible[i] = true;
        }
    }

    // ==================== 结构扫描 ====================

    /** 最大渲染距离:默认只有 64 格,按配置放大(与 TESR 内的手动距离剔除一致). */
    @Override
    public double getMaxRenderDistanceSquared() {
        double d = com.github.aeddddd.ae2enhanced.config.ClientPreferences.current().displayWall.renderDistance;
        return d * d;
    }

    /** 面板同朝向判定时读取的朝向(来自 blockstate). */
    public EnumFacing getFacing() {
        if (world == null) return EnumFacing.NORTH;
        IBlockState state = world.getBlockState(pos);
        return TileBlockStates.is(state, TileBlockStates.DISPLAY_PANEL)
                ? TileBlockStates.facing(state) : EnumFacing.NORTH;
    }

    /** 扫描结果. */
    public static final class ScanResult {
        public final List<BlockPos> panels;
        public final BlockPos master;
        public final int width, height;

        ScanResult(List<BlockPos> panels, BlockPos master, int width, int height) {
            this.panels = panels;
            this.master = master;
            this.width = width;
            this.height = height;
        }
    }

    /** 统计矩形周边边框方块颜色,决定屏幕主题(浅色边框多于深色则为浅色主题). */
    private DisplayTheme detectTheme() {
        EnumFacing facing = getFacing();
        int dark = 0, light = 0;
        int minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        int maxX = facing.getAxis() == EnumFacing.Axis.Z ? minX + rectW - 1 : minX;
        int maxY = minY + rectH - 1;
        int maxZ = facing.getAxis() == EnumFacing.Axis.Z ? minZ : minZ + rectW - 1;
        for (int y = minY - 1; y <= maxY + 1; y++) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) {
                    boolean inside = x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
                    if (inside) continue;
                    IBlockState frameState = world.getBlockState(new BlockPos(x, y, z));
                    Boolean lightFrame = TileBlockStates.frameLight(frameState);
                    if (lightFrame != null) {
                        if (lightFrame) light++;
                        else dark++;
                    }
                }
            }
        }
        return light > dark ? DisplayTheme.LIGHT : DisplayTheme.DARK;
    }

    /** 立即重新扫描(右键/邻居变化时调用). */
    public void requestRescan() {
        this.rescanQueued = true;
    }

    private void tryForm() {
        ScanResult result = scanRectangle(world, pos);
        if (result == null) return;
        for (BlockPos p : result.panels) {
            TileEntity te = world.getTileEntity(p);
            if (te instanceof TileDisplayPanel) {
                TileDisplayPanel panel = (TileDisplayPanel) te;
                panel.formed = true;
                panel.isMaster = p.equals(result.master);
                panel.masterPos = result.master;
                panel.rectW = result.width;
                panel.rectH = result.height;
                panel.markDirty();
                // 通知客户端成型状态(非 master 面板渲染依赖 formed/masterPos)
                world.notifyBlockUpdate(p, world.getBlockState(p), world.getBlockState(p), 2);
            }
        }
        TileEntity masterTe = world.getTileEntity(result.master);
        if (masterTe instanceof TileDisplayPanel) {
            TileDisplayPanel master = (TileDisplayPanel) masterTe;
            master.theme = master.detectTheme();
            master.applyPowerUsage();
        }
    }

    /** master 周期性校验结构完整性;失效则解散整屏. */
    private void verifyStructure() {
        ScanResult result = scanRectangle(world, pos);
        boolean ok = result != null && result.master.equals(pos)
                && result.width == rectW && result.height == rectH;
        if (ok) return;
        // 解散:清除旧矩形内所有面板的成型标记
        EnumFacing facing = getFacing();
        for (int y = 0; y < rectH; y++) {
            for (int a = 0; a < rectW; a++) {
                BlockPos p = facing.getAxis() == EnumFacing.Axis.Z
                        ? pos.add(a, y, 0) : pos.add(0, y, a);
                TileEntity te = world.getTileEntity(p);
                if (te instanceof TileDisplayPanel) {
                    TileDisplayPanel panel = (TileDisplayPanel) te;
                    panel.clearFormed();
                }
            }
        }
        clearFormed();
    }

    private void clearFormed() {
        boolean wasMaster = isMaster;
        formed = false;
        isMaster = false;
        masterPos = null;
        renderBB = null;
        if (wasMaster && proxy != null) {
            proxy.invalidate();
            proxyReady = false;
        }
        markDirty();
        if (world != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        }
    }

    /** 非 master 面板校验 master 是否仍然有效. */
    private void verifyMasterAlive() {
        if (masterPos == null) {
            clearFormed();
            return;
        }
        TileEntity te = world.getTileEntity(masterPos);
        if (!(te instanceof TileDisplayPanel)) {
            clearFormed();
            return;
        }
        TileDisplayPanel master = (TileDisplayPanel) te;
        if (!master.formed || !master.isMaster) {
            clearFormed();
        }
    }

    // ==================== Tick ====================

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        tickCounter++;
        if (rescanQueued) {
            rescanQueued = false;
            if (!formed) {
                tryForm();
            } else if (isMaster) {
                verifyStructure();
            } else {
                verifyMasterAlive();
            }
        }
        if ((tickCounter + pos.hashCode()) % RESCAN_INTERVAL == 0) {
            if (!formed) {
                tryForm();
            } else if (isMaster) {
                verifyStructure();
            } else {
                verifyMasterAlive();
            }
        }
        if (formed && isMaster) {
            updateMaster();
        }
    }

    private void updateMaster() {
        if (!proxyReady) {
            proxyReady = true;
            applyPowerUsage();
            getProxy().onReady();
        }
        if (tickCounter % 20 != 0) return;
        // 每秒采样
        boolean wasPowered = powered;
        powered = getProxy().isPowered();
        long[] values = new long[MAX_TRACKED];
        int validMask = 0;
        if (powered) {
            sampleNetwork(values);
            for (int i = 0; i < MAX_TRACKED; i++) {
                if (!configInv.getStackInSlot(i).isEmpty()) validMask |= (1 << i);
            }
        }
        for (int i = 0; i < MAX_TRACKED; i++) {
            buffers[i].push(values[i], (validMask & (1 << i)) != 0);
        }
        sampleTotal++;
        if (wasPowered != powered) {
            syncToClient();
        }
        // 增量同步给附近玩家
        AE2Enhanced.network.sendToAllAround(
                new PacketDisplayTick(pos, values, validMask, powered, sampleTotal),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SYNC_RANGE));
    }

    private void sampleNetwork(long[] out) {
        IGridNode node = getProxy().getNode();
        if (node == null) return;
        IGrid grid = node.getGrid();
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        IMEMonitor<IAEItemStack> itemMon = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        IMEMonitor<IAEFluidStack> fluidMon = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        for (int i = 0; i < MAX_TRACKED; i++) {
            ItemStack cfg = configInv.getStackInSlot(i);
            if (cfg.isEmpty()) continue;
            if (ItemFluidDrop.isFluidDrop(cfg)) {
                FluidStack fs = ItemFluidDrop.getFluidStack(cfg);
                if (fs == null) continue;
                IAEFluidStack found = fluidMon.getStorageList().findPrecise(AEFluidStack.fromFluidStack(fs));
                out[i] = found == null ? 0 : found.getStackSize();
            } else {
                IAEItemStack key = AEItemStack.fromItemStack(cfg);
                if (key == null) continue;
                IAEItemStack found = itemMon.getStorageList().findPrecise(key);
                out[i] = found == null ? 0 : found.getStackSize();
            }
        }
    }

    private void applyPowerUsage() {
        if (proxy != null) {
            proxy.setIdlePowerUsage(rectW * rectH * com.github.aeddddd.ae2enhanced.config.ServerConfig.current().displayWall.powerPerBlock);
        }
    }

    public void syncToClient() {
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        }
    }

    /** 客户端:应用增量采样包. 序号对齐,重复包忽略、丢包则请求全量重同步,避免图表跳变. */
    public void applyTickPacket(long[] values, int validMask, boolean powered, long total) {
        if (total <= sampleTotal) {
            // 重复/过期包:仅刷新供电状态
            this.powered = powered;
            return;
        }
        if (total > sampleTotal + 1 && world != null) {
            // 丢包失步:请求服务端重发全量数据(update tag)
            AE2Enhanced.network.sendToServer(new PacketDisplayAction(
                    pos, PacketDisplayAction.ACTION_REQUEST_SYNC, 0));
            return;
        }
        for (int i = 0; i < MAX_TRACKED && i < values.length; i++) {
            buffers[i].push(values[i], (validMask & (1 << i)) != 0);
        }
        this.sampleTotal = total;
        this.powered = powered;
        this.clientLastTickTime = world.getTotalWorldTime();
    }

    // ==================== 配置操作(由网络包触发) ====================

    public void cycleChartType() {
        chartType = chartType.next();
        syncToClient();
    }

    public void setChartType(int ordinal) {
        ChartType t = ChartType.byOrdinal(ordinal);
        if (t != chartType) {
            chartType = t;
            syncToClient();
        }
    }

    public void setTimeRange(int ordinal) {
        TimeRange r = TimeRange.byOrdinal(ordinal);
        if (r != timeRange) {
            timeRange = r;
            syncToClient();
        }
    }

    public void cycleTimeRange() {
        timeRange = timeRange.next();
        syncToClient();
    }

    public void cycleYMode() {
        yMode = yMode.next();
        if (yMode == YAxisMode.FIXED) {
            fixedMax = Math.max(1, computeAutoMax());
        }
        syncToClient();
    }

    public void cycleSlotColor(int slot) {
        if (slot < 0 || slot >= MAX_TRACKED) return;
        slotColors[slot] = (slotColors[slot] + 1) % com.github.aeddddd.ae2enhanced.display.DisplayPalette.size();
        syncToClient();
    }

    public void cycleSlotColorPrev(int slot) {
        if (slot < 0 || slot >= MAX_TRACKED) return;
        int size = com.github.aeddddd.ae2enhanced.display.DisplayPalette.size();
        slotColors[slot] = Math.floorMod(slotColors[slot] - 1, size);
        syncToClient();
    }

    public void toggleSlotVisible(int slot) {
        if (slot < 0 || slot >= MAX_TRACKED) return;
        slotVisible[slot] = !slotVisible[slot];
        syncToClient();
    }

    /** 当前可见窗口内所有启用监控项的最大值(用于 FIXED 量程捕获与客户端 AUTO 参考). */
    public long computeAutoMax() {
        int tier = TrendBuffer.selectTier(timeRange.getSeconds());
        int samples = Math.min(buffersSampleCount(tier), timeRange.getSeconds() / TrendBuffer.TIER_INTERVAL[tier]);
        long max = 1;
        for (int i = 0; i < MAX_TRACKED; i++) {
            if (configInv.getStackInSlot(i).isEmpty() || !slotVisible[i]) continue;
            for (int n = 0; n < samples; n++) {
                if (buffers[i].isValid(tier, n)) {
                    max = Math.max(max, buffers[i].getValue(tier, n));
                }
            }
        }
        return max;
    }

    private int buffersSampleCount(int tier) {
        int s = 0;
        for (int i = 0; i < MAX_TRACKED; i++) {
            s = Math.max(s, buffers[i].getSize(tier));
        }
        return s;
    }

    // ==================== IGridProxyable ====================

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "display_wall",
                    new ItemStack(getBlockType()), true);
            proxy.setValidSides(EnumSet.allOf(EnumFacing.class));
        }
        return proxy;
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        // 未成型或非 master 不对外提供节点,线缆不会误连
        if (!formed || !isMaster) return null;
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return formed && isMaster ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
    }

    @Override
    public void securityBreak() {
        if (world != null && !world.isRemote) {
            world.destroyBlock(pos, false);
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public void invalidate() {
        super.invalidate();
        if (proxy != null) proxy.invalidate();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (proxy != null) proxy.onChunkUnload();
    }

    // ==================== NBT / 同步 ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        compound.setBoolean("isMaster", isMaster);
        if (masterPos != null) compound.setLong("masterPos", masterPos.toLong());
        compound.setInteger("rectW", rectW);
        compound.setInteger("rectH", rectH);
        if (isMaster) {
            compound.setInteger("theme", theme.ordinal());
            compound.setInteger("chartType", chartType.ordinal());
            compound.setInteger("timeRange", timeRange.ordinal());
            compound.setInteger("yMode", yMode.ordinal());
            compound.setLong("fixedMax", fixedMax);
            compound.setBoolean("powered", powered);
            compound.setLong("sampleTotal", sampleTotal);
            compound.setByteArray("slotColors", toBytes(slotColors));
            byte[] vis = new byte[MAX_TRACKED];
            for (int i = 0; i < MAX_TRACKED; i++) vis[i] = (byte) (slotVisible[i] ? 1 : 0);
            compound.setByteArray("slotVisible", vis);
            compound.setTag("config", configInv.serializeNBT());
            NBTTagCompound bufTag = new NBTTagCompound();
            for (int i = 0; i < MAX_TRACKED; i++) {
                if (!configInv.getStackInSlot(i).isEmpty()) {
                    bufTag.setTag("b" + i, buffers[i].writeToNBT());
                }
            }
            compound.setTag("buffers", bufTag);
            if (proxy != null) {
                NBTTagCompound proxyTag = new NBTTagCompound();
                proxy.writeToNBT(proxyTag);
                compound.setTag("proxy", proxyTag);
            }
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        formed = compound.getBoolean("formed");
        isMaster = compound.getBoolean("isMaster");
        masterPos = compound.hasKey("masterPos") ? BlockPos.fromLong(compound.getLong("masterPos")) : null;
        rectW = compound.getInteger("rectW");
        rectH = compound.getInteger("rectH");
        if (isMaster) {
            theme = DisplayTheme.byOrdinal(compound.getInteger("theme"));
            chartType = ChartType.byOrdinal(compound.getInteger("chartType"));
            timeRange = TimeRange.byOrdinal(compound.getInteger("timeRange"));
            yMode = YAxisMode.byOrdinal(compound.getInteger("yMode"));
            fixedMax = Math.max(1, compound.getLong("fixedMax"));
            powered = compound.getBoolean("powered");
            sampleTotal = compound.getLong("sampleTotal");
            byte[] cols = compound.getByteArray("slotColors");
            for (int i = 0; i < MAX_TRACKED && i < cols.length; i++) slotColors[i] = cols[i] & 0xFF;
            byte[] vis = compound.getByteArray("slotVisible");
            for (int i = 0; i < MAX_TRACKED && i < vis.length; i++) slotVisible[i] = vis[i] != 0;
            configInv.deserializeNBT(compound.getCompoundTag("config"));
            NBTTagCompound bufTag = compound.getCompoundTag("buffers");
            for (int i = 0; i < MAX_TRACKED; i++) {
                buffers[i].clear();
                if (bufTag.hasKey("b" + i)) {
                    buffers[i].readFromNBT(bufTag.getCompoundTag("b" + i));
                }
            }
            if (compound.hasKey("proxy")) {
                getProxy().readFromNBT(compound.getCompoundTag("proxy"));
            }
        }
        renderBB = null;
        rescanQueued = true;
    }

    private static byte[] toBytes(int[] arr) {
        byte[] out = new byte[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = (byte) arr[i];
        return out;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        // proxy NBT 不发送给客户端
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.removeTag("proxy");
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    // ==================== 访问器 ====================

    public boolean isFormed() { return formed; }
    public boolean isMasterRole() { return formed && isMaster; }
    public BlockPos getMasterPos() { return masterPos; }
    public int getRectW() { return rectW; }
    public int getRectH() { return rectH; }
    public DisplayTheme getTheme() { return theme; }
    public ChartType getChartType() { return chartType; }
    public TimeRange getTimeRange() { return timeRange; }
    public YAxisMode getYMode() { return yMode; }
    public long getFixedMax() { return fixedMax; }
    public boolean isPowered() { return powered; }
    public ItemStackHandler getConfigInv() { return configInv; }
    public TrendBuffer getBuffer(int slot) { return buffers[slot]; }
    public int getSlotColor(int slot) { return slotColors[slot]; }
    public boolean isSlotVisible(int slot) { return slotVisible[slot]; }
}
