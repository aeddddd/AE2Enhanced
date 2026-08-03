package com.github.aeddddd.ae2enhanced.structure;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.github.aeddddd.ae2enhanced.multiblock.IMultiblockController;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

/**
 * 多方块结构的通用抽象实现.
 * <p>提供基于 {@link StructureDefinition} 的验证、装配、拆解、缺失统计与一键放置实现.
 * 子类只需提供结构旋转方向与可能的自定义装配逻辑.</p>
 */
public abstract class AbstractMultiblockStructure implements IMultiblockStructure {

    protected final StructureDefinition definition;

    protected AbstractMultiblockStructure(StructureDefinition definition) {
        this.definition = definition;
    }

    @Override
    public boolean validate(Level level, BlockPos controllerPos) {
        ValidationResult result = validateDetailed(level, controllerPos);
        return result.passed();
    }

    @Override
    public ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        boolean allChunksLoaded = true;
        for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
            BlockPos actual = controllerPos.offset(StructureUtils.rotate(entry.getKey(), getRotation(level, controllerPos)));
            if (!level.isLoaded(actual)) {
                allChunksLoaded = false;
                missing.merge(entry.getValue(), 1, Integer::sum);
                continue;
            }
            if (level.getBlockState(actual).getBlock() != entry.getValue()) {
                missing.merge(entry.getValue(), 1, Integer::sum);
            }
        }
        return new ValidationResult(missing.isEmpty() && allChunksLoaded, missing, allChunksLoaded);
    }

    @Override
    public Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
            BlockPos actual = controllerPos.offset(StructureUtils.rotate(entry.getKey(), getRotation(level, controllerPos)));
            if (!level.isLoaded(actual)) {
                continue;
            }
            if (level.getBlockState(actual).getBlock() != entry.getValue()) {
                missing.merge(entry.getValue(), 1, Integer::sum);
            }
        }
        return missing;
    }

    @Override
    public void placeMissingBlocks(Level level, BlockPos controllerPos, @Nullable Player player) {
        if (level.isClientSide()) {
            return;
        }
        for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
            BlockPos actual = controllerPos.offset(StructureUtils.rotate(entry.getKey(), getRotation(level, controllerPos)));
            if (level.getBlockState(actual).getBlock() != entry.getValue()) {
                level.setBlock(actual, entry.getValue().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        assemble(level, controllerPos);
    }

    @Override
    public boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player) {
        if (level.isClientSide()) {
            return false;
        }
        if (getMissingMap(level, controllerPos).isEmpty()) {
            assemble(level, controllerPos);
            return true;
        }

        // 部分组装：逐个缺失位置消耗背包中对应方块并放置,
        // 材料不够的种类跳过,允许玩家多次点击逐步补齐直至完整成型.
        Inventory inv = player.getInventory();
        Direction rotation = getRotation(level, controllerPos);
        boolean placedAny = false;
        for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
            BlockPos actual = controllerPos.offset(StructureUtils.rotate(entry.getKey(), rotation));
            if (!level.isLoaded(actual)) {
                continue;
            }
            if (level.getBlockState(actual).getBlock() == entry.getValue()) {
                continue;
            }
            if (!consumeOne(inv, entry.getValue().asItem())) {
                continue;
            }
            level.setBlock(actual, entry.getValue().defaultBlockState(), Block.UPDATE_ALL);
            placedAny = true;
        }

        if (getMissingMap(level, controllerPos).isEmpty()) {
            assemble(level, controllerPos);
        }
        return placedAny;
    }

    /**
     * 从背包主物品栏消耗一个指定物品.
     *
     * @return 是否成功消耗
     */
    private static boolean consumeOne(Inventory inv, Item item) {
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                inv.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<BlockPos> getAllPositions() {
        return definition.getAllPositions();
    }

    @Override
    public Map<Block, Integer> getRequiredMaterials() {
        Map<Block, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<Block, Set<BlockPos>> entry : definition.getBlockSets().entrySet()) {
            result.put(entry.getKey(), entry.getValue().size());
        }
        return result;
    }

    /**
     * 获取按当前控制器朝向旋转后的期望方块相对坐标集合.
     *
     * @return (旋转后的相对坐标, 方块类型) 集合
     */
    @Override
    public Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos) {
        Direction rotation = getRotation(level, controllerPos);
        Set<Map.Entry<BlockPos, Block>> result = new HashSet<>();
        for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
            result.add(new AbstractMap.SimpleEntry<>(StructureUtils.rotate(entry.getKey(), rotation), entry.getValue()));
        }
        return result;
    }

    @Override
    public void assemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) {
            return;
        }
        if (!(level.getBlockEntity(controllerPos) instanceof IMultiblockController controller)) {
            return;
        }
        controller.assemble();
    }

    @Override
    public void disassemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) {
            return;
        }
        if (!(level.getBlockEntity(controllerPos) instanceof IMultiblockController controller)) {
            return;
        }
        controller.disassemble();
    }

    @Override
    public abstract Direction getRotation(Level level, BlockPos controllerPos);

    /**
     * 辅助方法：获取当前控制器方块的水平朝向.
     */
    protected static Direction getBlockFacing(Level level, BlockPos pos, Block expectedBlock) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == expectedBlock) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.NORTH;
    }
}
