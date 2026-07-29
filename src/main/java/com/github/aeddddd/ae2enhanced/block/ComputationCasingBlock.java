package com.github.aeddddd.ae2enhanced.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.blockentity.ComputationCasingBlockEntity;

/**
 * 超因果计算核心结构方块基类.
 * <p>附带网格节点方块实体,使任意结构方块均可连接 ME 网络.</p>
 */
public class ComputationCasingBlock extends Block implements EntityBlock {

    public ComputationCasingBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputationCasingBlockEntity(pos, state);
    }
}
