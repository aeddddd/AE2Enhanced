package com.github.aeddddd.ae2enhanced.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储中枢控制器。
 * <p>与 master 一致，仅保留 facing 属性；成形状态由方块实体维护。</p>
 */
public class HyperdimensionalControllerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public HyperdimensionalControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || level.isClientSide()) {
            return InteractionResult.PASS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof HyperdimensionalControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }

        if (controller.isFormed()) {
            // Phase 1 将在此打开 Hyperdimensional Nexus GUI
            return InteractionResult.SUCCESS;
        }

        // 未成形：尝试一键装配
        if (player.getAbilities().instabuild) {
            HyperdimensionalStructure.placeMissingBlocks(level, pos, player);
        } else {
            HyperdimensionalStructure.tryConsumeAndPlace(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && state.getBlock() != newState.getBlock()) {
            HyperdimensionalStructure.disassemble(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, isMoving);
        if (level.isClientSide()) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HyperdimensionalControllerBlockEntity controller) {
            if (controller.isFormed() && !HyperdimensionalStructure.validate(level, pos)) {
                HyperdimensionalStructure.disassemble(level, pos);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperdimensionalControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : (lvl, p, st, be) -> {
            if (be instanceof HyperdimensionalControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }
}
