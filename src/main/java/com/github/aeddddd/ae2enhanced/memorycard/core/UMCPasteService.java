package com.github.aeddddd.ae2enhanced.memorycard.core;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;

/**
 * UMC 粘贴逻辑服务(含批量粘贴).
 */
public class UMCPasteService {

    public static void handlePaste(Player player, ItemStack stack, BlockPos pos, Direction face) {
        if (!UniversalMemoryCardItem.hasConfig(stack)) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.no_config"), false);
            return;
        }

        CompoundTag config = UniversalMemoryCardItem.getConfig(stack);
        CompoundTag data = config.getCompound("data");

        Level level = player.level();
        String dim = UniversalMemoryCardItem.dimensionId(level);
        Object target = UMCCopyService.findTarget(level, pos, face);
        if (target == null) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.paste_invalid"), false);
            return;
        }

        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(stack);
        boolean isBulk = false;
        for (UniversalMemoryCardItem.SelectionEntry entry : selections) {
            if (entry.dim.equals(dim) && entry.pos.equals(pos)) {
                isBulk = true;
                break;
            }
        }

        if (isBulk) {
            int success = 0;
            int failed = 0;
            for (UniversalMemoryCardItem.SelectionEntry entry : selections) {
                if (!entry.dim.equals(dim)) {
                    continue;
                }
                Object bulkTarget = resolveTarget(level, entry);
                if (bulkTarget == null) {
                    continue;
                }
                IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(bulkTarget);
                if (handler == null) {
                    continue;
                }
                PasteResult result = handler.paste(bulkTarget, data, player);
                if (result == PasteResult.SUCCESS) {
                    success++;
                } else {
                    failed++;
                }
            }
            player.displayClientMessage(
                    Component.translatable("gui.ae2enhanced.umc.msg.bulk_success", success, failed), false);
        } else {
            IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
            if (handler == null) {
                player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.paste_unsupported"), false);
                return;
            }
            PasteResult result = handler.paste(target, data, player);
            switch (result) {
                case SUCCESS -> player.displayClientMessage(
                        Component.translatable("gui.ae2enhanced.umc.msg.paste_success", handler.getDisplayName(target)),
                        false);
                case MISSING_UPGRADES -> {
                    StringBuilder req = new StringBuilder();
                    appendUpgradeNames(req, data, "ae2e:upgrades");
                    player.displayClientMessage(
                            Component.translatable("gui.ae2enhanced.umc.msg.missing_upgrades", req.toString()),
                            false);
                }
                case INVALID_MACHINE -> player.displayClientMessage(
                        Component.translatable("gui.ae2enhanced.umc.msg.invalid_machine"), false);
                case FAILED -> player.displayClientMessage(
                        Component.translatable("gui.ae2enhanced.umc.msg.paste_failed"), false);
            }
        }
    }

    private static void appendUpgradeNames(StringBuilder req, CompoundTag data, String key) {
        if (!data.contains(key)) {
            return;
        }
        ListTag upgList = data.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < upgList.size(); i++) {
            ItemStack upg = ItemStack.of(upgList.getCompound(i));
            if (!upg.isEmpty()) {
                if (req.length() > 0) {
                    req.append(", ");
                }
                req.append(upg.getHoverName().getString());
                if (upg.getCount() > 1) {
                    req.append("×").append(upg.getCount());
                }
            }
        }
    }

    private static Object resolveTarget(Level level, UniversalMemoryCardItem.SelectionEntry entry) {
        if (!entry.dim.equals(UniversalMemoryCardItem.dimensionId(level))) {
            return null;
        }
        if (!level.isLoaded(entry.pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(entry.pos);
        if (be == null) {
            return null;
        }
        if (entry.side >= 0 && be instanceof IPartHost host) {
            IPart part = host.getPart(Direction.from3DDataValue(entry.side));
            if (part != null) {
                return part;
            }
        }
        return be;
    }
}
