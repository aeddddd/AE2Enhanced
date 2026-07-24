package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;

import appeng.api.config.Actionable;
import appeng.api.implementations.items.IFacadeItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.localization.PlayerMessages;
import appeng.items.parts.FacadeItem;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;

/**
 * 放置工具核心辅助类。
 * 负责从 ME 网络（或副手）提取物品并放置方块、AE2 Part、Facade、线缆，以及批量放置和撤销。
 *
 * <p>1.20.1 移植说明：方块放置改用 {@link UseOnContext} + {@code ItemStack.useOn}
 * （内部走 BlockItem.place，会正常触发 Forge BlockPlaceEvent）；
 * Part 放置改用 AE2 公开 API {@link PartHelper#usePartItem}（等价 1.12 的 placeBus）；
 * Facade 放置复刻 {@link FacadeItem} 的判定逻辑；
 * 网络存取改用 {@link MEStorage} + {@link AEItemKey}。</p>
 */
public final class PlacementToolHelper {

    private PlacementToolHelper() {}

    // 每个玩家最近一次的放置记录，用于撤销
    private static final Map<UUID, UndoRecord> PLAYER_UNDO = new LinkedHashMap<UUID, UndoRecord>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, UndoRecord> eldest) {
            return size() > 100;
        }
    };

    // ==================== 单格放置 ====================

    /**
     * 单格放置。自动解析目标物品（副手优先），从网络或副手提取并放置。
     *
     * @return 是否成功放置
     */
    public static boolean placeSingle(Player player, Level level, BlockPos pos, Direction side,
            InteractionHand hand, ItemStack toolStack, float hitX, float hitY, float hitZ) {
        if (level.isClientSide()) return true;

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, level, pos);
        if (target.isEmpty()) {
            sendMessage(player, "message.ae2enhanced.placement.no_configured_item");
            return false;
        }

        return placeSingleWithTarget(player, level, pos, side, hand, toolStack, target, hitX, hitY, hitZ);
    }

    private static boolean placeSingleWithTarget(Player player, Level level, BlockPos pos, Direction side,
            InteractionHand hand, ItemStack toolStack, ItemStack target,
            float hitX, float hitY, float hitZ) {
        IGrid grid = OmniToolNetworkLink.getLinkedGrid(toolStack, level);
        if (grid == null) {
            player.displayClientMessage(Component.translatable(PlayerMessages.DeviceNotLinked.getTranslationKey()),
                    false);
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        if (storage == null) {
            sendMessage(player, "message.ae2enhanced.placement.no_storage");
            return false;
        }

        IActionSource source = IActionSource.ofPlayer(player);

        // 线缆特殊处理：按配置颜色生成目标线缆
        if (PlacementTargetResolver.isCable(target)) {
            return placeSingleCable(player, level, pos, side, hand, toolStack, target, configColor(toolStack));
        }

        AEItemKey request = findMatchingStack(storage, target);
        if (request == null) {
            sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
            return false;
        }

        // 模拟提取
        long simulated = storage.extract(request, 1, Actionable.SIMULATE, source);
        if (simulated < 1) {
            // 如果目标是副手物品，尝试直接消耗副手
            if (isTargetFromOffhand(player, target)) {
                return placeFromOffhand(player, level, pos, side, hand, target, 1);
            }
            sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
            return false;
        }

        ItemStack placeStack = request.toStack();
        placeStack.setCount(1);

        BlockPos blockPlacePos = pos.relative(side);
        BlockSnapshot preSnapshot = BlockSnapshot.create(level.dimension(), level, blockPlacePos);
        BlockState prevState = level.getBlockState(blockPlacePos);

        boolean placed = false;
        PlacementTarget targetType = PlacementTarget.OTHER;

        try {
            if (placeStack.getItem() instanceof BlockItem) {
                targetType = PlacementTarget.BLOCK;
                placed = tryPlaceBlock(player, level, pos, side, hand, placeStack, hitX, hitY, hitZ);
            } else if (placeStack.getItem() instanceof IPartItem) {
                targetType = PlacementTarget.PART;
                placed = tryPlacePart(player, level, pos, side, hand, placeStack);
            } else if (placeStack.getItem() instanceof IFacadeItem) {
                targetType = PlacementTarget.FACADE;
                placed = tryPlaceFacade(player, level, pos, side, placeStack);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Exception during placement", e);
        }

        if (placed) {
            long extracted = storage.extract(request, 1, Actionable.MODULATE, source);
            if (extracted < 1) {
                rollbackSingle(level, pos, side, prevState, targetType);
                sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
                return false;
            }

            BlockPos soundPos = targetType == PlacementTarget.BLOCK ? blockPlacePos : pos;
            level.playSound(null, soundPos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.swing(hand, true);

            UndoRecord record = new UndoRecord();
            if (targetType == PlacementTarget.BLOCK) {
                record.snapshots.add(preSnapshot);
            }
            record.consumed.put(request, 1L);
            PLAYER_UNDO.put(player.getUUID(), record);
            return true;
        } else {
            sendMessage(player, "message.ae2enhanced.placement.cannot_place");
            return false;
        }
    }

    // ==================== 批量放置（建筑手杖模式） ====================

    /**
     * 批量放置。使用建筑手杖式扩展，最大 512 个方块。
     */
    public static boolean placeBulk(Player player, Level level, BlockPos pos, Direction side,
            InteractionHand hand, ItemStack toolStack, float hitX, float hitY, float hitZ) {
        if (level.isClientSide()) return true;

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = PlacementTargetResolver.resolveBulk(player, level, pos);
        if (target.isEmpty()) {
            sendMessage(player, "message.ae2enhanced.placement.no_configured_item");
            return false;
        }

        if (!(target.getItem() instanceof BlockItem)) {
            sendMessage(player, "message.ae2enhanced.placement.cannot_place");
            return false;
        }

        IGrid grid = OmniToolNetworkLink.getLinkedGrid(toolStack, level);
        if (grid == null) {
            player.displayClientMessage(Component.translatable(PlayerMessages.DeviceNotLinked.getTranslationKey()),
                    false);
            return false;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        if (storage == null) {
            sendMessage(player, "message.ae2enhanced.placement.no_storage");
            return false;
        }

        IActionSource source = IActionSource.ofPlayer(player);

        AEItemKey request = findMatchingStack(storage, target);
        if (request == null) {
            sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
            return false;
        }

        List<BlockPos> positions = ConstructionWandHelper.calculatePositions(level, pos, side,
                config.getPlacementRestriction());
        if (positions.isEmpty()) {
            sendMessage(player, "message.ae2enhanced.placement.cannot_place");
            return false;
        }

        // 模拟提取
        long simulated = storage.extract(request, positions.size(), Actionable.SIMULATE, source);

        boolean useOffhand = false;
        if (simulated < positions.size()) {
            // 如果目标是副手物品，尝试从副手补充
            if (isTargetFromOffhand(player, target)) {
                useOffhand = true;
            } else {
                sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
                return false;
            }
        }

        ItemStack placeStack = request.toStack();
        placeStack.setCount(1);

        List<BlockSnapshot> snapshots = new ArrayList<>();
        List<BlockPos> placedPositions = new ArrayList<>();
        boolean success = true;

        try {
            for (BlockPos placePos : positions) {
                snapshots.add(BlockSnapshot.create(level.dimension(), level, placePos));
                // 每次放置都使用新的 stack 副本，防止 BlockItem 消耗同一份 stack
                ItemStack stackForPos = placeStack.copy();
                if (!tryPlaceBlockAt(player, level, placePos, side, hand, stackForPos, hitX, hitY, hitZ)) {
                    success = false;
                    break;
                }
                placedPositions.add(placePos);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Exception during bulk placement", e);
            success = false;
        }

        if (!success || placedPositions.isEmpty()) {
            rollbackBulk(snapshots);
            sendMessage(player, "message.ae2enhanced.placement.cannot_place");
            return false;
        }

        // 消耗：网络优先，不足则补副手
        int networkConsumed = 0;
        int offhandConsumed = 0;
        if (useOffhand) {
            // 网络能拿多少拿多少
            if (simulated > 0) {
                storage.extract(request, simulated, Actionable.MODULATE, source);
                networkConsumed = (int) simulated;
            }
            offhandConsumed = placedPositions.size() - networkConsumed;
            ItemStack off = player.getOffhandItem();
            if (off.getCount() < offhandConsumed) {
                rollbackBulk(snapshots);
                sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
                return false;
            }
            off.shrink(offhandConsumed);
        } else {
            long extracted = storage.extract(request, placedPositions.size(), Actionable.MODULATE, source);
            if (extracted < placedPositions.size()) {
                rollbackBulk(snapshots);
                sendMessage(player, "message.ae2enhanced.placement.network_missing", target.getHoverName());
                return false;
            }
            networkConsumed = placedPositions.size();
        }

        // 记录撤销
        UndoRecord record = new UndoRecord();
        record.snapshots.addAll(snapshots);
        record.consumed.put(request, (long) networkConsumed);
        PLAYER_UNDO.put(player.getUUID(), record);

        level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.swing(hand, true);
        return true;
    }

    // ==================== 线缆放置 ====================

    /**
     * 放置单格线缆，使用配置颜色。
     */
    private static boolean placeSingleCable(Player player, Level level, BlockPos pos, Direction side,
            InteractionHand hand, ItemStack toolStack, ItemStack baseCable, AEColor color) {
        BlockPos placePos = pos.relative(side);
        List<BlockPos> placed = CablePlacementHelper.placeCable(player, level, placePos, placePos,
                hand, toolStack, baseCable, color);
        return !placed.isEmpty();
    }

    /**
     * 执行两点线缆放置（右键起点 + 左键终点）。
     *
     * @return 是否成功放置任意线缆
     */
    public static boolean placeCableBetween(Player player, Level level,
            BlockPos start, BlockPos end,
            InteractionHand hand, ItemStack toolStack) {
        if (level.isClientSide()) return true;

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, level, start);
        if (target.isEmpty() || !PlacementTargetResolver.isCable(target)) {
            sendMessage(player, "message.ae2enhanced.placement.no_configured_item");
            return false;
        }

        AEColor color = config.getCableColor();
        List<BlockPos> placed = CablePlacementHelper.placeCable(player, level, start, end, hand, toolStack, target,
                color);
        if (placed.isEmpty()) return false;

        // 记录撤销（线缆放置使用 BlockSnapshot 方式记录，因为路径上的方块可能原本是空气）
        UndoRecord record = new UndoRecord();
        for (BlockPos p : placed) {
            record.snapshots.add(BlockSnapshot.create(level.dimension(), level, p));
        }
        PLAYER_UNDO.put(player.getUUID(), record);
        return true;
    }

    // ==================== 撤销 ====================

    public static boolean undoLast(Player player, Level level, ItemStack toolStack) {
        if (level.isClientSide()) return true;

        UndoRecord record = PLAYER_UNDO.remove(player.getUUID());
        if (record == null || record.snapshots.isEmpty()) {
            sendMessage(player, "message.ae2enhanced.placement.nothing_to_undo");
            return false;
        }

        IGrid grid = OmniToolNetworkLink.getLinkedGrid(toolStack, level);
        MEStorage storage = grid != null ? grid.getStorageService().getInventory() : null;

        List<BlockSnapshot> snapshots = new ArrayList<>(record.snapshots);
        java.util.Collections.reverse(snapshots);
        for (BlockSnapshot snapshot : snapshots) {
            try {
                snapshot.restore(true, false);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to restore block at {}", snapshot.getPos(), e);
            }
        }

        if (storage != null) {
            IActionSource source = IActionSource.ofPlayer(player);
            for (Map.Entry<AEItemKey, Long> entry : record.consumed.entrySet()) {
                long inserted = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
                long notInjected = entry.getValue() - inserted;
                if (notInjected > 0) {
                    ItemStack drop = entry.getKey().toStack();
                    drop.setCount((int) Math.min(notInjected, drop.getMaxStackSize()));
                    ItemEntity entity = new ItemEntity(level,
                            player.getX(), player.getY(), player.getZ(), drop);
                    level.addFreshEntity(entity);
                }
            }
        }

        sendMessage(player, "message.ae2enhanced.placement.undone");
        return true;
    }

    // ==================== 内部工具方法 ====================

    private static void sendMessage(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), false);
    }

    private static void sendMessage(Player player, String key, Object arg) {
        player.displayClientMessage(Component.translatable(key, arg), false);
    }

    private static boolean isTargetFromOffhand(Player player, ItemStack target) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty()) return false;
        return ItemStack.isSameItemSameTags(off, target);
    }

    private static boolean placeFromOffhand(Player player, Level level, BlockPos pos, Direction side,
            InteractionHand hand, ItemStack target, int count) {
        ItemStack off = player.getOffhandItem();
        if (off.getCount() < count) return false;

        ItemStack placeStack = off.copy();
        placeStack.setCount(count);

        BlockPos placePos = pos.relative(side);
        boolean placed = false;
        PlacementTarget targetType = PlacementTarget.OTHER;

        try {
            if (placeStack.getItem() instanceof BlockItem) {
                targetType = PlacementTarget.BLOCK;
                placed = tryPlaceBlock(player, level, pos, side, hand, placeStack, 0.5f, 0.5f, 0.5f);
            } else if (placeStack.getItem() instanceof IPartItem) {
                targetType = PlacementTarget.PART;
                placed = tryPlacePart(player, level, pos, side, hand, placeStack);
            } else if (placeStack.getItem() instanceof IFacadeItem) {
                targetType = PlacementTarget.FACADE;
                placed = tryPlaceFacade(player, level, pos, side, placeStack);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Exception during offhand placement", e);
        }

        if (placed) {
            off.shrink(count);
            level.playSound(null, targetType == PlacementTarget.BLOCK ? placePos : pos,
                    SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.swing(hand, true);
            return true;
        }
        return false;
    }

    private static AEColor configColor(ItemStack toolStack) {
        return new PlacementConfig(toolStack).getCableColor();
    }

    private static boolean tryPlaceBlockAt(Player player, Level level, BlockPos placePos, Direction side,
            InteractionHand hand, ItemStack placeStack, float hitX, float hitY, float hitZ) {
        if (!canPlaceBlockAt(level, placePos)) return false;

        ItemStack originalMain = player.getMainHandItem();
        ItemStack originalOff = player.getOffhandItem();

        BlockPos clickedPos = placePos.relative(side.getOpposite());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            UseOnContext context = createUseContext(player, hand, clickedPos, side, hitX, hitY, hitZ);
            InteractionResult result = placeStack.useOn(context);
            return result.consumesAction();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
        }
    }

    private static boolean canPlaceBlockAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    private static void rollbackBulk(List<BlockSnapshot> snapshots) {
        List<BlockSnapshot> reversed = new ArrayList<>(snapshots);
        java.util.Collections.reverse(reversed);
        for (BlockSnapshot snapshot : reversed) {
            try {
                snapshot.restore(true, false);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to rollback block at {}", snapshot.getPos(), e);
            }
        }
    }

    private static boolean tryPlaceBlock(Player player, Level level, BlockPos clickedPos, Direction side,
            InteractionHand hand, ItemStack placeStack, float hitX, float hitY, float hitZ) {
        BlockPos placePos = clickedPos.relative(side);
        if (!canPlaceBlockAt(level, placePos)) return false;

        ItemStack originalMain = player.getMainHandItem();
        ItemStack originalOff = player.getOffhandItem();

        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            UseOnContext context = createUseContext(player, hand, clickedPos, side, hitX, hitY, hitZ);
            InteractionResult result = placeStack.useOn(context);
            return result.consumesAction();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
        }
    }

    private static boolean tryPlacePart(Player player, Level level, BlockPos clickedPos, Direction side,
            InteractionHand hand, ItemStack placeStack) {
        try {
            ItemStack originalMain = player.getMainHandItem();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, placeStack);
                Vec3 hitVec = new Vec3(
                        clickedPos.getX() + 0.5 + side.getStepX() * 0.5,
                        clickedPos.getY() + 0.5 + side.getStepY() * 0.5,
                        clickedPos.getZ() + 0.5 + side.getStepZ() * 0.5);
                BlockHitResult hit = new BlockHitResult(hitVec, side, clickedPos, false);
                InteractionResult result = PartHelper.usePartItem(new UseOnContext(player, hand, hit));
                return result.consumesAction();
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to place part", e);
            return false;
        }
    }

    private static boolean tryPlaceFacade(Player player, Level level, BlockPos clickedPos, Direction side,
            ItemStack placeStack) {
        try {
            IFacadeItem facadeItem = (IFacadeItem) placeStack.getItem();
            IFacadePart facade = facadeItem.createPartFromItemStack(placeStack, side);
            if (facade == null) return false;

            IPartHost host = PartHelper.getPartHost(level, clickedPos);
            if (host == null) return false;

            if (FacadeItem.canPlaceFacade(host, facade)) {
                boolean added = host.getFacadeContainer().addFacade(facade);
                if (added) {
                    host.markForSave();
                    host.markForUpdate();
                    return true;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to place facade", e);
        }
        return false;
    }

    @Nullable
    public static AEItemKey findMatchingStack(MEStorage storage, ItemStack target) {
        // 优先精确匹配
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                ItemStack netStack = itemKey.toStack();
                if (ItemStack.isSameItemSameTags(netStack, target)) {
                    return itemKey;
                }
            }
        }

        // 退而忽略 NBT 匹配 item
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                ItemStack netStack = itemKey.toStack();
                if (ItemStack.isSameItem(netStack, target)) {
                    return itemKey;
                }
            }
        }

        return null;
    }

    private static void rollbackSingle(Level level, BlockPos clickedPos, Direction side,
            BlockState prevState, PlacementTarget targetType) {
        try {
            if (targetType == PlacementTarget.BLOCK) {
                BlockPos placePos = clickedPos.relative(side);
                level.setBlock(placePos, prevState, 3);
            } else if (targetType == PlacementTarget.PART) {
                IPartHost host = PartHelper.getPartHost(level, clickedPos);
                if (host != null) {
                    host.removePartFromSide(side);
                    host.markForSave();
                    host.markForUpdate();
                }
            } else if (targetType == PlacementTarget.FACADE) {
                IPartHost host = PartHelper.getPartHost(level, clickedPos);
                if (host != null) {
                    host.getFacadeContainer().removeFacade(host, side);
                    host.markForSave();
                    host.markForUpdate();
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to rollback placement at {}", clickedPos, e);
        }
    }

    private static UseOnContext createUseContext(Player player, InteractionHand hand, BlockPos clickedPos,
            Direction side, float hitX, float hitY, float hitZ) {
        Vec3 hitVec = new Vec3(clickedPos.getX() + hitX, clickedPos.getY() + hitY, clickedPos.getZ() + hitZ);
        BlockHitResult hit = new BlockHitResult(hitVec, side, clickedPos, false);
        return new UseOnContext(player, hand, hit);
    }

    private enum PlacementTarget {
        BLOCK, PART, FACADE, OTHER
    }

    private static class UndoRecord {
        final List<BlockSnapshot> snapshots = new ArrayList<>();
        final Map<AEItemKey, Long> consumed = new LinkedHashMap<>();
    }
}
