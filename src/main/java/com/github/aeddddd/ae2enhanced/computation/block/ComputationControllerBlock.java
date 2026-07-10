package com.github.aeddddd.ae2enhanced.computation.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.client.gui.ComputationCoreMenu;
import com.github.aeddddd.ae2enhanced.client.gui.ComputationUnformedMenu;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.ComputationCoreIndex;
import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

/**
 * 超因果计算核心控制器方块。
 */
public class ComputationControllerBlock extends MultiblockControllerBlock {

    public ComputationControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || level.isClientSide()) {
            return InteractionResult.PASS;
        }

        if (level.getBlockEntity(pos) instanceof ComputationCoreBlockEntity controller) {
            if (controller.isFormed()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                            (id, inv, p) -> new ComputationCoreMenu(id, inv, pos),
                            Component.translatable("gui.ae2enhanced.computation_core")), pos);
                }
            } else {
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                            (id, inv, p) -> new ComputationUnformedMenu(id, inv, pos),
                            Component.translatable("gui.ae2enhanced.computation.unformed.title")), pos);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputationCoreBlockEntity(pos, state);
    }

    @Override
    protected void addToIndex(ServerLevel level, BlockPos pos) {
        ComputationCoreIndex.get(level).add(pos);
    }

    @Override
    protected void removeFromIndex(ServerLevel level, BlockPos pos) {
        ComputationCoreIndex.get(level).remove(pos);
    }

    @Override
    protected IMultiblockStructure getStructure() {
        return SupercausalStructure.INSTANCE;
    }

    @Override
    protected void disassembleStructure(Level level, BlockPos pos) {
        SupercausalStructure.disassemble(level, pos);
    }
}
