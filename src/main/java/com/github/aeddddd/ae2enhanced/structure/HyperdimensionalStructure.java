package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

/**
 * 超维度仓储中枢结构验证与一键装配逻辑。
 * <p>坐标系以控制器为原点，默认朝向北方（Z 轴正方向伸出），根据控制器朝向旋转。</p>
 */
public final class HyperdimensionalStructure {

    public static final Set<BlockPos> CONTROLLER_SET;
    public static final Set<BlockPos> INTERFACE_SET;
    public static final Set<BlockPos> CASING_SET;
    public static final Set<BlockPos> CORE_SET;
    public static final Set<BlockPos> ALL_SET;

    static {
        Set<BlockPos> controller = new HashSet<>();
        controller.add(new BlockPos(0, 0, 0));
        CONTROLLER_SET = Collections.unmodifiableSet(controller);

        Set<BlockPos> meInterface = new HashSet<>();
        meInterface.add(new BlockPos(0, 0, 4));
        INTERFACE_SET = Collections.unmodifiableSet(meInterface);

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
        all.addAll(INTERFACE_SET);
        all.addAll(CORE_SET);
        all.addAll(CASING_SET);
        ALL_SET = Collections.unmodifiableSet(all);
    }

    private HyperdimensionalStructure() {
    }

    public static BlockPos rotate(BlockPos rel, Direction facing) {
        if (facing == Direction.NORTH) {
            return rel;
        }
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        return switch (facing) {
            case SOUTH -> new BlockPos(-x, y, -z);
            case EAST -> new BlockPos(-z, y, x);
            case WEST -> new BlockPos(z, y, -x);
            default -> rel;
        };
    }

    public static boolean validate(Level level, BlockPos controllerPos) {
        Direction facing = getControllerFacing(level, controllerPos);
        return checkBlock(level, controllerPos, CONTROLLER_SET, ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get(), facing)
                && checkBlock(level, controllerPos, INTERFACE_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), facing)
                && checkBlock(level, controllerPos, CORE_SET, ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get(), facing)
                && checkBlock(level, controllerPos, CASING_SET, ModBlocks.HYPERDIMENSIONAL_CASING.get(), facing);
    }

    private static boolean checkBlock(Level level, BlockPos controllerPos, Set<BlockPos> relativeSet,
            Block expected, Direction facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!level.isLoaded(actual)) {
                continue;
            }
            if (level.getBlockState(actual).getBlock() != expected) {
                return false;
            }
        }
        return true;
    }

    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        Direction facing = getControllerFacing(level, controllerPos);
        Map<Block, Integer> missing = new LinkedHashMap<>();
        countMissing(level, controllerPos, INTERFACE_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), missing, facing);
        countMissing(level, controllerPos, CORE_SET, ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get(), missing, facing);
        countMissing(level, controllerPos, CASING_SET, ModBlocks.HYPERDIMENSIONAL_CASING.get(), missing, facing);
        return missing;
    }

    private static void countMissing(Level level, BlockPos controllerPos, Set<BlockPos> relativeSet,
            Block expected, Map<Block, Integer> missing, Direction facing) {
        for (BlockPos rel : relativeSet) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!level.isLoaded(actual)) {
                continue;
            }
            if (level.getBlockState(actual).getBlock() != expected) {
                missing.put(expected, missing.getOrDefault(expected, 0) + 1);
            }
        }
    }

    public static void assemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) {
            return;
        }

        // 更新控制器状态
        BlockState controllerState = level.getBlockState(controllerPos);
        if (controllerState.getBlock() == ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get()) {
            if (level.getBlockEntity(controllerPos) instanceof HyperdimensionalControllerBlockEntity controller) {
                controller.assemble();
            }
        }

        // 绑定接口到控制器
        Direction facing = getControllerFacing(level, controllerPos);
        for (BlockPos rel : INTERFACE_SET) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            BlockState state = level.getBlockState(actual);
            if (state.getBlock() == ModBlocks.MULTIBLOCK_ME_INTERFACE.get()) {
                if (level.getBlockEntity(actual) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
                    interfaceBe.setControllerPos(controllerPos);
                }
            }
        }
    }

    public static void disassemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) {
            return;
        }

        // 解除接口绑定
        Direction facing = getControllerFacing(level, controllerPos);
        for (BlockPos rel : INTERFACE_SET) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            BlockState state = level.getBlockState(actual);
            if (state.getBlock() == ModBlocks.MULTIBLOCK_ME_INTERFACE.get()) {
                if (level.getBlockEntity(actual) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
                    interfaceBe.setControllerPos(null);
                }
            }
        }

        // 更新控制器状态
        BlockState controllerState = level.getBlockState(controllerPos);
        if (controllerState.getBlock() == ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get()) {
            if (level.getBlockEntity(controllerPos) instanceof HyperdimensionalControllerBlockEntity controller) {
                controller.disassemble();
            }
        }
    }

    /**
     * 创造模式：一键生成所有缺失方块并立即组装。
     */
    public static void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
        if (level.isClientSide()) {
            return;
        }
        Direction facing = getControllerFacing(level, controllerPos);

        placeBlocks(level, controllerPos, INTERFACE_SET, ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), facing);
        placeBlocks(level, controllerPos, CORE_SET, ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get(), facing);
        placeBlocks(level, controllerPos, CASING_SET, ModBlocks.HYPERDIMENSIONAL_CASING.get(), facing);

        assemble(level, controllerPos);
    }

    private static void placeBlocks(Level level, BlockPos controllerPos, Set<BlockPos> set, Block block,
            Direction facing) {
        for (BlockPos rel : set) {
            BlockPos pos = controllerPos.offset(rotate(rel, facing));
            if (level.getBlockState(pos).getBlock() != block) {
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * 生存模式：检查背包材料，足够则扣除并放置，最后组装。
     */
    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player) {
        if (level.isClientSide()) {
            return false;
        }

        Map<Block, Integer> missing = getMissingMap(level, controllerPos);
        if (missing.isEmpty()) {
            assemble(level, controllerPos);
            return true;
        }

        Inventory inv = player.getInventory();
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (ItemStack stack : inv.items) {
            if (stack.isEmpty()) {
                continue;
            }
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                if (stack.getItem() == entry.getKey().asItem()) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    entry.setValue(Math.max(0, need - have));
                    break;
                }
            }
        }

        for (int count : needed.values()) {
            if (count > 0) {
                return false;
            }
        }

        for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
            Block block = entry.getKey();
            int remaining = entry.getValue();
            for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
                ItemStack stack = inv.items.get(i);
                if (stack.getItem() == block.asItem()) {
                    int take = Math.min(stack.getCount(), remaining);
                    inv.removeItem(i, take);
                    remaining -= take;
                }
            }
        }

        placeMissingBlocks(level, controllerPos, player);
        return true;
    }

    private static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        BlockState state = level.getBlockState(controllerPos);
        if (state.getBlock() == ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get()) {
            return state.getValue(HyperdimensionalControllerBlock.FACING);
        }
        return Direction.NORTH;
    }
}
