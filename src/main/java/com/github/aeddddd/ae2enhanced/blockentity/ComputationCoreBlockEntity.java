package com.github.aeddddd.ae2enhanced.blockentity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;

import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.client.render.AbstractMultiblockRenderer;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.structure.ValidationResult;
import com.github.aeddddd.ae2enhanced.util.BlockEntityRemovalHelper;

/**
 * 超因果计算核心控制器方块实体.
 * <p>自身作为 AE2 网络节点（任意结构方块均可并网）,成形后维护一个虚拟
 * AE2 Crafting CPU 池,并通过 Mixin 注册到 CraftingService.</p>
 */
public class ComputationCoreBlockEntity extends AENetworkBlockEntity implements IMultiblockController {

    private static final String PARALLEL_LIMIT_TAG = "parallelLimit";
    private static final String POOL_SIZE_TAG = "poolSize";

    private final List<VirtualCraftingCPU> cpuPool = new ArrayList<>();
    private int parallelLimit = 0;
    private int validationCooldown = 0;
    private boolean formed = false;
    private boolean showingStructureProjection = false;

    // 客户端显示数据（经 getUpdateTag 同步,cpuPool 本身仅存在于服务端）
    private int clientPoolSize = 0;
    private int clientActiveJobs = 0;
    private boolean clientNetworkActive = false;
    private int lastSyncedPoolSize = -1;
    private int lastSyncedActiveJobs = -1;
    private boolean lastSyncedNetworkActive = false;

