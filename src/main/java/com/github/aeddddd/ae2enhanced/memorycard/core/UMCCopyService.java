package com.github.aeddddd.ae2enhanced.memorycard.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.blockentity.AEBaseBlockEntity;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;

/**
 * UMC 复制逻辑服务.
 */
public class UMCCopyService {

    public static void handleCopy(Player player, ItemStack stack, BlockPos pos, Direction face) {
        Level level = player.level();
        Object target = findTarget(level, pos, face);
        if (target == null) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.copy_invalid"), false);
            return;
        }

        IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
        if (handler == null) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.copy_unsupported"), false);
            return;
        }

        CompoundTag data = handler.copy(target);
        if (data == null || data.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("gui.ae2enhanced.umc.msg.copy_empty", handler.getDisplayName(target)),
                    false);
            return;
        }

        String handlerId;
        if (target instanceof IPart) {
            handlerId = "ae2_part";
        } else if (target instanceof AEBaseBlockEntity) {
            handlerId = "ae2_tile";
        } else {
            handlerId = "ae2e_custom";
        }

        UniversalMemoryCardItem.setConfig(stack, handlerId, handler.getDisplayName(target), data);
        player.displayClientMessage(
                Component.translatable("gui.ae2enhanced.umc.msg.copy_success", handler.getDisplayName(target)),
                false);
    }

    static Object findTarget(Level level, BlockPos pos, Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IPartHost host) {
            IPart part = host.getPart(face);
            if (part != null) {
                return part;
            }
        }
        return be;
    }
}
