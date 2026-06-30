package com.github.aeddddd.ae2enhanced.multiblock;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.block.AE2EBaseEntityBlock;

/**
 * 通用多方块 ME 接口方块。
 * <p>可被三种多方块复用，成形后作为该多方块对 AE2 网络的接入点。</p>
 */
public class MultiblockMeInterfaceBlock extends AE2EBaseEntityBlock {

    public MultiblockMeInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return state.getValue(FORMED) ? 10 : 0;
    }

    @Nullable
    @Override
    public MultiblockMeInterfaceBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockMeInterfaceBlockEntity(pos, state);
    }
}
