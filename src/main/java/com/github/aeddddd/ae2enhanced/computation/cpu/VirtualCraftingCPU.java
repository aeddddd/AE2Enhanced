package com.github.aeddddd.ae2enhanced.computation.cpu;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;

/**
 * 超因果计算核心提供的虚拟 AE2 Crafting CPU。
 * <p>内部包装一个真正的 {@link CraftingCPUCluster}，通过虚假合成单元方块实体
 * 把集群的节点指向通用 ME 接口，从而完整参与 AE2 自动合成调度。</p>
 */
public class VirtualCraftingCPU {

    private static final Method ADD_BLOCK_ENTITY;

    static {
        try {
            ADD_BLOCK_ENTITY = CraftingCPUCluster.class.getDeclaredMethod("addBlockEntity", CraftingBlockEntity.class);
            ADD_BLOCK_ENTITY.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to locate CraftingCPUCluster.addBlockEntity", e);
        }
    }

    private final ComputationCoreBlockEntity host;
    private final IManagedGridNode interfaceNode;
    private final CraftingCPUCluster cluster;

    public VirtualCraftingCPU(ComputationCoreBlockEntity host, IManagedGridNode interfaceNode,
            Level level, BlockPos pos) {
        this.host = host;
        this.interfaceNode = interfaceNode;
        this.cluster = new CraftingCPUCluster(pos, pos);

        VirtualCraftingUnitBlockEntity fakeUnit = new VirtualCraftingUnitBlockEntity(level,
                pos, AEBlocks.CRAFTING_UNIT.block().defaultBlockState(), interfaceNode);
        try {
            ADD_BLOCK_ENTITY.invoke(cluster, fakeUnit);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to add virtual unit to CraftingCPUCluster", e);
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

    public void destroy() {
        cluster.destroy();
    }
}
