package com.github.aeddddd.ae2enhanced.memorycard.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;

/**
 * UMC 选取与绑定逻辑服务.
 *
 * <p>绑定逻辑相对 1.12 已重构:1.12 的「绑定中枢 ME 接口」(一对多网络)与
 * 「绑定 ME 网络回收节点」均不移植;绑定用途(粘贴缺升级卡时向网络请求物品)
 * 改为绑定 AE2 原生无线访问点(Wireless Access Point),见 {@link UMCNetworkLink}.</p>
 */
public class UMCSelectionService {

    public static void handleSelect(Player player, ItemStack stack, BlockPos pos, Direction face) {
        Level level = player.level();
        String dim = UniversalMemoryCardItem.dimensionId(level);
        BlockEntity be = level.getBlockEntity(pos);

        List<UniversalMemoryCardItem.SelectionEntry> selections = UniversalMemoryCardItem.getSelections(stack);
        for (int i = 0; i < selections.size(); i++) {
            UniversalMemoryCardItem.SelectionEntry entry = selections.get(i);
            if (entry.dim.equals(dim) && entry.pos.equals(pos)) {
                UniversalMemoryCardItem.removeSelection(stack, i);
                player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.deselect"), false);
                return;
            }
        }

        if (be instanceof IPartHost host) {
            IPart part = host.getPart(face);
            if (part != null) {
                String tileId = part.getClass().getName();
                int side = face.get3DDataValue();
                UniversalMemoryCardItem.addSelection(stack,
                        new UniversalMemoryCardItem.SelectionEntry(pos, dim, tileId, side));
                player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.select_part"), false);
                return;
            }
        }

        if (be != null) {
            String tileId = be.getClass().getName();
            List<BlockPos> connected = findConnectedBlocks(level, pos, be.getClass(), 64);
            for (BlockPos p : connected) {
                UniversalMemoryCardItem.addSelection(stack,
                        new UniversalMemoryCardItem.SelectionEntry(p, dim, tileId, -1));
            }
            player.displayClientMessage(
                    Component.translatable("gui.ae2enhanced.umc.msg.select_tile", connected.size()), false);
        } else {
            String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            UniversalMemoryCardItem.addSelection(stack,
                    new UniversalMemoryCardItem.SelectionEntry(pos, dim, blockId, -1));
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.select_block"), false);
        }
    }

    /**
     * 绑定无线访问点:粘贴缺升级卡/缺物品时可向该网络发起提取/自动合成请求.
     */
    public static void handleBindAccessPoint(Player player, ItemStack stack, BlockPos pos) {
        Level level = player.level();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IWirelessAccessPoint accessPoint)) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.bind_invalid_ap"), false);
            return;
        }
        if (accessPoint.getGrid() == null) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.bind_ap_offline"), false);
            return;
        }
        GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
        UMCNetworkLink.link(stack, globalPos);
        player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.bind_success",
                globalPos.dimension().location() + " " + globalPos.pos().toShortString()), false);
    }

    /**
     * 解除内存卡的无线访问点绑定.
     */
    public static void handleClearBinding(Player player, ItemStack stack) {
        if (!UMCNetworkLink.isLinked(stack)) {
            player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.no_binding"), false);
            return;
        }
        UMCNetworkLink.unlink(stack);
        player.displayClientMessage(Component.translatable("gui.ae2enhanced.umc.msg.binding_cleared"), false);
    }

    private static List<BlockPos> findConnectedBlocks(Level level, BlockPos start, Class<?> beClass, int maxCount) {
        List<BlockPos> result = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxCount) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.getClass() == beClass) {
                result.add(pos);

                for (Direction facing : Direction.values()) {
                    BlockPos neighbor = pos.relative(facing);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        return result;
    }
}
