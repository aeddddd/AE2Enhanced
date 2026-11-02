package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.parts.CableBusContainer;
import appeng.tile.networking.TileCableBus;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.access.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.crafting.access.mixin.ICraftingCPUClusterAccessor;
import com.github.aeddddd.ae2enhanced.tile.SupercausalStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 超因果计算核心 TileEntity.
 *
 * <p>设计定位：网络中的超级 Crafting CPU.不再实现自定义合成引擎,而是直接创建并管理
 * 原生的 {@link CraftingCPUCluster} 实例,通过 Mixin 将其行为重定向到本 TileEntity.</p>
 *
 * <p>每个计算核心维护一个 {@code cpuPool},其中索引 0 为常驻集群,其余为动态生成的额外集群.
 * 当多个订单并发时,终端会自动看到多个 CPU；订单完成后空闲的额外集群会被自动回收.</p>
 */
public class TileComputationCore extends TileAENetworkBase implements IActionHost, ITickable {

    public static final int MAX_PARALLEL = 16384;
    private static final String NBT_CPU_POOL = "cpuPool";
    private static final String NBT_CLIENT_TASKS = "clientTasks";
    private static final String DEFAULT_NAME = "Supercausal Computation Core";

    private boolean formed = false;
    private int parallelLimit = 0;
    private final List<CraftingCPUCluster> cpuPool = new ArrayList<>();
    private final List<ComputationCoreTaskSummary> clientTaskSummaries = new ArrayList<>();
    private final java.util.List<NBTTagCompound> preservedClusterEntries = new java.util.ArrayList<>();

    // ---------- 状态访问 ----------

    public boolean isFormed() {
        return formed;
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public List<CraftingCPUCluster> getCpuPool() {
        return cpuPool;
    }
    private NBTTagCompound lastClientSyncTag;

    public List<ComputationCoreTaskSummary> getTaskSummaries() {
        return java.util.Collections.unmodifiableList(clientTaskSummaries);
    }

    public int getActiveOrderCount() {
        if (world != null && world.isRemote) return clientTaskSummaries.size();
        return createTaskSummaries().size();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    // ---------- 组装 / 解体 ----------

    public void assemble(int parallelLimit) {
        this.formed = true;
        this.parallelLimit = parallelLimit;

        CraftingCPUCluster primary = createCluster();
        this.cpuPool.add(primary);

        getProxy().onReady();
        bindMeInterface();

        markDirty();
        syncToClient();
        IGridNode node = getProxy().getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().postEvent(new MENetworkCraftingCpuChange(node));
        }
    }

    @Override
    protected void onProxyInvalidate() {
        removeCpuPoolFromCraftingGridCache();
    }

    @Override
    protected void onProxyChunkUnload() {
        removeCpuPoolFromCraftingGridCache();
    }

    private List<ComputationCoreTaskSummary> createTaskSummaries() {
        List<ComputationCoreTaskSummary> summaries = new ArrayList<>();
        for (CraftingCPUCluster cpu : cpuPool) {
            IAEItemStack finalOutput = cpu.getFinalOutput();
            long start = cpu.getStartItemCount();
            long remaining = cpu.getRemainingItemCount();
            if (!cpu.isBusy() && finalOutput == null && start == 0 && remaining == 0) continue;
            summaries.add(new ComputationCoreTaskSummary(cpu.getName(),
                    finalOutput != null ? finalOutput.createItemStack() : ItemStack.EMPTY,
                    start, remaining, cpu.getElapsedTime()));
        }
        return summaries;
    }

    public void disassemble() {
        IGridNode node = getProxy().getNode();
        IMEMonitor<IAEItemStack> network = null;
        if (node != null && node.getGrid() != null) {
            try {
                IStorageGrid storage = node.getGrid().getCache(IStorageGrid.class);
                if (storage != null) {
                    network = storage.getInventory(AEApi.instance().storage()
                            .getStorageChannel(IItemStorageChannel.class));
                }
            } catch (RuntimeException e) {
                AE2Enhanced.LOGGER.warn("[AE2E] CPU recovery network unavailable; dropping remainders");
            }
        }
        this.formed = false;
        this.parallelLimit = 0;

        List<CraftingCPUCluster> retained = new ArrayList<>();
        MachineSource source = new MachineSource(this);
        java.util.function.Predicate<ItemStack> drop = world != null && !world.isRemote
                ? this::dropCpuStack : null;
        for (CraftingCPUCluster cpu : new ArrayList<>(cpuPool)) {
            boolean drained = com.github.aeddddd.ae2enhanced.crafting.CpuInventoryRecovery.cancelAndDrain(
                    cpu::cancel, cpu.getInventory(), network, source, drop);
            if (!drained) retained.add(cpu);
        }
        removeCpuPoolFromCraftingGridCache();
        cpuPool.clear();
        cpuPool.addAll(retained);

        unbindMeInterface();

        if (node != null && node.getGrid() != null) {
            node.getGrid().postEvent(new MENetworkCraftingCpuChange(node));
        }
        if (proxy != null) {
            proxy.invalidate();
        }
        markDirty();
        syncToClient();
    }

    // ---------- ITickable ----------

    private boolean dropCpuStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || world == null || world.isRemote) return false;
        net.minecraft.entity.item.EntityItem drop = new net.minecraft.entity.item.EntityItem(world,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        drop.setDefaultPickupDelay();
        return world.spawnEntity(drop);
    }

