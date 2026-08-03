package com.github.aeddddd.ae2enhanced.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalCasingBlockEntity;

/**
 * 超维度仓储中枢外壳方块.
 */
public class HyperdimensionalCasingBlock extends Block implements EntityBlock {

    public HyperdimensionalCasingBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperdimensionalCasingBlockEntity(pos, state);
    }
}
