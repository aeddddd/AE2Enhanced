package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.facade.FacadePart;
import appeng.facade.IFacadeItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 放置工具核心辅助类。
 * 负责从 ME 网络（或副手）提取物品并放置方块、AE2 Part、Facade、线缆，以及批量放置和撤销。
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
    public static boolean placeSingle(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                      EnumHand hand, ItemStack toolStack, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, world, pos);
        if (target.isEmpty()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_configured_item"));
            return false;
        }

        return placeSingleWithTarget(player, world, pos, side, hand, toolStack, target, hitX, hitY, hitZ);
    }

    private static boolean placeSingleWithTarget(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                                  EnumHand hand, ItemStack toolStack, ItemStack target,
                                                  float hitX, float hitY, float hitZ) {
        IGrid grid = SecurityTerminalBindingHelper.getLinkedGrid(toolStack, world, player);
        if (grid == null) return false;

        IMEMonitor<IAEItemStack> monitor = SecurityTerminalBindingHelper.getItemMonitor(grid);
        if (monitor == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_storage"));
            return false;
        }

        // 线缆特殊处理：按配置颜色生成目标线缆
        if (PlacementTargetResolver.isCable(target)) {
            return placeSingleCable(player, world, pos, side, hand, toolStack, target, configColor(toolStack));
        }

        IAEItemStack request = findMatchingStack(monitor, target);
        if (request == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    target.getDisplayName()));
            return false;
        }

        // 模拟提取
        IAEItemStack toExtract = request.copy();
        toExtract.setStackSize(1);
        IAEItemStack simulated = monitor.extractItems(toExtract, Actionable.SIMULATE, SecurityTerminalBindingHelper.createPlayerSource(player));
        if (simulated == null || simulated.getStackSize() < 1) {
            // 如果目标是副手物品，尝试直接消耗副手
            if (isTargetFromOffhand(player, target)) {
                return placeFromOffhand(player, world, pos, side, hand, target, 1);
            }
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    target.getDisplayName()));
            return false;
        }

        ItemStack placeStack = request.getDefinition().copy();
        placeStack.setCount(1);

        BlockPos blockPlacePos = pos.offset(side);
        BlockSnapshot preSnapshot = BlockSnapshot.getBlockSnapshot(world, blockPlacePos);

        boolean placed = false;
        PlacementTarget targetType = PlacementTarget.OTHER;

        try {
            if (placeStack.getItem() instanceof ItemBlock) {
                targetType = PlacementTarget.BLOCK;
                placed = tryPlaceBlock(player, world, pos, side, hand, placeStack, hitX, hitY, hitZ);
            } else if (placeStack.getItem() instanceof IPartItem) {
                targetType = PlacementTarget.PART;
                placed = tryPlacePart(player, world, pos, side, hand, placeStack);
            } else if (placeStack.getItem() instanceof IFacadeItem) {
                targetType = PlacementTarget.FACADE;
                placed = tryPlaceFacade(player, world, pos, side, placeStack);
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Exception during placement", e);
        }

        if (placed) {
            IAEItemStack extracted = monitor.extractItems(toExtract, Actionable.MODULATE, SecurityTerminalBindingHelper.createPlayerSource(player));
            if (extracted == null || extracted.getStackSize() < 1) {
                rollbackSingle(world, pos, side, preSnapshot.getCurrentBlock(), targetType);
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                        target.getDisplayName()));
                return false;
            }

            BlockPos soundPos = targetType == PlacementTarget.BLOCK ? blockPlacePos : pos;
            world.playSound(null, soundPos, net.minecraft.init.SoundEvents.BLOCK_STONE_PLACE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.swingArm(hand);

            UndoRecord record = new UndoRecord();
            if (targetType == PlacementTarget.BLOCK) {
                record.snapshots.add(preSnapshot);
            }
            record.consumed.put(request.copy().setStackSize(1), 1L);
            PLAYER_UNDO.put(player.getUniqueID(), record);
            return true;
        } else {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.cannot_place"));
            return false;
        }
    }

    // ==================== 批量放置（建筑手杖模式） ====================

    /**
     * 批量放置。使用建筑手杖式扩展，最大 512 个方块。
     */
    public static boolean placeBulk(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                    EnumHand hand, ItemStack toolStack, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = PlacementTargetResolver.resolveBulk(player, world, pos);
        if (target.isEmpty()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_configured_item"));
            return false;
        }

        if (!(target.getItem() instanceof ItemBlock)) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.cannot_place"));
            return false;
        }

        IGrid grid = SecurityTerminalBindingHelper.getLinkedGrid(toolStack, world, player);
        if (grid == null) return false;

        IMEMonitor<IAEItemStack> monitor = SecurityTerminalBindingHelper.getItemMonitor(grid);
        if (monitor == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_storage"));
            return false;
        }

        IAEItemStack request = findMatchingStack(monitor, target);
        if (request == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    target.getDisplayName()));
            return false;
        }

        List<BlockPos> positions = ConstructionWandHelper.calculatePositions(world, pos, side, config.getPlacementRestriction());
        if (positions.isEmpty()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.cannot_place"));
            return false;
        }

        // 模拟提取
        IAEItemStack toExtract = request.copy();
        toExtract.setStackSize(positions.size());
        IAEItemStack simulated = monitor.extractItems(toExtract, Actionable.SIMULATE,
                SecurityTerminalBindingHelper.createPlayerSource(player));

        boolean useOffhand = false;
        if (simulated == null || simulated.getStackSize() < positions.size()) {
            // 如果目标是副手物品，尝试从副手补充
            if (isTargetFromOffhand(player, target)) {
                useOffhand = true;
            } else {
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                        target.getDisplayName()));
                return false;
            }
        }

        ItemStack placeStack = request.getDefinition().copy();
        placeStack.setCount(1);

        List<BlockSnapshot> snapshots = new ArrayList<>();
        List<BlockPos> placedPositions = new ArrayList<>();
        boolean success = true;

        try {
            for (BlockPos placePos : positions) {
                snapshots.add(BlockSnapshot.getBlockSnapshot(world, placePos));
                // 每次放置都使用新的 stack 副本，防止 ItemBlock 消耗同一份 stack
                ItemStack stackForPos = placeStack.copy();
                if (!tryPlaceBlockAt(player, world, placePos, side, hand, stackForPos, hitX, hitY, hitZ)) {
                    success = false;
                    break;
                }
                placedPositions.add(placePos);
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Exception during bulk placement", e);
            success = false;
        }

        if (!success || placedPositions.isEmpty()) {
            rollbackBulk(world, snapshots);
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.cannot_place"));
            return false;
        }

        // 消耗：网络优先，不足则补副手
        int networkConsumed = 0;
        int offhandConsumed = 0;
        if (useOffhand) {
            // 网络能拿多少拿多少
            if (simulated != null && simulated.getStackSize() > 0) {
                IAEItemStack netExtract = request.copy();
                netExtract.setStackSize(simulated.getStackSize());
                monitor.extractItems(netExtract, Actionable.MODULATE, SecurityTerminalBindingHelper.createPlayerSource(player));
                networkConsumed = (int) simulated.getStackSize();
            }
            offhandConsumed = placedPositions.size() - networkConsumed;
            ItemStack off = player.getHeldItemOffhand();
            if (off.getCount() < offhandConsumed) {
                rollbackBulk(world, snapshots);
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                        target.getDisplayName()));
                return false;
            }
            off.shrink(offhandConsumed);
        } else {
            IAEItemStack finalExtract = request.copy();
            finalExtract.setStackSize(placedPositions.size());
            IAEItemStack extracted = monitor.extractItems(finalExtract, Actionable.MODULATE,
                    SecurityTerminalBindingHelper.createPlayerSource(player));
            if (extracted == null || extracted.getStackSize() < placedPositions.size()) {
                rollbackBulk(world, snapshots);
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                        target.getDisplayName()));
                return false;
            }
            networkConsumed = placedPositions.size();
        }

        // 记录撤销
        UndoRecord record = new UndoRecord();
        record.snapshots.addAll(snapshots);
        record.consumed.put(request.copy().setStackSize(1), (long) networkConsumed);
        PLAYER_UNDO.put(player.getUniqueID(), record);

        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_STONE_PLACE,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        player.swingArm(hand);
        return true;
    }

    // ==================== 线缆放置 ====================

    /**
     * 放置单格线缆，使用配置颜色。
     */
    private static boolean placeSingleCable(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                            EnumHand hand, ItemStack toolStack, ItemStack baseCable, AEColor color) {
        BlockPos placePos = pos.offset(side);
        List<BlockPos> placed = CablePlacementHelper.placeCable(player, world, placePos, placePos,
                hand, toolStack, baseCable, color);
        if (placed.isEmpty()) return false;
        return true;
    }

    /**
     * 执行两点线缆放置（右键起点 + 左键终点）。
     *
     * @return 是否成功放置任意线缆
     */
    public static boolean placeCableBetween(EntityPlayer player, World world,
                                             BlockPos start, BlockPos end,
                                             EnumHand hand, ItemStack toolStack) {
        if (world.isRemote) return true;

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, world, start);
        if (target.isEmpty() || !PlacementTargetResolver.isCable(target)) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_configured_item"));
            return false;
        }

        AEColor color = config.getCableColor();
        List<BlockPos> placed = CablePlacementHelper.placeCable(player, world, start, end, hand, toolStack, target, color);
        if (placed.isEmpty()) return false;

        // 记录撤销（线缆放置使用 BlockSnapshot 方式记录，因为路径上的方块可能原本是空气）
        UndoRecord record = new UndoRecord();
        for (BlockPos p : placed) {
            record.snapshots.add(BlockSnapshot.getBlockSnapshot(world, p));
        }
        PLAYER_UNDO.put(player.getUniqueID(), record);
        return true;
    }

    // ==================== 撤销 ====================

    public static boolean undoLast(EntityPlayer player, World world, ItemStack toolStack) {
        if (world.isRemote) return true;

        UndoRecord record = PLAYER_UNDO.remove(player.getUniqueID());
        if (record == null || record.snapshots.isEmpty()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.nothing_to_undo"));
            return false;
        }

        IGrid grid = SecurityTerminalBindingHelper.getLinkedGrid(toolStack, world, player);
        IMEMonitor<IAEItemStack> monitor = grid != null ? SecurityTerminalBindingHelper.getItemMonitor(grid) : null;

        List<BlockSnapshot> snapshots = new ArrayList<>(record.snapshots);
        java.util.Collections.reverse(snapshots);
        for (BlockSnapshot snapshot : snapshots) {
            try {
                snapshot.restore(true, false);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to restore block at {}", snapshot.getPos(), e);
            }
        }

        if (monitor != null) {
            for (Map.Entry<IAEItemStack, Long> entry : record.consumed.entrySet()) {
                IAEItemStack refund = entry.getKey().copy();
                refund.setStackSize(entry.getValue());
                IAEItemStack notInjected = monitor.injectItems(refund, Actionable.MODULATE,
                        SecurityTerminalBindingHelper.createPlayerSource(player));
                if (notInjected != null && notInjected.getStackSize() > 0) {
                    ItemStack drop = notInjected.getDefinition().copy();
                    drop.setCount((int) Math.min(notInjected.getStackSize(), drop.getMaxStackSize()));
                    net.minecraft.entity.item.EntityItem entity = new net.minecraft.entity.item.EntityItem(world,
                            player.posX, player.posY, player.posZ, drop);
                    world.spawnEntity(entity);
                }
            }
        }

        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.undone"));
        return true;
    }

    // ==================== 内部工具方法 ====================

    private static boolean isTargetFromOffhand(EntityPlayer player, ItemStack target) {
        ItemStack off = player.getHeldItemOffhand();
        if (off.isEmpty()) return false;
        return ItemStack.areItemsEqual(off, target) && ItemStack.areItemStackTagsEqual(off, target);
    }

    private static boolean placeFromOffhand(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                            EnumHand hand, ItemStack target, int count) {
        ItemStack off = player.getHeldItemOffhand();
        if (off.getCount() < count) return false;

        ItemStack placeStack = off.copy();
        placeStack.setCount(count);

        BlockPos placePos = pos.offset(side);
        IBlockState prevBlockState = world.getBlockState(placePos);
        boolean placed = false;
        PlacementTarget targetType = PlacementTarget.OTHER;

        try {
            if (placeStack.getItem() instanceof ItemBlock) {
                targetType = PlacementTarget.BLOCK;
                placed = tryPlaceBlock(player, world, pos, side, hand, placeStack, 0.5f, 0.5f, 0.5f);
            } else if (placeStack.getItem() instanceof IPartItem) {
                targetType = PlacementTarget.PART;
                placed = tryPlacePart(player, world, pos, side, hand, placeStack);
            } else if (placeStack.getItem() instanceof IFacadeItem) {
                targetType = PlacementTarget.FACADE;
                placed = tryPlaceFacade(player, world, pos, side, placeStack);
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Exception during offhand placement", e);
        }

        if (placed) {
            off.shrink(count);
            world.playSound(null, targetType == PlacementTarget.BLOCK ? placePos : pos,
                    net.minecraft.init.SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.swingArm(hand);
            return true;
        }
        return false;
    }

    private static AEColor configColor(ItemStack toolStack) {
        return new PlacementConfig(toolStack).getCableColor();
    }

    private static boolean tryPlaceBlockAt(EntityPlayer player, World world, BlockPos placePos, EnumFacing side,
                                           EnumHand hand, ItemStack placeStack, float hitX, float hitY, float hitZ) {
        if (!canPlaceBlockAt(world, placePos)) return false;

        ItemStack originalMain = player.getHeldItemMainhand();
        ItemStack originalOff = player.getHeldItemOffhand();

        BlockPos clickedPos = placePos.offset(side.getOpposite());
        try {
            player.setHeldItem(EnumHand.MAIN_HAND, placeStack);
            player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            EnumActionResult result = placeStack.onItemUse(player, world, clickedPos, hand, side, hitX, hitY, hitZ);
            return result == EnumActionResult.SUCCESS;
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, originalMain);
            player.setHeldItem(EnumHand.OFF_HAND, originalOff);
        }
    }

    private static boolean canPlaceBlockAt(World world, BlockPos pos) {
        return world.isAirBlock(pos) || world.getBlockState(pos).getBlock().isReplaceable(world, pos);
    }

    private static void rollbackBulk(World world, List<BlockSnapshot> snapshots) {
        List<BlockSnapshot> reversed = new ArrayList<>(snapshots);
        java.util.Collections.reverse(reversed);
        for (BlockSnapshot snapshot : reversed) {
            try {
                snapshot.restore(true, false);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to rollback block at {}", snapshot.getPos(), e);
            }
        }
    }

    private static boolean tryPlaceBlock(EntityPlayer player, World world, BlockPos clickedPos, EnumFacing side,
                                         EnumHand hand, ItemStack placeStack, float hitX, float hitY, float hitZ) {
        BlockPos placePos = clickedPos.offset(side);
        if (!canPlaceBlockAt(world, placePos)) return false;

        ItemStack originalMain = player.getHeldItemMainhand();
        ItemStack originalOff = player.getHeldItemOffhand();

        try {
            player.setHeldItem(EnumHand.MAIN_HAND, placeStack);
            player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            EnumActionResult result = placeStack.onItemUse(player, world, clickedPos, hand, side, hitX, hitY, hitZ);
            return result == EnumActionResult.SUCCESS;
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, originalMain);
            player.setHeldItem(EnumHand.OFF_HAND, originalOff);
        }
    }

    private static boolean tryPlacePart(EntityPlayer player, World world, BlockPos clickedPos, EnumFacing side,
                                        EnumHand hand, ItemStack placeStack) {
        try {
            EnumActionResult result = AEApi.instance().partHelper().placeBus(placeStack, clickedPos, side, player, hand, world);
            return result == EnumActionResult.SUCCESS;
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to place part", e);
            return false;
        }
    }

    private static boolean tryPlaceFacade(EntityPlayer player, World world, BlockPos clickedPos, EnumFacing side,
                                          ItemStack placeStack) {
        try {
            IFacadeItem facadeItem = (IFacadeItem) placeStack.getItem();
            AEPartLocation loc = AEPartLocation.fromFacing(side);
            FacadePart facade = facadeItem.createPartFromItemStack(placeStack, loc);
            if (facade == null) return false;

            IPartHost host = AEApi.instance().partHelper().getPartHost(world, clickedPos);
            if (host == null) return false;

            if (host.getPart(AEPartLocation.INTERNAL) != null && host.getFacadeContainer().canAddFacade(facade)) {
                boolean added = host.getFacadeContainer().addFacade(facade);
                if (added) {
                    host.markForSave();
                    host.markForUpdate();
                    return true;
                }
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to place facade", e);
        }
        return false;
    }

    @Nullable
    public static IAEItemStack findMatchingStack(IMEMonitor<IAEItemStack> monitor, ItemStack target) {
        IAEItemStack request = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                .createStack(target);
        if (request == null) return null;
        request.setStackSize(1);

        // 优先精确匹配
        for (IAEItemStack stack : monitor.getStorageList()) {
            if (stack.isSameType(target)) {
                return stack.copy();
            }
        }

        // 退而忽略 NBT 匹配 item + meta
        for (IAEItemStack stack : monitor.getStorageList()) {
            ItemStack netStack = stack.getDefinition().copy();
            netStack.setCount(1);
            if (netStack.getItem() == target.getItem()
                    && netStack.getMetadata() == target.getMetadata()) {
                return stack.copy();
            }
        }

        return null;
    }

    private static void rollbackSingle(World world, BlockPos clickedPos, EnumFacing side,
                                       IBlockState prevBlockState, PlacementTarget targetType) {
        try {
            if (targetType == PlacementTarget.BLOCK) {
                BlockPos placePos = clickedPos.offset(side);
                world.setBlockState(placePos, prevBlockState);
            } else if (targetType == PlacementTarget.PART) {
                IPartHost host = AEApi.instance().partHelper().getPartHost(world, clickedPos);
                if (host != null) {
                    host.removePart(AEPartLocation.fromFacing(side), true);
                    host.markForSave();
                    host.markForUpdate();
                }
            } else if (targetType == PlacementTarget.FACADE) {
                IPartHost host = AEApi.instance().partHelper().getPartHost(world, clickedPos);
                if (host != null) {
                    host.getFacadeContainer().removeFacade(host, AEPartLocation.fromFacing(side));
                    host.markForSave();
                    host.markForUpdate();
                }
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to rollback placement at {}", clickedPos, e);
        }
    }

    private enum PlacementTarget {
        BLOCK, PART, FACADE, OTHER
    }

    private static class UndoRecord {
        final List<BlockSnapshot> snapshots = new ArrayList<>();
        final Map<IAEItemStack, Long> consumed = new LinkedHashMap<>();
    }
}