    public ComputationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTATION_CONTROLLER.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModItems.COMPUTATION_CONTROLLER.get());
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public int getActiveJobs() {
        int count = 0;
        for (VirtualCraftingCPU cpu : cpuPool) {
            if (cpu.isBusy()) {
                count++;
            }
        }
        return count;
    }

    // ---- 客户端显示数据访问（仅 GUI 使用） ----

    public int getClientPoolSize() {
        return clientPoolSize;
    }

    public int getClientActiveJobs() {
        return clientActiveJobs;
    }

    public boolean isClientNetworkActive() {
        return clientNetworkActive;
    }

    /**
     * 获取虚拟 CPU 挂靠的网格节点（控制器自身节点）.
     * <p>用于在 Mixin 中把虚拟集群的网格操作重定向到控制器所在网络.</p>
     *
     * @return 控制器节点,节点尚未创建时返回 null.
     */
    @Nullable
    public IGridNode getActionSourceNode() {
        IManagedGridNode node = getMainNode();
        return node != null ? node.getNode() : null;
    }

    // ---- IMultiblockController ----

    @Override
    public boolean isFormed() {
        return formed;
    }

    @Override
    public boolean isShowingStructureProjection() {
        return showingStructureProjection;
    }

    @Override
    public void toggleStructureProjection() {
        if (formed) {
            showingStructureProjection = false;
            return;
        }
        showingStructureProjection = !showingStructureProjection;
        setChanged();
        markForUpdate();
    }

    @Override
    public void setFormed(boolean formed) {
        if (this.formed != formed) {
            this.formed = formed;
            setChanged();
            markForUpdate();
        }
    }

    @Override
    public void assemble() {
        if (isFormed()) {
            return;
        }
        onAssemble();
        setFormed(true);
    }

    @Override
    public void disassemble() {
        if (!isFormed()) {
            return;
        }
        onDisassemble();
        setFormed(false);
    }

    @Override
    public void onAssemble() {
        IMultiblockStructure structure = getStructure();
        if (structure == null || level == null || level.isClientSide()) {
            return;
        }
        ValidationResult result = structure.validateDetailed(level, worldPosition);
        if (!result.passed()) {
            return;
        }
        this.parallelLimit = result.parallelLimit();
        bindVirtualCpu();
    }

    @Override
    public void onDisassemble() {
        unbindVirtualCpu();
        parallelLimit = 0;
    }

    @Override
    public BlockPos getControllerPos() {
        return worldPosition;
    }

    @Override
    @Nullable
    public IMultiblockStructure getStructure() {
        if (level == null) {
            return null;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof MultiblockControllerBlock controllerBlock) {
            return controllerBlock.getStructure();
        }
        return null;
    }

    @Override
    public boolean isVirtualCpuAvailable() {
        return isFormed();
    }

    @Override
    public int getVirtualCpuParallelLimit() {
        return isFormed() ? parallelLimit : 0;
    }

    @Override
    public IActionSource getActionSource() {
        return IActionSource.ofMachine(this);
    }

    // ---- Tick ----

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // 自愈:池为空、或池内集群已销毁/被移出注册表（节点掉线、区块重载等）时重建
        if (isFormed() && !isPoolHealthy()) {
            unbindVirtualCpu();
            bindVirtualCpu();
        }

        if (isFormed()) {
            managePool();
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            IMultiblockStructure structure = getStructure();
            if (structure != null && isFormed() && !structure.validateDetailed(level, worldPosition).passed()) {
                structure.disassemble(level, worldPosition);
            }

            // 池规模/活跃任务/网络状态变化时同步客户端显示数据
            int activeJobs = getActiveJobs();
            IManagedGridNode node = getMainNode();
            boolean networkActive = node != null && node.isActive();
            if (cpuPool.size() != lastSyncedPoolSize || activeJobs != lastSyncedActiveJobs
                    || networkActive != lastSyncedNetworkActive) {
                lastSyncedPoolSize = cpuPool.size();
                lastSyncedActiveJobs = activeJobs;
                lastSyncedNetworkActive = networkActive;
                markForUpdate();
            }
        }
    }

    private void managePool() {
        // 分裂发生在任务提交时（见 spawnSubCpu,参考 AAE 量子计算机）,
        // tick 中只清理多余的空闲 CPU,保留至少 1 个空闲 CPU,不销毁忙碌 CPU
        int idleCount = 0;
        for (VirtualCraftingCPU cpu : cpuPool) {
            if (!cpu.isBusy()) {
                idleCount++;
            }
        }
        Iterator<VirtualCraftingCPU> iterator = cpuPool.iterator();
        while (iterator.hasNext() && idleCount > 1) {
            VirtualCraftingCPU cpu = iterator.next();
            if (cpu.isBusy()) {
                continue;
            }
            VirtualCraftingCPURegistry.unregister(cpu.getCluster());
            cpu.destroy();
            iterator.remove();
            idleCount--;
            setChanged();
        }
    }

    /**
     * 提交任务时无空闲子 CPU,立即分裂一个新的子 CPU（参考 AAE 量子计算机的自动分裂）.
     *
     * @return 新分裂子 CPU 的集群,达到池上限或节点未就绪时返回 null.
     */
    @Nullable
    public VirtualCraftingCPU spawnSubCpu() {
        if (!isFormed() || cpuPool.size() >= AE2EnhancedConfig.COMMON.computationMaxCpus.get()) {
            return null;
        }
        return createCpu();
    }

    /**
     * 池是否健康：非空且所有集群未销毁、仍在注册表中.
     * <p>集群可能因节点掉线被移出注册表、或区块重载后失效,此时需整体重建.</p>
     */
    private boolean isPoolHealthy() {
        if (cpuPool.isEmpty()) {
            return false;
        }
        for (VirtualCraftingCPU cpu : cpuPool) {
            if (cpu.isDestroyed() || !VirtualCraftingCPURegistry.getClusters().contains(cpu.getCluster())) {
                return false;
            }
        }
        return true;
    }

    private void bindVirtualCpu() {
        if (level == null || !cpuPool.isEmpty()) {
            return;
        }
        createCpu();
    }

    @Nullable
    private VirtualCraftingCPU createCpu() {
        if (level == null) {
            return null;
        }
        IManagedGridNode node = getMainNode();
        if (node == null || !node.isReady()) {
            return null;
        }
        VirtualCraftingCPU cpu = new VirtualCraftingCPU(this, node, level, worldPosition, parallelLimit);
        cpuPool.add(cpu);
        VirtualCraftingCPURegistry.register(cpu.getCluster());
        setChanged();
        return cpu;
    }

    private void unbindVirtualCpu() {
        for (VirtualCraftingCPU cpu : cpuPool) {
            VirtualCraftingCPURegistry.unregister(cpu.getCluster());
            cpu.destroy();
        }
        cpuPool.clear();
        setChanged();
    }

    // ---- 渲染 ----

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos pos = getBlockPos();
        Direction facing = Direction.NORTH;
        if (level != null) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(MultiblockControllerBlock.FACING)) {
                facing = state.getValue(MultiblockControllerBlock.FACING);
            }
        }
        IMultiblockStructure structure = getStructure();
        Set<BlockPos> positions = structure != null ? structure.getAllPositions() : Set.of();
        float[] bounds = AbstractMultiblockRenderer.computeBounds(positions, facing);
        Vec3 center = AbstractMultiblockRenderer.computeCenterOffset(bounds);
        double radius = AbstractMultiblockRenderer.computeRadius(bounds) + 15.0;
        Vec3 worldCenter = new Vec3(pos.getX() + center.x, pos.getY() + center.y, pos.getZ() + center.z);
        return new AABB(worldCenter, worldCenter).inflate(radius);
    }

    // ---- 生命周期 ----

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide() && BlockEntityRemovalHelper.isBlockBeingBroken(this)) {
            unbindVirtualCpu();
            if (isFormed()) {
                disassemble();
            }
        }
        super.setRemoved();
    }

    // ---- NBT ----

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        formed = data.getBoolean("formed");
        showingStructureProjection = data.getBoolean("showProjection");
        parallelLimit = data.getInt(PARALLEL_LIMIT_TAG);
        // 池大小仅用于记录,实际 CPU 在加载后由 serverTick 重新创建
        data.getInt(POOL_SIZE_TAG);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        data.putBoolean("formed", formed);
        data.putBoolean("showProjection", showingStructureProjection);
        data.putInt(PARALLEL_LIMIT_TAG, parallelLimit);
        data.putInt(POOL_SIZE_TAG, cpuPool.size());
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("formed", formed);
        tag.putBoolean("showProjection", showingStructureProjection);
        tag.putInt(PARALLEL_LIMIT_TAG, parallelLimit);
        tag.putInt("clientPoolSize", cpuPool.size());
        tag.putInt("clientActiveJobs", getActiveJobs());
        IManagedGridNode node = getMainNode();
        tag.putBoolean("clientNetworkActive", node != null && node.isActive());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("formed", Tag.TAG_BYTE)) {
            this.formed = tag.getBoolean("formed");
        }
        if (tag.contains("showProjection", Tag.TAG_BYTE)) {
            this.showingStructureProjection = tag.getBoolean("showProjection");
        }
        if (tag.contains(PARALLEL_LIMIT_TAG, Tag.TAG_INT)) {
            this.parallelLimit = tag.getInt(PARALLEL_LIMIT_TAG);
        }
        if (tag.contains("clientPoolSize", Tag.TAG_INT)) {
            this.clientPoolSize = tag.getInt("clientPoolSize");
        }
        if (tag.contains("clientActiveJobs", Tag.TAG_INT)) {
            this.clientActiveJobs = tag.getInt("clientActiveJobs");
        }
        if (tag.contains("clientNetworkActive", Tag.TAG_BYTE)) {
            this.clientNetworkActive = tag.getBoolean("clientNetworkActive");
        }
    }
}
