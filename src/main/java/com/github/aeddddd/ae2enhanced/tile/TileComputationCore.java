package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.crafting.CraftingOrder;
import com.github.aeddddd.ae2enhanced.crafting.OrderScheduler;
import com.github.aeddddd.ae2enhanced.crafting.ParallelAllocator;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * 超因果计算核心 TileEntity。
 *
 * 设计定位：网络中的超级 Crafting CPU，不存储任何样板。
 * 玩家通过 AE 终端下单，订单由 AE2 CraftingGrid 路由至本核心执行。
 * 内部引擎（OrderScheduler + ParallelAllocator）管理订单队列与 16384 并行分配。
 *
 * 集成方式：通过 Mixin 向 AE2 CraftingGridCache 注册为可用 CPU 节点。
 * 不实现 ICraftingProvider / ICraftingMedium（样板由网络中其他设备提供）。
 */
public class TileComputationCore extends TileEntity implements IGridProxyable, ITickable {

    public static final int MAX_PARALLEL = ParallelAllocator.MAX_PARALLEL; // 16384

    private boolean formed = false;
    private int parallelLimit = 0;
    private int activeOrderCount = 0;

    // AE2 网络代理
    private AENetworkProxy proxy;
    private boolean needsReady = false;

    // 核心引擎
    private final OrderScheduler scheduler = new OrderScheduler(MAX_PARALLEL);
    private final ParallelAllocator parallelAllocator = new ParallelAllocator();

    // ---------- 状态访问 ----------

    public boolean isFormed() {
        return formed;
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public int getActiveOrderCount() {
        return activeOrderCount;
    }

    public OrderScheduler getScheduler() {
        return scheduler;
    }

    public ParallelAllocator getParallelAllocator() {
        return parallelAllocator;
    }

    // ---------- 组装 / 解体 ----------

    public void assemble(int parallelLimit) {
        this.formed = true;
        this.parallelLimit = parallelLimit;
        this.activeOrderCount = 0;
        markDirty();
        syncToClient();
        if (proxy != null) {
            proxy.onReady();
        } else {
            needsReady = true;
        }
        // Mixin 将在代理就绪后自动向 CraftingGridCache 注册本核心
    }

    public void disassemble() {
        this.formed = false;
        this.parallelLimit = 0;
        this.activeOrderCount = 0;
        // 清理活跃订单
        for (CraftingOrder order : scheduler.getActiveOrdersSnapshot()) {
            scheduler.fail(order);
        }
        parallelAllocator.reset();
        markDirty();
        syncToClient();
        if (proxy != null) {
            proxy.invalidate();
        }
        // Mixin 将在代理失效后自动从 CraftingGridCache 注销本核心
    }

    // ---------- ITickable ----------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        // 延迟就绪代理（避免 tile 初始化时 world 尚未绑定）
        if (needsReady && formed) {
            needsReady = false;
            getProxy().onReady();
        }

        // P1：驱动内部订单调度器
        if (formed) {
            tickScheduler();
        }
    }

    private void tickScheduler() {
        // P1 骨架：仅同步活跃订单数到客户端
        this.activeOrderCount = scheduler.getActiveCount();
        // TODO: P1 完整实现 —— 从 scheduler 取订单、执行子批次、注入产物
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
        return AECableType.SMART;
    }

    @Override
    public void securityBreak() {
    }

    // ---------- 网络代理生命周期 ----------

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
        this.activeOrderCount = compound.getInteger("activeOrderCount");
        if (compound.hasKey("proxy")) {
            getProxy().readFromNBT(compound.getCompoundTag("proxy"));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        compound.setInteger("parallelLimit", parallelLimit);
        compound.setInteger("activeOrderCount", activeOrderCount);
        getProxy().writeToNBT(compound);
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
}
