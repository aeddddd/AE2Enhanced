package com.github.aeddddd.ae2enhanced.assembly.block;

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

import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyMenu;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyUnformedMenu;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;

/**
 * 装配枢纽控制器方块。
 */
public class AssemblyControllerBlock extends MultiblockControllerBlock {

    public AssemblyControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof AssemblyControllerBlockEntity controller) {
                if (controller.isFormed()) {
                    NetworkHooks.openScreen(serverPlayer,
                            new SimpleMenuProvider((id, inv, p) -> new AssemblyMenu(id, inv, pos),
                                    Component.translatable("gui.ae2enhanced.assembly")),
                            buf -> buf.writeBlockPos(pos));
                } else {
                    NetworkHooks.openScreen(serverPlayer,
                            new SimpleMenuProvider((id, inv, p) -> new AssemblyUnformedMenu(id, inv, pos),
                                    Component.translatable("gui.ae2enhanced.unformed.title")),
                            buf -> buf.writeBlockPos(pos));
                }
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AssemblyControllerBlockEntity(pos, state);
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
        AssemblyStructure.disassemble(level, pos);
    }
}
