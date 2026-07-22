package com.github.aeddddd.ae2enhanced.computation.blockentity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingUnitBlockEntity;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCPUClusterInvoker;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModItems;

/**
 * 【仅开发环境】测试用单方块合成 CPU 的方块实体.
 * <p>自身作为 AE2 网络节点,包装一个不放入世界的 {@link CraftingCPUCluster}
 * （单个虚假合成单元：无限存储 + 16 协处理器）,经
 * {@link VirtualCraftingCPURegistry} 注入 CraftingService 参与调度.</p>
 * <p>注意：进行中的合成任务不持久化,区块卸载/破坏时集群直接销毁（测试用途足够）.</p>
 */
public class TestCraftingCpuBlockEntity extends AENetworkBlockEntity {

    /**
     * 单个虚假合成单元的协处理器线程数.AE2 限制每单元不超过 16.
     */
    private static final int CO_PROCESSORS = 16;

    @Nullable
    private CraftingCPUCluster cluster;

    public TestCraftingCpuBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEST_CRAFTING_CPU.get(), pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(1.0)
                .setVisualRepresentation(ModItems.TEST_CRAFTING_CPU.get());
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // 集群丢失（节点掉线被 MixinCraftingService 移出注册表、区块重载等）时自愈重建
        boolean alive = cluster != null && !cluster.isDestroyed()
                && VirtualCraftingCPURegistry.getClusters().contains(cluster);
        if (!alive) {
            rebuildCluster();
        }
    }

    private void rebuildCluster() {
        destroyCluster();
        IManagedGridNode node = getMainNode();
        if (node == null || !node.isReady()) {
            return;
        }
        CraftingCPUCluster newCluster = new CraftingCPUCluster(worldPosition, worldPosition);
        VirtualCraftingUnitBlockEntity fakeUnit = new VirtualCraftingUnitBlockEntity(
                level, worldPosition, AEBlocks.CRAFTING_UNIT.block().defaultBlockState(), node, CO_PROCESSORS);
        ((CraftingCPUClusterInvoker) (Object) newCluster).invokeAddBlockEntity(fakeUnit);
        this.cluster = newCluster;
        VirtualCraftingCPURegistry.register(newCluster);
    }

    private void destroyCluster() {
        if (cluster != null) {
            VirtualCraftingCPURegistry.unregister(cluster);
            cluster.destroy();
            cluster = null;
        }
    }

    @Override
    public void setRemoved() {
        destroyCluster();
        super.setRemoved();
    }
}
