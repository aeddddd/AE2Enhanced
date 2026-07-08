package com.github.aeddddd.ae2enhanced.assembly.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyCasingBlockEntity;

public class AssemblyCasingBlock extends Block implements EntityBlock {
    public AssemblyCasingBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblyCasingBlockEntity(pos, state);
    }
}
