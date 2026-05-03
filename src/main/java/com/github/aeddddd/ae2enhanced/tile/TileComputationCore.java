package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import appeng.parts.CableBusContainer;
import appeng.tile.networking.TileCableBus;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.block.BlockSuperCraftingInterface;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 超因果计算核心 TileEntity。
 *
 * <p>设计定位：网络中的超级 Crafting CPU。不再实现自定义合成引擎，而是直接创建并管理
 * 原生的 {@link CraftingCPUCluster} 实例，通过 Mixin 将其行为重定向到本 TileEntity。</p>
 *
 * <p>每个计算核心维护一个 {@code cpuPool}，其中索引 0 为常驻集群，其余为动态生成的额外集群。
 * 当多个订单并发时，终端会自动看到多个 CPU；订单完成后空闲的额外集群会被自动回收。</p>
 */
public class TileComputationCore extends TileEntity implements IGridProxyable, IActionHost, ITickable {

    public static final int MAX_PARALLEL = 16384;
    private static final String NBT_CPU_POOL = "cpuPool";
    private static final String DEFAULT_NAME = "Supercausal Computation Core";

    private boolean formed = false;
    private int parallelLimit = 0;

    // AE2 网络代理
    private AENetworkProxy proxy;
    private boolean needsReady = false;

    // CPU 集群池：索引 0 为常驻集群，>0 为动态集群
    private final List<CraftingCPUCluster> cpuPool = new ArrayList<>();

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

    /**
     * 返回当前活跃的合成订单数量（即处于忙碌状态的 CPU 集群数）。
     */
    public int getActiveOrderCount() {
        int count = 0;
        for (CraftingCPUCluster cpu : cpuPool) {
            if (cpu.isBusy()) {
                count++;
            }
        }
        return count;
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

    public void disassemble() {
        IGridNode node = getProxy().getNode();
        this.formed = false;
        this.parallelLimit = 0;

        for (CraftingCPUCluster cpu : new ArrayList<>(cpuPool)) {
            try {
                cpu.cancel();
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Error cancelling CraftingCPUCluster on disassemble: {}", e.toString());
            }
        }
        cpuPool.clear();

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

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        if (needsReady && formed) {
            needsReady = false;
            getProxy().onReady();
            bindMeInterface();
        }

        if (formed && world.getTotalWorldTime() % 20 == 0) {
            bindMeInterface();
        }

        if (formed) {
            injectCpuPoolIntoCraftingGridCache();
        }

        if (!formed || cpuPool.isEmpty()) return;

        // 统计空闲数量
        int idleCount = 0;
        for (CraftingCPUCluster cpu : cpuPool) {
            if (!cpu.isBusy()) idleCount++;
        }

        // 清理空闲的额外集群，但始终保留至少 1 个空闲 CPU
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

        // 如果所有集群都忙碌，立即创建一个备用空闲集群
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

    // ---------- Job Submission ----------

    /**
     * 尝试提交合成任务。先检查现有空闲集群，若全部忙碌则动态创建新集群。
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

        // 立即注入到 CraftingGridCache，确保终端立即可见
        injectCpuPoolIntoCraftingGridCache();

        // 触发 CraftingGridCache 重建以注册新集群
        IGridNode node = getProxy().getNode();
        if (node != null && node.getGrid() != null) {
            node.getGrid().postEvent(new MENetworkCraftingCpuChange(node));
        }

        return newCpu.submitJob(grid, job, src, req);
    }

    // ---------- ME 接口绑定 ----------

    private void bindMeInterface() {
        if (world == null) return;
        BlockPos interfacePos = getMeInterfacePos();
        if (interfacePos == null) return;
        TileEntity te = world.getTileEntity(interfacePos);
        if (te instanceof TileSuperCraftingInterface) {
            IBlockState state = world.getBlockState(interfacePos);
            if (state.getBlock() instanceof BlockSuperCraftingInterface && !state.getValue(BlockSuperCraftingInterface.FORMED)) {
                world.setBlockState(interfacePos, state.withProperty(BlockSuperCraftingInterface.FORMED, true));
                // setBlockState 可能在某些情况下重新创建 TileEntity，需要重新获取
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

    private void unbindMeInterface() {
        if (world == null) return;
        BlockPos interfacePos = getMeInterfacePos();
        if (interfacePos == null) return;
        TileEntity te = world.getTileEntity(interfacePos);
        if (te instanceof TileSuperCraftingInterface) {
            ((TileSuperCraftingInterface) te).setControllerPos(null);
            IBlockState state = world.getBlockState(interfacePos);
            if (state.getBlock() instanceof BlockSuperCraftingInterface && state.getValue(BlockSuperCraftingInterface.FORMED)) {
                world.setBlockState(interfacePos, state.withProperty(BlockSuperCraftingInterface.FORMED, false));
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
            // 设置 machineSrc 指向本 TileEntity（IActionHost）
            Field machineSrcField = CraftingCPUCluster.class.getDeclaredField("machineSrc");
            machineSrcField.setAccessible(true);
            machineSrcField.set(cluster, new MachineSource(this));

            // 设置无限存储
            Field availableStorageField = CraftingCPUCluster.class.getDeclaredField("availableStorage");
            availableStorageField.setAccessible(true);
            availableStorageField.setLong(cluster, Long.MAX_VALUE);

            // 设置 16384 协处理器
            Field acceleratorField = CraftingCPUCluster.class.getDeclaredField("accelerator");
            acceleratorField.setAccessible(true);
            acceleratorField.setInt(cluster, MAX_PARALLEL);

            // 设置固定名称
            Field myNameField = CraftingCPUCluster.class.getDeclaredField("myName");
            myNameField.setAccessible(true);
            myNameField.set(cluster, DEFAULT_NAME);

            // 设置 Mixin 字段，标记该集群属于本计算核心
            Field mixinCoreField = CraftingCPUCluster.class.getDeclaredField("ae2enhanced$computationCore");
            mixinCoreField.setAccessible(true);
            mixinCoreField.set(cluster, this);

        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to setup CraftingCPUCluster for Computation Core", e);
        }

        return cluster;
    }

    // ---------- IGridProxyable ----------

    private AENetworkProxy createProxy() {
        AENetworkProxy p = new AENetworkProxy(this, "computation_core",
                new net.minecraft.item.ItemStack(ModBlocks.COMPUTATION_CORE), true);
        p.setValidSides(java.util.EnumSet.allOf(net.minecraft.util.EnumFacing.class));
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
        return AECableType.NONE;
    }

    @Override
    public void securityBreak() {
    }

    // ---------- 网络代理生命周期 ----------

    @Override
    public void validate() {
        super.validate();
        this.needsReady = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (proxy != null) {
            proxy.invalidate();
        }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (proxy != null) {
            proxy.onChunkUnload();
        }
    }

    // ---------- NBT / 同步 ----------

    private void syncToClient() {
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.formed = compound.getBoolean("formed");
        this.parallelLimit = compound.getInteger("parallelLimit");
        if (compound.hasKey("proxy")) {
            getProxy().readFromNBT(compound.getCompoundTag("proxy"));
        }

        if (formed && compound.hasKey(NBT_CPU_POOL)) {
            NBTTagList list = compound.getTagList(NBT_CPU_POOL, 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound cpuTag = list.getCompoundTagAt(i);
                CraftingCPUCluster cpu = createCluster();
                try {
                    cpu.readFromNBT(cpuTag);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to read CraftingCPUCluster NBT, dropping cluster: {}", e.toString());
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
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState, net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    // ---------- 备用方案：直接注入 CraftingGridCache ----------

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
}
