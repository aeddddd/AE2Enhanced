package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.block.state.IBlockState;

/**
 * 个人维度地板预设，按单元平铺。
 */
public class FloorPreset {

    public final int width;
    public final int depth;
    public final IBlockState[] palette;
    public final int[] stateList;

    public FloorPreset(int width, int depth, IBlockState[] palette, int[] stateList) {
        this.width = width;
        this.depth = depth;
        this.palette = palette;
        this.stateList = stateList;
    }

    public IBlockState getState(int x, int z) {
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
}
