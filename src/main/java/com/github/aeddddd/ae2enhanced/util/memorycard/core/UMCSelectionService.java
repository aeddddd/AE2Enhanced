package com.github.aeddddd.ae2enhanced.util.memorycard.core;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.recycler.TargetManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * UMC 选取与绑定逻辑服务.
 */
public class UMCSelectionService {

    private static final int MAX_CHAIN_SELECT = 64;
    private static final int MAX_AREA_SELECT = 128;

    public static void handleSelect(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        int dim = world.provider.getDimension();

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        for (int i = 0; i < selections.size(); i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(i);
            if (entry.dim == dim && entry.pos.equals(pos)) {
                ItemUniversalMemoryCard.removeSelection(stack, i);
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.deselect"));
                return;
            }
        }

        // Part 目标在任何选取模式下都按单个处理
        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart part = host.getPart(AEPartLocation.fromFacing(face));
            if (part != null) {
                String tileId = part.getClass().getName();
                int side = AEPartLocation.fromFacing(face).ordinal();
                ItemUniversalMemoryCard.addSelection(stack, new ItemUniversalMemoryCard.SelectionEntry(
                        pos, dim, tileId, side, makeIcon(world, pos, part)));
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_part"));
                return;
            }
        }

        ItemUniversalMemoryCard.SelectMode mode = ItemUniversalMemoryCard.getSelectMode(stack);
        switch (mode) {
            case SINGLE:
                handleSelectSingle(player, stack, world, pos, te, dim);
                break;
            case CHAIN:
                handleSelectChain(player, stack, world, pos, te, dim);
                break;
            case AREA:
                handleSelectArea(player, stack, world, pos, te, dim);
                break;
        }
    }

    private static void handleSelectSingle(EntityPlayer player, ItemStack stack, World world, BlockPos pos,
                                           net.minecraft.tileentity.TileEntity te, int dim) {
        if (te != null) {
            ItemUniversalMemoryCard.addSelection(stack,
                    new ItemUniversalMemoryCard.SelectionEntry(pos, dim, te.getClass().getName(), -1,
                            makeIcon(world, pos, null)));
        } else {
            String blockId = world.getBlockState(pos).getBlock().getRegistryName().toString();
            ItemUniversalMemoryCard.addSelection(stack,
                    new ItemUniversalMemoryCard.SelectionEntry(pos, dim, blockId, -1,
                            makeIcon(world, pos, null)));
        }
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_single"));
    }

    private static void handleSelectChain(EntityPlayer player, ItemStack stack, World world, BlockPos pos,
                                          net.minecraft.tileentity.TileEntity te, int dim) {
        if (te == null) {
            // 无 TileEntity 的方块退化为单选
            handleSelectSingle(player, stack, world, pos, null, dim);
            return;
        }
        // 收集本维度已选取坐标,连锁扩展时跳过,避免重复选取
        Set<BlockPos> alreadySelected = new HashSet<>();
        for (ItemUniversalMemoryCard.SelectionEntry entry : ItemUniversalMemoryCard.getSelections(stack)) {
            if (entry.dim == dim) alreadySelected.add(entry.pos);
        }

        String tileId = te.getClass().getName();
        List<BlockPos> connected = findConnectedBlocks(world, pos, te.getClass(), MAX_CHAIN_SELECT, alreadySelected);
        if (connected.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_no_new"));
            return;
        }
        for (BlockPos p : connected) {
            ItemUniversalMemoryCard.addSelection(stack, new ItemUniversalMemoryCard.SelectionEntry(
                    p, dim, tileId, -1, makeIcon(world, p, null)));
        }
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_tile",
                ItemUniversalMemoryCard.getSelectionCount(stack), connected.size()));
    }

    private static void handleSelectArea(EntityPlayer player, ItemStack stack, World world, BlockPos pos,
                                         net.minecraft.tileentity.TileEntity te, int dim) {
        net.minecraft.nbt.NBTTagCompound corner = ItemUniversalMemoryCard.getAreaCorner(stack);
        if (corner == null || corner.getInteger("dim") != dim) {
            // 第一次点击：记录角点
            ItemUniversalMemoryCard.setAreaCorner(stack, pos, dim);
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.area_corner_set"));
            return;
        }

        // 第二次点击：框选区域内所有可适配的 TileEntity
        BlockPos from = BlockPos.fromLong(corner.getLong("pos"));
        ItemUniversalMemoryCard.clearAreaCorner(stack);

        Set<BlockPos> alreadySelected = new HashSet<>();
        for (ItemUniversalMemoryCard.SelectionEntry entry : ItemUniversalMemoryCard.getSelections(stack)) {
            if (entry.dim == dim) alreadySelected.add(entry.pos);
        }

        int added = 0;
        outer:
        for (BlockPos p : BlockPos.getAllInBox(from, pos)) {
            BlockPos immutable = p.toImmutable();
            if (alreadySelected.contains(immutable)) continue;
            if (!world.isBlockLoaded(immutable)) continue;
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(immutable);
            if (tile == null) continue;
            if (MemoryCardHandlerRegistry.findHandler(tile) == null) continue;
            ItemUniversalMemoryCard.addSelection(stack,
                    new ItemUniversalMemoryCard.SelectionEntry(immutable, dim, tile.getClass().getName(), -1,
                            makeIcon(world, immutable, null)));
            if (++added >= MAX_AREA_SELECT) break outer;
        }

        if (added == 0) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_no_new"));
        } else {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.area_selected",
                    added, ItemUniversalMemoryCard.getSelectionCount(stack)));
        }
    }

    public static void handleBindSource(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileCentralMEInterface)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_source"));
            return;
        }

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        if (selections.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.no_selections"));
            return;
        }

        TileCentralMEInterface source = (TileCentralMEInterface) te;
        int bound = 0;
        for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
            if (entry.dim != world.provider.getDimension()) continue;
            if (!world.isBlockLoaded(entry.pos)) continue;
            net.minecraft.tileentity.TileEntity targetTe = world.getTileEntity(entry.pos);
            if (targetTe == null) continue;

            String blockId = world.getBlockState(entry.pos).getBlock().getRegistryName().toString();
            source.addBinding(new com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding(entry.pos, entry.dim, blockId));
            bound++;
        }

        ItemUniversalMemoryCard.clearSelections(stack);
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_success", bound));
    }

    public static void handleClearBindings(EntityPlayer player, BlockPos pos) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileCentralMEInterface)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_source"));
            return;
        }
        TileCentralMEInterface source = (TileCentralMEInterface) te;
        int count = source.getInterfaceDuality().getBindings().size();
        source.clearBindings();
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.clear_bindings", count));
    }

    public static void handleBindRecycler(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMENetworkRecycler)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_recycler"));
            return;
        }

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        if (selections.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.no_selections"));
            return;
        }

        TileMENetworkRecycler recycler = (TileMENetworkRecycler) te;
        int bound = 0;
        int skipped = 0;
        for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
            if (recycler.getTargetManager().getTargetCount() >= com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.recycler.maxTargets) {
                skipped++;
                continue;
            }
            TargetManager.TargetRef target = new TargetManager.TargetRef(entry.dim, entry.pos,
                    entry.side >= 0 ? EnumFacing.values()[entry.side] : EnumFacing.UP);
            if (recycler.tryBindTarget(target)) {
                bound++;
            } else {
                skipped++;
            }
        }
        recycler.markDirty();
        ItemUniversalMemoryCard.clearSelections(stack);
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_recycler_success", bound, skipped));
    }

    public static void handleClearRecyclerBindings(EntityPlayer player, BlockPos pos) {
        World world = player.world;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileMENetworkRecycler)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bind_invalid_recycler"));
            return;
        }
        TileMENetworkRecycler recycler = (TileMENetworkRecycler) te;
        int count = recycler.getTargetManager().getTargetCount();
        recycler.clearTargets();
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.clear_recycler_bindings", count));
    }

    /**
     * 生成选取条目的图标物品堆(GUI 列表显示用).
     * Part 取其 NETWORK 物品形态;方块优先取 pickBlock 形态——部分 mod(如热力机器)
     * 的机型存于物品 NBT,光有 blockMeta 区分不了机型变体,失败时回退
     * ItemBlock + 真实 meta;再失败或无物品形态时返回 EMPTY,GUI 端自动降级为纯文字.
     */
    private static ItemStack makeIcon(World world, BlockPos pos, @javax.annotation.Nullable IPart part) {
        try {
            if (part != null) {
                ItemStack s = part.getItemStack(appeng.api.parts.PartItemStack.NETWORK);
                if (s == null || s.isEmpty()) {
                    s = part.getItemStack(appeng.api.parts.PartItemStack.PICK);
                }
                return s == null ? ItemStack.EMPTY : s;
            }
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            net.minecraft.block.Block block = state.getBlock();
            try {
                ItemStack pick = block.getPickBlock(state,
                        new net.minecraft.util.math.RayTraceResult(net.minecraft.util.math.RayTraceResult.Type.BLOCK,
                                new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                net.minecraft.util.EnumFacing.UP, pos),
                        world, pos, null);
                if (pick != null && !pick.isEmpty()) return pick;
            } catch (Exception ignored) {
                // 回退到 ItemBlock + meta
            }
            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
            if (item == null) return ItemStack.EMPTY;
            return new ItemStack(item, 1, block.getMetaFromState(state));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 洪泛搜索与起点同类的连通 TileEntity.
     * 已选取的坐标会被跳过(不加入结果),但仍参与连通性扩展,
     * 保证已选取片段不会阻断未选取片段的发现.
     */
    private static List<BlockPos> findConnectedBlocks(World world, BlockPos start, Class<?> tileClass,
                                                      int maxCount, Set<BlockPos> exclude) {
        List<BlockPos> result = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < maxCount) {
            BlockPos pos = queue.poll();
            if (!world.isBlockLoaded(pos)) continue;
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
            if (te != null && te.getClass() == tileClass) {
                if (!exclude.contains(pos)) {
                    result.add(pos);
                }

                for (EnumFacing facing : EnumFacing.values()) {
                    BlockPos neighbor = pos.offset(facing);
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
