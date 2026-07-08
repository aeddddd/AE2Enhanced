package com.github.aeddddd.ae2enhanced.assembly.blockentity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;

import appeng.api.util.AECableType;
import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.grid.AENetworkBlockEntity;

import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * 装配枢纽外壳方块实体。
 * <p>本身不提供 AE2 服务，仅作为网格节点让任意外壳方块都能连接 ME 网络。
 * 成形后通过相邻节点与装配控制器共享同一网络。</p>
 */
public class AssemblyCasingBlockEntity extends AENetworkBlockEntity {

    public AssemblyCasingBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ASSEMBLY_CASING.get(), pos, state);
    }

    public AssemblyCasingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
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
