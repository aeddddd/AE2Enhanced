package com.github.aeddddd.ae2enhanced.computation.cpu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCPUClusterInvoker;

/**
 * 超因果计算核心提供的虚拟 AE2 Crafting CPU.
 * <p>内部包装一个真正的 {@link CraftingCPUCluster},通过虚假合成单元方块实体
 * 把集群的节点指向通用 ME 接口,从而完整参与 AE2 自动合成调度.</p>
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

        // AE2 限制单个合成单元协处理器线程数不超过 16（addBlockEntity 超限抛异常）,
        // 因此将并行上限拆分为多个虚假单元注册；存储容量只计在首个单元上,防止累加溢出.
        int remainingThreads = Math.max(1, parallel);
        boolean first = true;
        while (remainingThreads > 0) {
            int threads = Math.min(remainingThreads, 16);
            VirtualCraftingUnitBlockEntity fakeUnit = new VirtualCraftingUnitBlockEntity(level,
                    pos, AEBlocks.CRAFTING_UNIT.block().defaultBlockState(), interfaceNode, threads,
                    first ? Long.MAX_VALUE : 0);
            ((CraftingCPUClusterInvoker) (Object) cluster).invokeAddBlockEntity(fakeUnit);
            first = false;
            remainingThreads -= threads;
        }

        try {
            ((IVirtualCraftingCPU) (Object) cluster).ae2enhanced$setHost(host);
        } catch (ClassCastException e) {
            AE2Enhanced.LOGGER.warn("MixinCraftingCPUCluster 未加载,虚拟 CPU 将完全依赖虚假方块实体.");
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

    /**
     * CPU 库存是否仍有残余材料（任务结束后待归还网络的物品）.
     * <p>任务在网络回流调用栈内完成时,原生 {@code finishJob → storeItems} 会被
     * {@code NetworkStorage} 的 mountsInUse 重入保护静默拒绝,残余材料要等下一拍
     * {@code tickCraftingLogic} 重试才能归还;而原生 {@code destroy()} 不清空合成库存,
     * 带残余销毁集群会让材料随集群对象一并丢失.池回收/解绑销毁前必须先检查本方法.</p>
     */
    public boolean hasStoredItems() {
        return !cluster.craftingLogic.getInventory().list.isEmpty();
    }

    public ComputationCoreBlockEntity getHost() {
        return host;
    }

    public void destroy() {
        cluster.destroy();
    }
}
