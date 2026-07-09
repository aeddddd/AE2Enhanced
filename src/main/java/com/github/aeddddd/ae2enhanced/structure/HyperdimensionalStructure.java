package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

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

    public static final AbstractMultiblockStructure INSTANCE;

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

        StructureDefinition definition = StructureDefinition.builder()
                .addAll(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get(), CONTROLLER_SET)
                .addAll(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), INTERFACE_SET)
                .addAll(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get(), CORE_SET)
                .addAll(ModBlocks.HYPERDIMENSIONAL_CASING.get(), CASING_SET)
                .interfacePos(new BlockPos(0, 0, 4))
                .build();
        INSTANCE = new Impl(definition);
    }

    public static boolean validate(Level level, BlockPos controllerPos) {
        return INSTANCE.validate(level, controllerPos);
    }

    public static ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
        return INSTANCE.validateDetailed(level, controllerPos);
    }

    public static Set<BlockPos> getAllSet() {
        return ALL_SET;
    }

    public static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        return INSTANCE.getRotation(level, controllerPos);
    }

    public static Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos) {
        return INSTANCE.getExpectedBlocks(level, controllerPos);
    }

    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        return INSTANCE.getMissingMap(level, controllerPos);
    }

    public static void assemble(Level level, BlockPos controllerPos) {
        INSTANCE.assemble(level, controllerPos);
    }

    public static void disassemble(Level level, BlockPos controllerPos) {
        INSTANCE.disassemble(level, controllerPos);
    }

    public static void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
        INSTANCE.placeMissingBlocks(level, controllerPos, player);
    }

    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player) {
        return INSTANCE.tryConsumeAndPlace(level, controllerPos, player);
    }

    private static class Impl extends AbstractMultiblockStructure {

        private Impl(StructureDefinition definition) {
            super(definition);
        }

        @Override
        public Direction getRotation(Level level, BlockPos controllerPos) {
            BlockState state = level.getBlockState(controllerPos);
            if (state.getBlock() == ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get()) {
                return state.getValue(HyperdimensionalControllerBlock.FACING);
            }
            return Direction.NORTH;
        }

        @Override
        public void assemble(Level level, BlockPos controllerPos) {
            if (level.isClientSide()) {
                return;
            }

            Direction facing = getRotation(level, controllerPos);
            for (BlockPos rel : INTERFACE_SET) {
                BlockPos actual = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (level.getBlockEntity(actual) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
                    interfaceBe.setControllerPos(controllerPos);
                }
            }

            if (level.getBlockEntity(controllerPos) instanceof HyperdimensionalControllerBlockEntity controller) {
                controller.assemble();
            }
        }

        @Override
        public void disassemble(Level level, BlockPos controllerPos) {
            if (level.isClientSide()) {
                return;
            }

            Direction facing = getRotation(level, controllerPos);
            for (BlockPos rel : INTERFACE_SET) {
                BlockPos actual = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (level.getBlockEntity(actual) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
                    interfaceBe.setControllerPos(null);
                }
            }

            if (level.getBlockEntity(controllerPos) instanceof HyperdimensionalControllerBlockEntity controller) {
                controller.disassemble();
            }
        }
    }
}
