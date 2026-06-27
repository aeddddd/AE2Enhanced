package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalMeInterface;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class HyperdimensionalStructure {

    // 相对于控制器 (0,0,0) 的坐标
    public static final Set<BlockPos> CONTROLLER_SET;
    public static final Set<BlockPos> ME_INTERFACE_SET;
    public static final Set<BlockPos> CASING_SET;
    public static final Set<BlockPos> CORE_SET; // singularity core
    public static final Set<BlockPos> ALL_SET;

    static {
        Set<BlockPos> controller = new HashSet<>();
        controller.add(new BlockPos(0, 0, 0));
        CONTROLLER_SET = Collections.unmodifiableSet(controller);

        Set<BlockPos> meInterface = new HashSet<>();
        meInterface.add(new BlockPos(0, 0, 4));
        ME_INTERFACE_SET = Collections.unmodifiableSet(meInterface);

        Set<BlockPos> core = new HashSet<>();
        core.add(new BlockPos(0, 0, 3));
        core.add(new BlockPos(-1, 0, 2));
        core.add(new BlockPos(1, 0, 2));
        core.add(new BlockPos(0, 0, 2));
        core.add(new BlockPos(0, 0, 1));
        CORE_SET = Collections.unmodifiableSet(core);

        Set<BlockPos> casing = new HashSet<>();
        casing.add(new BlockPos(-1, 0, 3));
        casing.add(new BlockPos(-2, 0, 3));
        casing.add(new BlockPos(1, 0, 4));
        casing.add(new BlockPos(1, 0, 3));
        casing.add(new BlockPos(-2, 0, 1));
        casing.add(new BlockPos(-1, 0, 1));
        casing.add(new BlockPos(-2, 0, 2));
        casing.add(new BlockPos(-1, 0, 0));
        casing.add(new BlockPos(1, 0, 1));
        casing.add(new BlockPos(1, 0, 0));
        casing.add(new BlockPos(-1, 0, 4));
        casing.add(new BlockPos(2, 0, 2));
        casing.add(new BlockPos(2, 0, 1));
        casing.add(new BlockPos(2, 0, 3));
        CASING_SET = Collections.unmodifiableSet(casing);

        Set<BlockPos> all = new HashSet<>();
        all.addAll(CONTROLLER_SET);
        all.addAll(ME_INTERFACE_SET);
        all.addAll(CORE_SET);
        all.addAll(CASING_SET);
        ALL_SET = Collections.unmodifiableSet(all);
    }

    public static BlockPos rotate(BlockPos rel, EnumFacing facing) {
        if (facing == EnumFacing.NORTH) return rel;
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        switch (facing) {
            case SOUTH: return new BlockPos(-x, y, -z);
            case EAST:  return new BlockPos(-z, y, x);
            case WEST:  return new BlockPos(z, y, -x);
            default:    return rel;
        }
    }

    public static boolean validate(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        if (!checkBlock(world, controllerPos, CONTROLLER_SET, BlockRegistry.HYPERDIMENSIONAL_CONTROLLER, facing)) {
            return false;
        }
        if (!checkBlock(world, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, facing)) {
            return false;
        }
        if (!checkBlock(world, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, facing)) {
            return false;
        }
        if (!checkBlock(world, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, facing)) {
            return false;
        }
        return true;
    }

    private static boolean checkBlock(World world, BlockPos controllerPos, Set<BlockPos> relativeSet, Block expected, EnumFacing facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                continue; // chunk 未加载,保持当前状态,不判定为缺失
            }
            if (world.getBlockState(actual).getBlock() != expected) {
                return false;
            }
        }
        return true;
    }

    public static Map<Block, Integer> getMissingMap(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        Map<Block, Integer> missing = new LinkedHashMap<>();
        countMissing(world, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, missing, facing);
        countMissing(world, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, missing, facing);
        countMissing(world, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, missing, facing);
        return missing;
    }

    private static void countMissing(World world, BlockPos controllerPos, Set<BlockPos> relativeSet, Block expected, Map<Block, Integer> missing, EnumFacing facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                continue;
            }
            if (world.getBlockState(actual).getBlock() != expected) {
                missing.put(expected, missing.getOrDefault(expected, 0) + 1);
            }
        }
    }

    public static void assemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileHyperdimensionalController tile = getControllerTile(world, controllerPos);
        if (tile != null) {
            tile.assemble();
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        updateMeInterfaceState(world, controllerPos, true, facing);
    }

    public static void disassemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileHyperdimensionalController tile = getControllerTile(world, controllerPos);
        if (tile != null) {
            tile.disassemble();
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        updateMeInterfaceState(world, controllerPos, false, facing);
    }

    private static void updateMeInterfaceState(World world, BlockPos controllerPos, boolean formed, EnumFacing facing) {
        IBlockState state = BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE.getDefaultState()
            .withProperty(com.github.aeddddd.ae2enhanced.block.BlockHyperdimensionalMeInterface.FORMED, formed);
        for (BlockPos rel : ME_INTERFACE_SET) {
            BlockPos pos = controllerPos.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() == BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE) {
                world.setBlockState(pos, state);
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
                if (te instanceof TileHyperdimensionalMeInterface) {
                    ((TileHyperdimensionalMeInterface) te).setControllerPos(formed ? controllerPos : null);
                }
            }
        }
    }

    private static TileHyperdimensionalController getControllerTile(World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        return te instanceof TileHyperdimensionalController ? (TileHyperdimensionalController) te : null;
    }

    private static EnumFacing getControllerFacing(World world, BlockPos controllerPos) {
        IBlockState state = world.getBlockState(controllerPos);
        if (state.getBlock() instanceof BlockHyperdimensionalController) {
            return state.getValue(BlockHyperdimensionalController.FACING);
        }
        return EnumFacing.NORTH;
    }

    /**
     * 创造模式：一键生成所有缺失方块
     */
    public static void placeMissingBlocks(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return;
        EnumFacing facing = getControllerFacing(world, controllerPos);

        placeBlocks(world, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, facing);
        placeBlocks(world, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, facing);
        placeBlocks(world, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, facing);

        // 立即触发组装(跳过 20 tick 等待)
        assemble(world, controllerPos);
    }

    private static void placeBlocks(World world, BlockPos controllerPos, Set<BlockPos> set, Block block, EnumFacing facing) {
        for (BlockPos rel : set) {
            BlockPos pos = controllerPos.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() != block) {
                world.setBlockState(pos, block.getDefaultState());
            }
        }
    }

    /**
     * 生存模式：检查背包材料,足够则扣除并放置
     * @return 是否成功
     */
    public static boolean tryConsumeAndPlace(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return false;
        EnumFacing facing = getControllerFacing(world, controllerPos);

        Map<Block, Integer> missing = new LinkedHashMap<>();
        countMissing(world, controllerPos, ME_INTERFACE_SET, BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE, missing, facing);
        countMissing(world, controllerPos, CORE_SET, BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE, missing, facing);
        countMissing(world, controllerPos, CASING_SET, BlockRegistry.HYPERDIMENSIONAL_CASING, missing, facing);

        if (missing.isEmpty()) {
            assemble(world, controllerPos);
            return true;
        }

        // 检查背包是否有足够材料
        net.minecraft.entity.player.InventoryPlayer inv = player.inventory;
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (net.minecraft.item.ItemStack stack : inv.mainInventory) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(block)) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }

        for (int count : needed.values()) {
            if (count > 0) return false; // 材料不足
        }

        // 扣除材料
        for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
            Block block = entry.getKey();
            int remaining = entry.getValue();
            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
            for (int i = 0; i < inv.mainInventory.size() && remaining > 0; i++) {
                net.minecraft.item.ItemStack stack = inv.mainInventory.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.decrStackSize(i, take).getCount();
                    remaining -= removed;
                }
            }
        }

        // 放置方块
        placeMissingBlocks(world, controllerPos, player);
        return true;
    }
}
