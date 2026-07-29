package com.github.aeddddd.ae2enhanced.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkBlockEntity;

import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * 超因果计算核心结构方块实体.
 * <p>本身不提供 AE2 服务,仅作为网格节点让任意结构方块都能连接 ME 网络.
 * 成形后通过相邻节点与计算核心控制器共享同一网络.</p>
 */
public class ComputationCasingBlockEntity extends AENetworkBlockEntity {

    public ComputationCasingBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.COMPUTATION_CASING.get(), pos, state);
    }

    public ComputationCasingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setIdlePowerUsage(0.0)
                .setVisualRepresentation(getBlockState().getBlock().asItem());
    }

    @Override
    public AECableType getCableConnectionType(net.minecraft.core.Direction dir) {
        return AECableType.SMART;
    }
}
