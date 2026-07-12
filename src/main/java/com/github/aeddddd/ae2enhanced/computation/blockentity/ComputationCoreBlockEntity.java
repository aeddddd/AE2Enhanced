package com.github.aeddddd.ae2enhanced.computation.blockentity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridNode;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.structure.ValidationResult;

/**
 * 超因果计算核心控制器方块实体。
 * <p>成形后维护一个虚拟 AE2 Crafting CPU 池，并通过 Mixin 注册到 CraftingService。</p>
 */
public class ComputationCoreBlockEntity extends MultiblockControllerBlockEntity {

    private static final String INTERFACE_POS_TAG = "interfacePos";
    private static final String PARALLEL_LIMIT_TAG = "parallelLimit";
    private static final String POOL_SIZE_TAG = "poolSize";

    private final List<VirtualCraftingCPU> cpuPool = new ArrayList<>();
    @Nullable
    private BlockPos interfacePos;
    private int parallelLimit = 0;
    private int validationCooldown = 0;

    public ComputationCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTATION_CONTROLLER.get(), pos, state);
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

    /**
     * 获取虚拟 CPU 挂靠的通用 ME 接口节点。
     * <p>用于在 Mixin 中把虚拟集群的网格操作重定向到真实控制器。</p>
     *
     * @return 接口节点，若未绑定或接口已失效则返回 null。
     */
    @Nullable
    public IGridNode getActionSourceNode() {
        if (level == null || interfacePos == null) {
            return null;
        }
        if (level.getBlockEntity(interfacePos) instanceof MultiblockMeInterfaceBlockEntity me) {
            return me.getActionableNode();
        }
        return null;
    }

    /**
     * 结构装配成功时调用。
     *
     * @param parallel       每个虚拟 CPU 的并行上限
     * @param interfacePos   通用 ME 接口位置，虚拟 CPU 将挂靠在该接口节点上
     */
    public void assemble(int parallel, BlockPos interfacePos) {
        if (level == null || level.isClientSide() || isFormed()) {
            return;
        }
        this.interfacePos = interfacePos.immutable();
        this.parallelLimit = parallel;
        super.assemble();
    }

    @Override
    public void assemble() {
        if (isFormed()) {
            return;
        }
        super.assemble();
    }

    @Override
    public void onAssemble() {
        super.onAssemble();
        IMultiblockStructure structure = getStructure();
        if (structure == null || level == null || level.isClientSide()) {
            return;
        }
        ValidationResult result = structure.validateDetailed(level, worldPosition);
        if (!result.passed()) {
            return;
        }
        BlockPos interfacePos = findInterfacePos(structure);
        bindVirtualCpu(interfacePos, result.parallelLimit());
        this.interfacePos = interfacePos;
        this.parallelLimit = result.parallelLimit();
    }

    @Nullable
    private BlockPos findInterfacePos(IMultiblockStructure structure) {
        if (structure.getInterfaceRelativePos() == null || level == null) {
            return null;
        }
        net.minecraft.core.Direction facing = structure.getRotation(level, worldPosition);
        return worldPosition.offset(com.github.aeddddd.ae2enhanced.util.StructureUtils.rotate(structure.getInterfaceRelativePos(), facing));
    }

    @Override
    public void disassemble() {
        if (!isFormed()) {
            return;
        }
        super.disassemble();
    }

    @Override
    public void onDisassemble() {
        unbindVirtualCpu();
        interfacePos = null;
        parallelLimit = 0;
    }

    @Override
    public boolean isVirtualCpuAvailable() {
        return isFormed();
    }

    @Override
    public int getVirtualCpuParallelLimit() {
        return isFormed() ? parallelLimit : 0;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // 重新加载后若已成形但池为空，重新绑定初始 CPU
        if (isFormed() && cpuPool.isEmpty() && interfacePos != null) {
            bindVirtualCpu(interfacePos, parallelLimit);
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
        }
    }

    private void managePool() {
        if (interfacePos == null) {
            return;
        }

        int maxPoolSize = AE2EnhancedConfig.COMMON.computationMaxParallel.get();

        // 所有 CPU 都忙碌且未达池上限时，新增一个 CPU
        boolean allBusy = !cpuPool.isEmpty() && cpuPool.stream().allMatch(VirtualCraftingCPU::isBusy);
        if (allBusy && cpuPool.size() < maxPoolSize) {
            addCpuToPool();
        }

        // 清理多余的空闲 CPU，保留至少 1 个空闲 CPU，不销毁忙碌 CPU
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

    private void bindVirtualCpu(@Nullable BlockPos interfacePos, int parallelLimit) {
        if (level == null || interfacePos == null || !cpuPool.isEmpty()) {
            return;
        }
        if (level.getBlockEntity(interfacePos) instanceof MultiblockMeInterfaceBlockEntity me) {
            VirtualCraftingCPU cpu = new VirtualCraftingCPU(this, me.getMainNode(), level, interfacePos, parallelLimit);
            cpuPool.add(cpu);
            VirtualCraftingCPURegistry.register(cpu.getCluster());
            setChanged();
        }
    }

    private void addCpuToPool() {
        if (level == null || interfacePos == null) {
            return;
        }
        if (level.getBlockEntity(interfacePos) instanceof MultiblockMeInterfaceBlockEntity me) {
            VirtualCraftingCPU cpu = new VirtualCraftingCPU(this, me.getMainNode(), level, interfacePos, parallelLimit);
            cpuPool.add(cpu);
            VirtualCraftingCPURegistry.register(cpu.getCluster());
            setChanged();
        }
    }

    private void unbindVirtualCpu() {
        for (VirtualCraftingCPU cpu : cpuPool) {
            VirtualCraftingCPURegistry.unregister(cpu.getCluster());
            cpu.destroy();
        }
        cpuPool.clear();
        setChanged();
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        long encoded = data.getLong(INTERFACE_POS_TAG);
        interfacePos = encoded != 0 ? BlockPos.of(encoded) : null;
        parallelLimit = data.getInt(PARALLEL_LIMIT_TAG);
        // 池大小仅用于记录，实际 CPU 在加载后由 serverTick 重新创建
        data.getInt(POOL_SIZE_TAG);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (interfacePos != null) {
            data.putLong(INTERFACE_POS_TAG, interfacePos.asLong());
        }
        data.putInt(PARALLEL_LIMIT_TAG, parallelLimit);
        data.putInt(POOL_SIZE_TAG, cpuPool.size());
    }
}
