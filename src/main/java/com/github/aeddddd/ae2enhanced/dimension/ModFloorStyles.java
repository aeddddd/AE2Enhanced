package com.github.aeddddd.ae2enhanced.dimension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorTile;
import com.github.aeddddd.ae2enhanced.api.dimension.PersonalDimensionApi;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

/**
 * 内置地板样式:与主分支 96×96 预设一致,以 6×6 区块网格的组合样式表达——
 * 第一行与第一列为马路(11 种边框单元),其余 5×5 为平台(同一种单元).
 */
public final class ModFloorStyles {

    /** 组合样式网格边长(区块),96 格 / 16 = 6. */
    public static final int GRID_SIZE = 6;

    /** 平台区域边长(区块),占据网格的 5×5. */
    public static final int PLATFORM_SIZE = 5;

    private ModFloorStyles() {
    }

    /**
     * 注册内置区块单元(马路/平台)与默认组合样式;id 已被其他模组占用时不覆盖.
     */
    public static void register() {
        FloorPreset road;
        FloorPreset platform;
        FloorStyle style;

        FloorPreset source = PresetLoader.loadBuiltinAsset();
        if (source != null && source.width == GRID_SIZE * IFloorTile.SIZE
                && source.depth == GRID_SIZE * IFloorTile.SIZE) {
            // 与主分支逐格一致:将 96×96 预设切为 6×6 个区块单元拼合
            FloorPreset[] tiles = sliceIntoTiles(source);
            road = tiles[tileIndex(0, 2)];
            platform = tiles[tileIndex(1, 1)];
            style = new FloorStyle(GRID_SIZE, GRID_SIZE, tiles);
        } else {
            // 内置 asset 缺失或尺寸异常时兜底:近似主分支风格的马路+平台组合
            AE2Enhanced.LOGGER.warn("[AE2E] Builtin floor preset asset missing or has unexpected size, "
                    + "using simplified road/platform fallback style.");
            road = createRoadTile();
            platform = createPlatformTile();
            style = createFallbackStyle(road, platform);
        }

        if (PersonalDimensionApi.getFloorTile(PersonalDimensionApi.ROAD_TILE_ID).isEmpty()) {
            PersonalDimensionApi.registerFloorTile(PersonalDimensionApi.ROAD_TILE_ID, road);
        }
        if (PersonalDimensionApi.getFloorTile(PersonalDimensionApi.PLATFORM_TILE_ID).isEmpty()) {
            PersonalDimensionApi.registerFloorTile(PersonalDimensionApi.PLATFORM_TILE_ID, platform);
        }
        if (PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID).isEmpty()) {
            PersonalDimensionApi.registerFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID, style);
        }
    }

    private static int tileIndex(int chunkX, int chunkZ) {
        return chunkZ * GRID_SIZE + chunkX;
    }

    /**
     * 将 96×96 预设按 16×16 切为 6×6 个区块单元(行优先).
     */
    private static FloorPreset[] sliceIntoTiles(FloorPreset source) {
        FloorPreset[] tiles = new FloorPreset[GRID_SIZE * GRID_SIZE];
        for (int tz = 0; tz < GRID_SIZE; tz++) {
            for (int tx = 0; tx < GRID_SIZE; tx++) {
                List<BlockState> palette = new ArrayList<>();
                Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
                int[] states = new int[IFloorTile.SIZE * IFloorTile.SIZE];
                for (int z = 0; z < IFloorTile.SIZE; z++) {
                    for (int x = 0; x < IFloorTile.SIZE; x++) {
                        BlockState state = source.getState(tx * IFloorTile.SIZE + x, tz * IFloorTile.SIZE + z);
                        if (state == null) {
                            state = Blocks.BEDROCK.defaultBlockState();
                        }
                        Integer idx = paletteIndex.get(state);
                        if (idx == null) {
                            idx = palette.size();
                            palette.add(state);
                            paletteIndex.put(state, idx);
                        }
                        states[z * IFloorTile.SIZE + x] = idx;
                    }
                }
                tiles[tileIndex(tx, tz)] = new FloorPreset(IFloorTile.SIZE, IFloorTile.SIZE,
                        palette.toArray(new BlockState[0]), states);
            }
        }
        return tiles;
    }

    /**
     * 兜底组合样式:第一行与第一列为马路,其余 5×5 为平台.
     */
    private static FloorStyle createFallbackStyle(FloorPreset road, FloorPreset platform) {
        FloorStyle.Builder builder = new FloorStyle.Builder(GRID_SIZE, GRID_SIZE, platform);
        for (int i = 0; i < GRID_SIZE; i++) {
            builder.set(0, i, road);
            builder.set(i, 0, road);
        }
        return builder.build();
    }

    /**
     * 兜底马路单元:灰色混凝土路面 + 白色混凝土十字中线 + 外圈警示方块路缘.
     */
    private static FloorPreset createRoadTile() {
        BlockState stripes = ModBlocks.YELLOW_STRIPES_BLOCK_B.get().defaultBlockState();
        BlockState asphalt = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState line = Blocks.WHITE_CONCRETE.defaultBlockState();
        BlockState[] palette = { stripes, asphalt, line };
        int[] states = new int[IFloorTile.SIZE * IFloorTile.SIZE];
        int mid = IFloorTile.SIZE / 2;
        for (int z = 0; z < IFloorTile.SIZE; z++) {
            for (int x = 0; x < IFloorTile.SIZE; x++) {
                boolean edge = x == 0 || z == 0 || x == IFloorTile.SIZE - 1 || z == IFloorTile.SIZE - 1;
                boolean centerLine = x == mid - 1 || x == mid || z == mid - 1 || z == mid;
                states[z * IFloorTile.SIZE + x] = edge ? 0 : (centerLine ? 2 : 1);
            }
        }
        return new FloorPreset(IFloorTile.SIZE, IFloorTile.SIZE, palette, states);
    }

    /**
     * 兜底平台单元:纯黑色混凝土,与主分支中心广场一致.
     */
    private static FloorPreset createPlatformTile() {
        BlockState[] palette = { Blocks.BLACK_CONCRETE.defaultBlockState() };
        int[] states = new int[IFloorTile.SIZE * IFloorTile.SIZE];
        return new FloorPreset(IFloorTile.SIZE, IFloorTile.SIZE, palette, states);
    }
}
