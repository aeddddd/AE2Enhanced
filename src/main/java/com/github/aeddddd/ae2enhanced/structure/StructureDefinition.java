package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * 多方块结构定义：描述结构由哪些方块组成、各位于什么相对坐标。
 * <p>可从 JSON 加载，也可在代码中硬编码构建，用于 {@link AbstractMultiblockStructure}。</p>
 */
public final class StructureDefinition {

    private final Map<Block, Set<BlockPos>> blockSets;
    private final Set<BlockPos> allPositions;
    private final BlockPos interfaceRelativePos;

    private StructureDefinition(Map<Block, Set<BlockPos>> blockSets, Set<BlockPos> allPositions,
            @Nullable BlockPos interfaceRelativePos) {
        this.blockSets = Collections.unmodifiableMap(blockSets);
        this.allPositions = Collections.unmodifiableSet(allPositions);
        this.interfaceRelativePos = interfaceRelativePos;
    }

    /**
     * 从 JSON 格式的方块-坐标映射构建定义。
     *
     * @param blockSets 方块 -> 相对坐标集合
     * @param interfaceRelativePos 通用 ME 接口相对坐标，可为 null
     */
    public static StructureDefinition of(Map<Block, Set<BlockPos>> blockSets, @Nullable BlockPos interfaceRelativePos) {
        Map<Block, Set<BlockPos>> copied = new LinkedHashMap<>();
        Set<BlockPos> all = new HashSet<>();
        for (Map.Entry<Block, Set<BlockPos>> entry : blockSets.entrySet()) {
            Set<BlockPos> set = Collections.unmodifiableSet(new HashSet<>(entry.getValue()));
            copied.put(entry.getKey(), set);
            all.addAll(set);
        }
        return new StructureDefinition(Collections.unmodifiableMap(copied), Collections.unmodifiableSet(all), interfaceRelativePos);
    }

    public Map<Block, Set<BlockPos>> getBlockSets() {
        return blockSets;
    }

    public Set<BlockPos> getAllPositions() {
        return allPositions;
    }

    @Nullable
    public BlockPos getInterfaceRelativePos() {
        return interfaceRelativePos;
    }

    /**
     * 返回所有期望方块位置及其类型，用于验证与缺失统计。
     */
    public Set<Map.Entry<BlockPos, Block>> getExpectedBlocks() {
        Set<Map.Entry<BlockPos, Block>> result = new HashSet<>();
        for (Map.Entry<Block, Set<BlockPos>> entry : blockSets.entrySet()) {
            Block block = entry.getKey();
            for (BlockPos rel : entry.getValue()) {
                result.add(new java.util.AbstractMap.SimpleEntry<>(rel, block));
            }
        }
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 链式构建器。
     */
    public static final class Builder {
        private final Map<Block, Set<BlockPos>> blockSets = new LinkedHashMap<>();
        private final Set<BlockPos> allPositions = new HashSet<>();
        private BlockPos interfaceRelativePos = null;

        private Builder() {
        }

        public Builder add(Block block, BlockPos relativePos) {
            blockSets.computeIfAbsent(block, k -> new HashSet<>()).add(relativePos);
            allPositions.add(relativePos);
            return this;
        }

        public Builder addAll(Block block, Set<BlockPos> relativePositions) {
            for (BlockPos pos : relativePositions) {
                add(block, pos);
            }
            return this;
        }

        public Builder interfacePos(BlockPos relativePos) {
            this.interfaceRelativePos = relativePos;
            return this;
        }

        public StructureDefinition build() {
            return StructureDefinition.of(blockSets, interfaceRelativePos);
        }
    }
}
