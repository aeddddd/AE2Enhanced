package com.github.aeddddd.ae2enhanced.computation.cpu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCPUClusterInvoker;

/**
 * 超因果计算核心提供的虚拟 AE2 Crafting CPU。
 * <p>内部包装一个真正的 {@link CraftingCPUCluster}，通过虚假合成单元方块实体
 * 把集群的节点指向通用 ME 接口，从而完整参与 AE2 自动合成调度。</p>
 */
public class VirtualCraftingCPU {

    private final ComputationCoreBlockEntity host;
    private final IManagedGridNode interfaceNode;
    private final CraftingCPUCluster cluster;

    public VirtualCraftingCPU(ComputationCoreBlockEntity host, IManagedGridNode interfaceNode,
            Level level, BlockPos pos, int parallel) {
        this.host = host;
        this.interfaceNode = interfaceNode;
        this.cluster = new CraftingCPUCluster(pos, pos);

        VirtualCraftingUnitBlockEntity fakeUnit = new VirtualCraftingUnitBlockEntity(level,
                pos, AEBlocks.CRAFTING_UNIT.block().defaultBlockState(), interfaceNode, parallel);
        ((CraftingCPUClusterInvoker) (Object) cluster).invokeAddBlockEntity(fakeUnit);

        try {
            ((IVirtualCraftingCPU) (Object) cluster).ae2enhanced$setHost(host);
        } catch (ClassCastException e) {
            AE2Enhanced.LOGGER.warn("MixinCraftingCPUCluster 未加载，虚拟 CPU 将完全依赖虚假方块实体。");
        }
    }

    public CraftingCPUCluster getCluster() {
        return cluster;
    }

    public IGrid getGrid() {
        return interfaceNode.getGrid();
    }

    public boolean isActive() {
        return interfaceNode.isActive();
    }

    public boolean isDestroyed() {
        return cluster.isDestroyed();
    }

    public boolean isBusy() {
        return cluster.isBusy();
    }

    public ComputationCoreBlockEntity getHost() {
        return host;
    }

    public void destroy() {
        cluster.destroy();
    }
}
