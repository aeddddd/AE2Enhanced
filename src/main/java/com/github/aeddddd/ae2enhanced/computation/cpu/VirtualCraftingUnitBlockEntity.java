package com.github.aeddddd.ae2enhanced.computation.cpu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlockEntities;

/**
 * 供虚拟 CPU 使用的虚假 AE2 合成单元方块实体。
 * <p>不放入世界，仅用于给 {@link appeng.me.cluster.implementations.CraftingCPUCluster}
 * 提供一个指向实际通用 ME 接口节点的 {@link #getActionableNode()}。</p>
 */
public class VirtualCraftingUnitBlockEntity extends CraftingBlockEntity {

    private final IManagedGridNode interfaceNode;

    public VirtualCraftingUnitBlockEntity(Level level, BlockPos pos, BlockState state,
            IManagedGridNode interfaceNode) {
        super(AEBlockEntities.CRAFTING_UNIT, pos, state);
        this.interfaceNode = interfaceNode;
        setLevel(level);
    }

    @Override
    public IGridNode getActionableNode() {
        return interfaceNode.getNode();
    }

    @Override
    public long getStorageBytes() {
        return Long.MAX_VALUE;
    }

    @Override
    public int getAcceleratorThreads() {
        return 16;
    }

    @Override
    public void breakCluster() {
        // 虚拟方块不处于真实世界，取消默认的掉落行为
    }
}
