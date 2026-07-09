package com.github.aeddddd.ae2enhanced.block;

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

import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalNexusMenu;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalUnformedMenu;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储中枢控制器。
 */
public class HyperdimensionalControllerBlock extends MultiblockControllerBlock {

    public HyperdimensionalControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || level.isClientSide()) {
            return InteractionResult.PASS;
        }

        if (level.getBlockEntity(pos) instanceof HyperdimensionalControllerBlockEntity controller) {
            if (controller.isFormed()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                            (id, inv, p) -> new HyperdimensionalNexusMenu(id, inv, pos),
                            Component.translatable("gui.ae2enhanced.hyperdimensional_nexus")), pos);
                }
            } else {
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                            (id, inv, p) -> new HyperdimensionalUnformedMenu(id, inv, pos),
                            Component.translatable("gui.ae2enhanced.hyperdimensional_unformed")), buf -> buf.writeBlockPos(pos));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperdimensionalControllerBlockEntity(pos, state);
    }

    @Override
    protected void addToIndex(ServerLevel level, BlockPos pos) {
        ControllerIndex.get(level).add(pos);
    }

    @Override
    protected void removeFromIndex(ServerLevel level, BlockPos pos) {
        ControllerIndex.get(level).remove(pos);
    }

    @Override
    protected void disassembleStructure(Level level, BlockPos pos) {
        HyperdimensionalStructure.disassemble(level, pos);
    }
}
