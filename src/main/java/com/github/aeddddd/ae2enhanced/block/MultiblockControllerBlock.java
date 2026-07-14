package com.github.aeddddd.ae2enhanced.block;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.ChatFormatting;

import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.util.BlockEntityRemovalHelper;

/**
 * 多方块控制器方块的通用基类。
 * <p>统一处理 FACING 属性、放置时加入索引、移除时解散结构、以及服务端 tick 调度。
 * 具体的菜单交互、结构验证与索引类型由子类实现。</p>
 */
public abstract class MultiblockControllerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MultiblockControllerBlock(Properties properties) {
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public abstract BlockEntity newBlockEntity(BlockPos pos, BlockState state);

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : (lvl, p, st, be) -> {
            if (be instanceof MultiblockControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            addToIndex(serverLevel, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && state.getBlock() != newState.getBlock()) {
            BlockEntityRemovalHelper.markForBreak(level.getBlockEntity(pos));
            disassembleStructure(level, pos);
            if (level instanceof ServerLevel serverLevel) {
                removeFromIndex(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 获取当前控制器对应的多方块结构定义。
     */
    public abstract IMultiblockStructure getStructure();

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("tooltip.ae2enhanced.structure_projection.toggle")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.ae2enhanced.structure_projection.hold_shift")
                .withStyle(ChatFormatting.DARK_GRAY));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.ae2enhanced.structure_projection.materials")
                    .withStyle(ChatFormatting.YELLOW));
            Map<Block, Integer> materials = getStructure().getRequiredMaterials();
            for (Map.Entry<Block, Integer> entry : materials.entrySet()) {
                Component name = entry.getKey().getName().withStyle(ChatFormatting.GRAY);
                tooltip.add(Component.literal("  ").append(name)
                        .append(Component.literal(" x" + entry.getValue()).withStyle(ChatFormatting.WHITE)));
            }
        }
    }

    /**
     * 将控制器位置加入维度索引，供 StructureEventHandler 快速查找。
     */
    protected abstract void addToIndex(ServerLevel level, BlockPos pos);

    /**
     * 从维度索引中移除控制器位置。
     */
    protected abstract void removeFromIndex(ServerLevel level, BlockPos pos);

    /**
     * 当控制器方块被移除时，解散其对应的多方块结构。
     */
    protected abstract void disassembleStructure(Level level, BlockPos pos);
}
