package com.github.aeddddd.ae2enhanced.dimension;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorTile;

/**
 * 组合式地板样式:以 16×16 区块单元为最小单元,按区块网格拼合并对全维度平铺.
 */
public class FloorStyle implements IFloorPreset {

    private final int gridWidth;
    private final int gridDepth;
    private final IFloorTile[] tiles;

    public FloorStyle(int gridWidth, int gridDepth, IFloorTile[] tiles) {
        if (gridWidth <= 0 || gridDepth <= 0) {
            throw new IllegalArgumentException("Floor style grid size must be positive");
        }
        if (tiles == null || tiles.length != gridWidth * gridDepth) {
            throw new IllegalArgumentException("Floor style tiles must match grid size");
        }
        for (IFloorTile tile : tiles) {
            if (tile == null) {
                throw new IllegalArgumentException("Floor style tiles must not contain null");
            }
        }
        this.gridWidth = gridWidth;
        this.gridDepth = gridDepth;
        this.tiles = tiles.clone();
    }

    @Override
    public int getWidth() {
        return gridWidth * IFloorTile.SIZE;
    }

    @Override
    public int getDepth() {
        return gridDepth * IFloorTile.SIZE;
    }

    @Override
    @Nullable
    public BlockState getState(int worldX, int worldZ) {
        int px = Math.floorMod(worldX, getWidth());
        int pz = Math.floorMod(worldZ, getDepth());
        IFloorTile tile = tiles[(pz / IFloorTile.SIZE) * gridWidth + (px / IFloorTile.SIZE)];
        return tile.getState(Math.floorMod(worldX, IFloorTile.SIZE), Math.floorMod(worldZ, IFloorTile.SIZE));
    }

    /**
     * 以指定单元填满整个网格创建样式.
     */
    public static FloorStyle filled(int gridWidth, int gridDepth, IFloorTile fill) {
        return new Builder(gridWidth, gridDepth, fill).build();
    }

    /**
     * 链式构建器:先以填充单元铺满网格,再按区块坐标覆盖局部单元.
     */
    public static final class Builder {

        private final int gridWidth;
        private final int gridDepth;
        private final IFloorTile[] tiles;

        public Builder(int gridWidth, int gridDepth, IFloorTile fill) {
            if (fill == null) {
                throw new IllegalArgumentException("Fill tile must not be null");
            }
            if (gridWidth <= 0 || gridDepth <= 0) {
                throw new IllegalArgumentException("Floor style grid size must be positive");
            }
            this.gridWidth = gridWidth;
            this.gridDepth = gridDepth;
            this.tiles = new IFloorTile[gridWidth * gridDepth];
            java.util.Arrays.fill(this.tiles, fill);
        }

        /**
         * 将区块坐标 (chunkX, chunkZ) 处的单元替换为指定单元.
         */
        public Builder set(int chunkX, int chunkZ, IFloorTile tile) {
            if (tile == null) {
                throw new IllegalArgumentException("Tile must not be null");
            }
            checkBounds(chunkX, chunkZ);
            tiles[chunkZ * gridWidth + chunkX] = tile;
            return this;
        }

        /**
         * 将区块坐标矩形区域(含两端)替换为指定单元.
         */
        public Builder fillRect(int fromChunkX, int fromChunkZ, int toChunkX, int toChunkZ, IFloorTile tile) {
            for (int z = fromChunkZ; z <= toChunkZ; z++) {
                for (int x = fromChunkX; x <= toChunkX; x++) {
                    set(x, z, tile);
                }
            }
            return this;
        }

        public FloorStyle build() {
            return new FloorStyle(gridWidth, gridDepth, tiles);
        }

        private void checkBounds(int chunkX, int chunkZ) {
            if (chunkX < 0 || chunkX >= gridWidth || chunkZ < 0 || chunkZ >= gridDepth) {
                throw new IllegalArgumentException(
                        "Chunk coordinate out of grid: (" + chunkX + ", " + chunkZ + ")");
            }
        }
    }
}
