package com.github.aeddddd.ae2enhanced.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraftforge.network.NetworkHooks;

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalNexusMenu;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalUnformedMenu;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
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
            // 成形：打开 Nexus 信息 GUI
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inv, p) -> new HyperdimensionalNexusMenu(id, inv, pos),
                        Component.translatable("gui.ae2enhanced.hyperdimensional_nexus")), pos);
            }
            return InteractionResult.SUCCESS;
        }

        // 未成形：打开缺失方块 GUI
        if (player instanceof ServerPlayer serverPlayer) {
            java.util.Map<Block, Integer> missing = HyperdimensionalStructure.getMissingMap(level, pos);
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new HyperdimensionalUnformedMenu(id, inv, pos, missing),
                    Component.translatable("gui.ae2enhanced.hyperdimensional_unformed")), buf -> {
                        buf.writeBlockPos(pos);
                        HyperdimensionalUnformedMenu.encodeMissing(buf, missing);
                    });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            ControllerIndex.get(serverLevel).add(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && state.getBlock() != newState.getBlock()) {
            HyperdimensionalStructure.disassemble(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                ControllerIndex.get(serverLevel).remove(pos);
            }
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