    // ---------- Job Submission ----------

    /**
     * 尝试提交合成任务.先检查现有空闲集群,若全部忙碌则动态创建新集群.
     */
    public ICraftingLink trySpawnAndSubmitJob(IGrid grid, ICraftingJob job,
                                               appeng.api.networking.security.IActionSource src,
                                               ICraftingRequester req) {
        if (!formed || cpuPool.isEmpty()) {
            return null;
        }

        // 1. 尝试现有空闲集群
        for (CraftingCPUCluster cpu : cpuPool) {
            if (!cpu.isBusy()) {
                return cpu.submitJob(grid, job, src, req);
            }
        }

        // 2. 动态创建新集群
        CraftingCPUCluster newCpu = createCluster();
        cpuPool.add(newCpu);

        // 立即注入到 CraftingGridCache,确保终端立即可见
        injectCpuPoolIntoCraftingGridCache();

        // 触发 CraftingGridCache 重建以注册新集群
        IGridNode node = getProxy().getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().postEvent(new MENetworkCraftingCpuChange(node));
        }

        return newCpu.submitJob(grid, job, src, req);
    }

    // ---------- ME 接口绑定 ----------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady && formed) {
            clearNeedsReady();
            getProxy().onReady();
            bindMeInterface();
        }

        if (formed && world.getTotalWorldTime() % 20 == 0) {
            bindMeInterface();
        }

        if (formed) {
            injectCpuPoolIntoCraftingGridCache();
        }

        if (formed && world.getTotalWorldTime() % 20 == 0) syncToClient();
        if (!formed || cpuPool.isEmpty()) return;

        int idleCount = 0;
        for (CraftingCPUCluster cpu : cpuPool) {
            if (!cpu.isBusy()) idleCount++;
        }

        Iterator<CraftingCPUCluster> it = cpuPool.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            CraftingCPUCluster cpu = it.next();
            if (cpu != cpuPool.get(0) && !cpu.isBusy() && isInventoryEmpty(cpu)) {
                if (idleCount > 1) {
                    it.remove();
                    idleCount--;
                    changed = true;
                }
            }
        }

        if (idleCount == 0) {
            CraftingCPUCluster standby = createCluster();
            cpuPool.add(standby);
            injectCpuPoolIntoCraftingGridCache();
            changed = true;
        }

        if (changed) {
            IGridNode node = getProxy().getNode();
            if (node != null && node.getGrid() != null) {
                node.getGrid().postEvent(new MENetworkCraftingCpuChange(node));
            }
        }
    }

    private void bindMeInterface() {
        if (world == null) return;
        BlockPos interfacePos = getMeInterfacePos();
        if (interfacePos == null) return;
        TileEntity te = world.getTileEntity(interfacePos);
        if (te instanceof TileSuperCraftingInterface) {
            IBlockState state = world.getBlockState(interfacePos);
            if (TileBlockStates.is(state, TileBlockStates.SUPER_CRAFTING_INTERFACE) && !TileBlockStates.formed(state)) {
                world.setBlockState(interfacePos, TileBlockStates.formed(state, true));
                // setBlockState 可能在某些情况下重新创建 TileEntity,需要重新获取
                te = world.getTileEntity(interfacePos);
            }
            if (te instanceof TileSuperCraftingInterface) {
                TileSuperCraftingInterface iface = (TileSuperCraftingInterface) te;
                if (iface.getControllerPos() == null) {
                    iface.setControllerPos(pos);
                }
            }
        }
        updateCableConnections(interfacePos);
    }

    private void updateCableConnections(BlockPos centerPos) {
        if (world == null || centerPos == null) return;
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighborPos = centerPos.offset(facing);
            TileEntity te = world.getTileEntity(neighborPos);
            if (te instanceof TileCableBus) {
                CableBusContainer cbc = ((TileCableBus) te).getCableBus();
                if (cbc != null) {
                    cbc.updateConnections();
                }
            }
        }
    }

    private BlockPos getMeInterfacePos() {
        if (world == null) return null;
        EnumFacing facing = SupercausalStructure.getControllerFacing(world, pos);
        return pos.add(SupercausalStructure.rotate(SupercausalStructure.ME_INTERFACE_REL, facing));
    }

    // ---------- 辅助方法 ----------

    private boolean isInventoryEmpty(CraftingCPUCluster cpu) {
        appeng.api.storage.data.IItemList<IAEItemStack> list =
            appeng.api.AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
        cpu.getInventory().getAvailableItems(list);
        return list.isEmpty();
    }

    // ---------- 集群创建 ----------

    private CraftingCPUCluster createCluster() {
        CraftingCPUCluster cluster = new CraftingCPUCluster(
            new WorldCoord(pos.getX(), pos.getY(), pos.getZ()),
            new WorldCoord(pos.getX(), pos.getY(), pos.getZ())
        );

        try {
            ICraftingCPUClusterAccessor acc = (ICraftingCPUClusterAccessor) (Object) cluster;

            // 设置 machineSrc 指向本 TileEntity(IActionHost)
            acc.ae2e$setMachineSrc(new MachineSource(this));

            // 设置无限存储
            acc.ae2e$setAvailableStorage(Long.MAX_VALUE);

            // 设置 16384 协处理器
            acc.ae2e$setAccelerator(MAX_PARALLEL);

            // 设置固定名称
            acc.ae2e$setMyName(DEFAULT_NAME);

            // 设置 Mixin 字段,标记该集群属于本计算核心
            ((IComputationCoreAccess) (Object) cluster).ae2enhanced$setComputationCore(this);

        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to setup CraftingCPUCluster for Computation Core", e);
        }

        return cluster;
    }

    @Override
    protected String getProxyName() {
        return "computation_core";
    }

    private void unbindMeInterface() {
        if (world == null) return;
        BlockPos interfacePos = getMeInterfacePos();
        if (interfacePos == null) return;
        TileEntity te = world.getTileEntity(interfacePos);
        if (te instanceof TileSuperCraftingInterface) {
            ((TileSuperCraftingInterface) te).setControllerPos(null);
            IBlockState state = world.getBlockState(interfacePos);
            if (TileBlockStates.is(state, TileBlockStates.SUPER_CRAFTING_INTERFACE) && TileBlockStates.formed(state)) {
                world.setBlockState(interfacePos, TileBlockStates.formed(state, false));
            }
        }
        updateCableConnections(interfacePos);
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.NONE;
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(getBlockType());
    }

    // ---------- NBT / 同步 ----------

    @Override
    public void securityBreak() {
        if (world != null) SupercausalStructure.disassemble(world, pos);
        else disassemble();
    }

    private void syncToClient() {
        if (world == null || world.isRemote) return;
        NBTTagCompound update = getUpdateTag();
        if (update.equals(lastClientSyncTag)) return;
        lastClientSyncTag = update.copy();
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.formed = compound.getBoolean("formed");
        this.parallelLimit = compound.getInteger("parallelLimit");
        cpuPool.clear();
        clientTaskSummaries.clear();
        preservedClusterEntries.clear();
        lastClientSyncTag = null;
        getProxy().readFromNBT(compound);

        if ((world == null || !world.isRemote) && compound.hasKey(NBT_CPU_POOL)) {
            NBTTagList list = compound.getTagList(NBT_CPU_POOL, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound cpuTag = list.getCompoundTagAt(i);
                CraftingCPUCluster cpu = createCluster();
                try {
                    cpu.readFromNBT(cpuTag);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to read CraftingCPUCluster NBT; preserving opaque entry: {}",
                            e.toString());
                    preservedClusterEntries.add(cpuTag.copy());
                    continue;
                }
                cpuPool.add(cpu);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        compound.setInteger("parallelLimit", parallelLimit);
        getProxy().writeToNBT(compound);

        NBTTagList list = new NBTTagList();
        for (NBTTagCompound raw : preservedClusterEntries) list.appendTag(raw.copy());
        for (CraftingCPUCluster cpu : cpuPool) {
            NBTTagCompound cpuTag = new NBTTagCompound();
            cpu.writeToNBT(cpuTag);
            list.appendTag(cpuTag);
        }
        compound.setTag(NBT_CPU_POOL, list);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound compound = super.writeToNBT(new NBTTagCompound());
        compound.setBoolean("formed", formed);
        compound.setInteger("parallelLimit", parallelLimit);
        getProxy().writeToNBT(compound);
        NBTTagList tasks = new NBTTagList();
        for (ComputationCoreTaskSummary summary : createTaskSummaries()) {
            tasks.appendTag(summary.writeToNBT());
        }
        compound.setTag(NBT_CLIENT_TASKS, tasks);
        return compound;
    }

    private void readClientSync(NBTTagCompound compound) {
        super.readFromNBT(compound);
        formed = compound.getBoolean("formed");
        parallelLimit = compound.getInteger("parallelLimit");
        cpuPool.clear();
        clientTaskSummaries.clear();
        NBTTagList tasks = compound.getTagList(NBT_CLIENT_TASKS, 10);
        for (int i = 0; i < tasks.tagCount(); i++) {
            clientTaskSummaries.add(ComputationCoreTaskSummary.readFromNBT(tasks.getCompoundTagAt(i)));
        }
        getProxy().readFromNBT(compound);
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readClientSync(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readClientSync(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState, net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // ---------- 备用方案：直接注入 CraftingGridCache ----------

    @Override
    @Nonnull
    public AxisAlignedBB getRenderBoundingBox() {
        if (world == null) return super.getRenderBoundingBox();
        if (!(TileBlockStates.is(world.getBlockState(pos), TileBlockStates.COMPUTATION_CORE))) {
            return super.getRenderBoundingBox();
        }
        EnumFacing facing = TileBlockStates.facing(world.getBlockState(pos));
        EnumFacing structureDir = facing.getOpposite();
        double cx = pos.getX() + 0.5 + structureDir.getXOffset() * 9.0;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5 + structureDir.getZOffset() * 9.0;
        double r = 12.0; // 覆盖戴森球全部渲染范围(半径约 10,留余量)
        return new AxisAlignedBB(cx - r, cy - r, cz - r, cx + r, cy + r, cz + r);
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0; // 256 格渲染距离
    }

    private void injectCpuPoolIntoCraftingGridCache() {
        try {
            IGridNode node = getProxy().getNode();
            if (node == null || node.getGrid() == null) return;
            appeng.me.cache.CraftingGridCache cache;
            try {
                cache = node.getGrid().getCache(appeng.me.cache.CraftingGridCache.class);
            } catch (NullPointerException e) {
                return; // grid not fully initialized yet
            }
            if (cache == null) return;
            java.lang.reflect.Field field = appeng.me.cache.CraftingGridCache.class.getDeclaredField("craftingCPUClusters");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<appeng.me.cluster.implementations.CraftingCPUCluster> set =
                (java.util.Set<appeng.me.cluster.implementations.CraftingCPUCluster>) field.get(cache);
            if (set == null) return;
            for (appeng.me.cluster.implementations.CraftingCPUCluster cpu : cpuPool) {
                if (cpu != null) {
                    set.add(cpu);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] injectCpuPoolIntoCraftingGridCache failed", e);
        }
    }

    private void removeCpuPoolFromCraftingGridCache() {
        try {
            IGridNode node = getProxy().getNode();
            if (node == null || node.getGrid() == null) return;
            appeng.me.cache.CraftingGridCache cache;
            try {
                cache = node.getGrid().getCache(appeng.me.cache.CraftingGridCache.class);
            } catch (NullPointerException e) {
                return;
            }
            if (cache == null) return;
            java.lang.reflect.Field field = appeng.me.cache.CraftingGridCache.class.getDeclaredField("craftingCPUClusters");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<appeng.me.cluster.implementations.CraftingCPUCluster> set =
                (java.util.Set<appeng.me.cluster.implementations.CraftingCPUCluster>) field.get(cache);
            if (set == null) return;
            for (appeng.me.cluster.implementations.CraftingCPUCluster cpu : cpuPool) {
                if (cpu != null) {
                    set.remove(cpu);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] removeCpuPoolFromCraftingGridCache failed", e);
        }
    }
}
