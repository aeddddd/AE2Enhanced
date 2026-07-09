package com.github.aeddddd.ae2enhanced.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * AE2Enhanced 实体方块基类，提供 FORMED 方块状态属性与基本的交互/破坏行为。
 */
public abstract class AE2EBaseEntityBlock extends Block implements EntityBlock {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public AE2EBaseEntityBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        // Phase 1+ 在此打开对应 GUI / 处理扳手、记忆卡等工具交互
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // 交由 Minecraft 标准方块替换逻辑处理 BlockEntity 生命周期；
        // 不手动调用 level.removeBlockEntity，避免重复移除或跳过 setRemoved 回调。
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);
}
