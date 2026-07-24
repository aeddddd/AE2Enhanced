package com.github.aeddddd.ae2enhanced.dimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorTile;

/**
 * 个人维度地板预设,按单元平铺;16×16 尺寸时可直接作为组合样式的区块单元使用.
 */
public class FloorPreset implements IFloorPreset, IFloorTile {

    public final int width;
    public final int depth;
    public final BlockState[] palette;
    public final int[] stateList;

    public FloorPreset(int width, int depth, BlockState[] palette, int[] stateList) {
        this.width = width;
        this.depth = depth;
        this.palette = palette;
        this.stateList = stateList;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    @Nullable
    public BlockState getState(int x, int z) {
        if (width <= 0 || depth <= 0 || palette == null || stateList == null) {
            return null;
        }
        int px = Math.floorMod(x, width);
        int pz = Math.floorMod(z, depth);
        int idx = pz * width + px;
        if (idx < 0 || idx >= stateList.length) {
            return null;
        }
        int stateIdx = stateList[idx];
        if (stateIdx < 0 || stateIdx >= palette.length) {
            return null;
        }
        return palette[stateIdx];
    }

    /**
     * 将任意 {@link IFloorPreset} 采样为调色板形式,用于 GUI 预览同步;
     * null 状态与生成器行为一致回退为基岩.
     */
    public static FloorPreset from(IFloorPreset preset) {
        if (preset instanceof FloorPreset floorPreset) {
            return floorPreset;
        }
        int width = preset.getWidth();
        int depth = preset.getDepth();
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> index = new LinkedHashMap<>();
        int[] states = new int[width * depth];
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                BlockState state = preset.getState(x, z);
                if (state == null) {
                    state = Blocks.BEDROCK.defaultBlockState();
                }
                Integer idx = index.get(state);
                if (idx == null) {
                    idx = palette.size();
                    palette.add(state);
                    index.put(state, idx);
                }
                states[z * width + x] = idx;
            }
        }
        return new FloorPreset(width, depth, palette.toArray(new BlockState[0]), states);
    }
}
