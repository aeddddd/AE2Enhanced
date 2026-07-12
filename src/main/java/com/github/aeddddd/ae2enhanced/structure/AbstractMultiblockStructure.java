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
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

/**
 * 多方块结构的通用抽象实现。
 * <p>提供基于 {@link StructureDefinition} 的验证、装配、拆解、缺失统计与一键放置实现。
 * 子类只需提供结构旋转方向与可能的自定义装配逻辑。</p>
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
            Item item = block.asItem();
            for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
                ItemStack stack = inv.items.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.removeItem(i, take).getCount();
                    remaining -= removed;
                }
            }
        }

        placeMissingBlocks(level, controllerPos, player);
        return true;
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
     * 获取按当前控制器朝向旋转后的期望方块相对坐标集合。
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
    @Nullable
    public BlockPos getInterfaceRelativePos() {
        return definition.getInterfaceRelativePos();
    }

    @Override
    public void assemble(Level level, BlockPos controllerPos) {
        if (level.isClientSide()) {
            return;
        }
        if (!(level.getBlockEntity(controllerPos) instanceof IMultiblockController controller)) {
            return;
        }
        BlockPos interfacePos = findInterfacePos(level, controllerPos);
        if (interfacePos != null && level.getBlockEntity(interfacePos) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
            interfaceBe.setControllerPos(controllerPos);
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
        BlockPos interfacePos = findInterfacePos(level, controllerPos);
        if (interfacePos != null && level.getBlockEntity(interfacePos) instanceof MultiblockMeInterfaceBlockEntity interfaceBe) {
            interfaceBe.setControllerPos(null);
        }
    }

    @Nullable
    protected BlockPos findInterfacePos(Level level, BlockPos controllerPos) {
        BlockPos rel = getInterfaceRelativePos();
        if (rel == null) {
            return null;
        }
        return controllerPos.offset(StructureUtils.rotate(rel, getRotation(level, controllerPos)));
    }

    @Override
    public abstract Direction getRotation(Level level, BlockPos controllerPos);

    /**
     * 辅助方法：获取当前控制器方块的水平朝向。
     */
    protected static Direction getBlockFacing(Level level, BlockPos pos, Block expectedBlock) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() == expectedBlock) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }
        return Direction.NORTH;
    }
}
