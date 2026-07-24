package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;

/**
 * 线缆放置辅助类。
 *
 * 功能：
 * 1. 根据起点、终点计算曼哈顿最短路径。
 * 2. 沿路径放置 AE2 线缆。
 * 3. 支持颜色选择。
 *
 * <p>1.20.1 移植说明：1.12 通过 AEApi.partHelper().placeBus 在目标位置放置线缆；
 * AE2 15.x 对应做法为 {@link PartHelper#getOrPlacePartHost} 取得（或创建）
 * 线缆总线方块实体，再以 {@link IPartHost#addPart} 在中心（side=null）添加线缆 Part。</p>
 */
public final class CablePlacementHelper {

    private CablePlacementHelper() {}

    /**
     * 执行线缆放置。
     *
     * @param player     玩家
     * @param level      世界
     * @param start      起点
     * @param end        终点
     * @param hand       手
     * @param toolStack  工具
     * @param cableStack 线缆物品（基础类型，颜色会被覆盖）
     * @param color      目标颜色
     * @return 实际放置的位置列表（用于撤销）
     */
    public static List<BlockPos> placeCable(Player player, Level level,
            BlockPos start, BlockPos end,
            InteractionHand hand, ItemStack toolStack,
            ItemStack cableStack, AEColor color) {
        List<BlockPos> result = new ArrayList<>();
        if (level.isClientSide()) return result;

        IGrid grid = OmniToolNetworkLink.getLinkedGrid(toolStack, level);
        if (grid == null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.placement.no_storage"), false);
            return result;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        if (storage == null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.placement.no_storage"), false);
            return result;
        }

        List<BlockPos> path = calculatePath(start, end);
        if (path.isEmpty()) return result;

        // 生成目标颜色的线缆 stack
        ItemStack placeStack = PlacementTargetResolver.createCableOfColor(cableStack, color);
        if (placeStack.isEmpty()) {
            placeStack = cableStack.copy();
            placeStack.setCount(1);
        }

        // 网络中查找任意同类型线缆，不区分颜色
        AEItemKey request = PlacementTargetResolver.findCableOfType(storage, cableStack);
        if (request == null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.placement.network_missing",
                            placeStack.getHoverName()),
                    false);
            return result;
        }

        IActionSource source = IActionSource.ofPlayer(player);

        // 模拟提取全部
        long simulated = storage.extract(request, path.size(), Actionable.SIMULATE, source);
        if (simulated < path.size()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.placement.network_missing",
                            placeStack.getHoverName()),
                    false);
            return result;
        }

        ItemStack actualPlaceStack = placeStack.copy();
        actualPlaceStack.setCount(1);

        List<BlockPos> placed = new ArrayList<>();
        try {
            for (BlockPos pos : path) {
                if (!canPlaceCableAt(level, pos)) continue;
                // 每次放置使用新的 stack 副本，防止 addPart 修改后影响后续放置
                ItemStack stackForPos = actualPlaceStack.copy();
                if (tryPlaceCable(player, level, pos, stackForPos)) {
                    placed.add(pos);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Exception during cable placement", e);
        }

        if (placed.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.placement.cannot_place"), false);
            return result;
        }

        // 实际提取已放置数量
        long extracted = storage.extract(request, placed.size(), Actionable.MODULATE, source);
        if (extracted < placed.size()) {
            // 回滚
            for (BlockPos pos : placed) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.placement.network_missing",
                            placeStack.getHoverName()),
                    false);
            return result;
        }

        level.playSound(null, start, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.swing(hand, true);

        return placed;
    }

    /**
     * 计算曼哈顿最短路径，按 X → Y → Z 顺序优先。
     */
    public static List<BlockPos> calculatePath(BlockPos start, BlockPos end) {
        List<BlockPos> result = new ArrayList<>();
        if (start.equals(end)) {
            result.add(start);
            return result;
        }

        BlockPos current = start;
        // X
        while (current.getX() != end.getX()) {
            current = current.getX() < end.getX() ? current.east() : current.west();
            result.add(current);
        }
        // Y
        while (current.getY() != end.getY()) {
            current = current.getY() < end.getY() ? current.above() : current.below();
            result.add(current);
        }
        // Z
        while (current.getZ() != end.getZ()) {
            current = current.getZ() < end.getZ() ? current.south() : current.north();
            result.add(current);
        }

        return result;
    }

    private static boolean canPlaceCableAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    private static boolean tryPlaceCable(Player player, Level level, BlockPos pos, ItemStack cableStack) {
        // 线缆放在方块中心（side = null）
        if (!(cableStack.getItem() instanceof IPartItem<?> partItem)) {
            return false;
        }
        IPartHost host = PartHelper.getOrPlacePartHost(level, pos, false, player);
        if (host == null) {
            return false;
        }
        return addCenterPart(host, partItem, player);
    }

    private static <T extends IPart> boolean addCenterPart(IPartHost host, IPartItem<T> partItem, Player player) {
        T added = host.addPart(partItem, null, player);
        if (added == null) {
            if (host.isEmpty()) {
                host.cleanup();
            }
            return false;
        }
        return true;
    }
}
