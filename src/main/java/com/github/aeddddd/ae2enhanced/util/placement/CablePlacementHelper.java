package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEColor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 线缆放置辅助类。
 *
 * 功能：
 * 1. 根据起点、终点计算曼哈顿最短路径。
 * 2. 沿路径放置 AE2 线缆。
 * 3. 支持颜色选择。
 */
public final class CablePlacementHelper {

    private CablePlacementHelper() {}

    /**
     * 执行线缆放置。
     *
     * @param player     玩家
     * @param world      世界
     * @param start      起点
     * @param end        终点
     * @param hand       手
     * @param toolStack  工具
     * @param cableStack 线缆物品（基础类型，颜色会被覆盖）
     * @param color      目标颜色
     * @return 实际放置的位置列表（用于撤销）
     */
    public static List<BlockPos> placeCable(EntityPlayer player, World world,
                                             BlockPos start, BlockPos end,
                                             EnumHand hand, ItemStack toolStack,
                                             ItemStack cableStack, AEColor color) {
        List<BlockPos> result = new ArrayList<>();
        if (world.isRemote) return result;

        IGrid grid = SecurityTerminalBindingHelper.getLinkedGrid(toolStack, world, player);
        if (grid == null) return result;

        IMEMonitor<IAEItemStack> monitor = SecurityTerminalBindingHelper.getItemMonitor(grid);
        if (monitor == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_storage"));
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
        IAEItemStack request = PlacementTargetResolver.findCableOfType(monitor, cableStack);
        if (request == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    placeStack.getDisplayName()));
            return result;
        }

        // 模拟提取全部
        IAEItemStack toExtract = request.copy();
        toExtract.setStackSize(path.size());
        IAEItemStack simulated = monitor.extractItems(toExtract, Actionable.SIMULATE,
                SecurityTerminalBindingHelper.createPlayerSource(player));
        if (simulated == null || simulated.getStackSize() < path.size()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    placeStack.getDisplayName()));
            return result;
        }

        ItemStack actualPlaceStack = placeStack.copy();
        actualPlaceStack.setCount(1);

        List<BlockPos> placed = new ArrayList<>();
        try {
            for (BlockPos pos : path) {
                if (!canPlaceCableAt(world, pos)) continue;
                // 每次放置使用新的 stack 副本，防止 AE placeBus 修改后影响后续放置
                ItemStack stackForPos = actualPlaceStack.copy();
                if (tryPlaceCable(player, world, pos, stackForPos, hand)) {
                    placed.add(pos);
                }
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Exception during cable placement", e);
        }

        if (placed.isEmpty()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.cannot_place"));
            return result;
        }

        // 实际提取已放置数量
        IAEItemStack finalExtract = request.copy();
        finalExtract.setStackSize(placed.size());
        IAEItemStack extracted = monitor.extractItems(finalExtract, Actionable.MODULATE,
                SecurityTerminalBindingHelper.createPlayerSource(player));
        if (extracted == null || extracted.getStackSize() < placed.size()) {
            // 回滚
            for (BlockPos pos : placed) {
                world.setBlockToAir(pos);
            }
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    placeStack.getDisplayName()));
            return result;
        }

        world.playSound(null, start, net.minecraft.init.SoundEvents.BLOCK_STONE_PLACE,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.swingArm(hand);

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
            current = current.getY() < end.getY() ? current.up() : current.down();
            result.add(current);
        }
        // Z
        while (current.getZ() != end.getZ()) {
            current = current.getZ() < end.getZ() ? current.south() : current.north();
            result.add(current);
        }

        return result;
    }

    private static boolean canPlaceCableAt(World world, BlockPos pos) {
        return world.isAirBlock(pos) || world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    private static boolean tryPlaceCable(EntityPlayer player, World world, BlockPos pos,
                                          ItemStack cableStack, EnumHand hand) {
        // 线缆放在方块中心（ INTERNAL 位置）
        ItemStack originalMain = player.getHeldItemMainhand();
        try {
            player.setHeldItem(EnumHand.MAIN_HAND, cableStack);
            EnumActionResult result = AEApi.instance().partHelper().placeBus(cableStack, pos, EnumFacing.DOWN, player, hand, world);
            return result == EnumActionResult.SUCCESS;
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, originalMain);
        }
    }
}
