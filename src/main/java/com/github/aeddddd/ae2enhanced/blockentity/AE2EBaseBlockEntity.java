package com.github.aeddddd.ae2enhanced.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.AEBaseBlockEntity;

/**
 * AE2Enhanced 非网络方块实体基类。
 * 仅用于不需要 AE2 网格节点的方块（如多方块控制器外壳、核心等）。
 */
public class AE2EBaseBlockEntity extends AEBaseBlockEntity {

    public AE2EBaseBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }
}
