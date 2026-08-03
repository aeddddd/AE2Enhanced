package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.FloorStyle;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FloorPreset} 单元测试.
 */
class FloorPresetTest {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    /** 2×2 棋盘预设: (0,0)=0, (1,0)=1, (0,1)=1, (1,1)=0. */
    private static FloorPreset checker() {
        return new FloorPreset(2, 2, new BlockState[] { STONE, DIRT }, new int[] { 0, 1, 1, 0 });
    }

    @Test
    void getStateShouldReadPaletteByIndex() {
        FloorPreset preset = checker();

        assertThat(preset.getState(0, 0)).isSameAs(STONE);
        assertThat(preset.getState(1, 0)).isSameAs(DIRT);
        assertThat(preset.getState(0, 1)).isSameAs(DIRT);
        assertThat(preset.getState(1, 1)).isSameAs(STONE);
    }

    @Test
    void getStateShouldTileInfinitely() {
        FloorPreset preset = checker();

        assertThat(preset.getState(2, 0)).isSameAs(STONE);
        assertThat(preset.getState(3, 1)).isSameAs(STONE);
        assertThat(preset.getState(100, 100)).isSameAs(STONE);
        assertThat(preset.getState(101, 100)).isSameAs(DIRT);
    }

    @Test
    void getStateShouldHandleNegativeCoordinates() {
        FloorPreset preset = checker();

        // floorMod(-1, 2) = 1
        assertThat(preset.getState(-1, 0)).isSameAs(DIRT);
        assertThat(preset.getState(0, -1)).isSameAs(DIRT);
        assertThat(preset.getState(-2, -2)).isSameAs(STONE);
    }

    @Test
    void getStateShouldReturnNullForDegeneratePreset() {
        assertThat(new FloorPreset(0, 2, new BlockState[] { STONE }, new int[] { 0 }).getState(0, 0)).isNull();
        assertThat(new FloorPreset(2, 0, new BlockState[] { STONE }, new int[] { 0 }).getState(0, 0)).isNull();
        assertThat(new FloorPreset(2, 2, null, new int[] { 0 }).getState(0, 0)).isNull();
        assertThat(new FloorPreset(2, 2, new BlockState[] { STONE }, null).getState(0, 0)).isNull();
    }

    @Test
    void getStateShouldReturnNullWhenStateListShorterThanArea() {
        // stateList 只有一个元素,访问 (1,1) 时 idx=3 越界
        FloorPreset preset = new FloorPreset(2, 2, new BlockState[] { STONE }, new int[] { 0 });

        assertThat(preset.getState(0, 0)).isSameAs(STONE);
        assertThat(preset.getState(1, 1)).isNull();
    }

    @Test
    void getStateShouldReturnNullWhenPaletteIndexOutOfBounds() {
        FloorPreset preset = new FloorPreset(1, 2, new BlockState[] { STONE }, new int[] { 0, 5 });

        assertThat(preset.getState(0, 0)).isSameAs(STONE);
        assertThat(preset.getState(0, 1)).isNull();

        FloorPreset negative = new FloorPreset(1, 1, new BlockState[] { STONE }, new int[] { -1 });
        assertThat(negative.getState(0, 0)).isNull();
    }

    @Test
    void fromShouldReturnSameInstanceForFloorPreset() {
        FloorPreset preset = checker();

        assertThat(FloorPreset.from(preset)).isSameAs(preset);
    }

    @Test
    void fromShouldSampleArbitraryPresetIntoPalette() {
        // 4×1 样式: 石 石 土 石
        IFloorPreset custom = new IFloorPreset() {
            @Override
            public int getWidth() {
                return 4;
            }

            @Override
            public int getDepth() {
                return 1;
            }

            @Override
            public BlockState getState(int worldX, int worldZ) {
                return worldX == 2 ? DIRT : STONE;
            }
        };

        FloorPreset sampled = FloorPreset.from(custom);

        assertThat(sampled.width).isEqualTo(4);
        assertThat(sampled.depth).isEqualTo(1);
        // 调色板去重,保持首次出现顺序
        assertThat(sampled.palette).containsExactly(STONE, DIRT);
        assertThat(sampled.getState(0, 0)).isSameAs(STONE);
        assertThat(sampled.getState(2, 0)).isSameAs(DIRT);
        assertThat(sampled.getState(3, 0)).isSameAs(STONE);
    }

    @Test
    void fromShouldReplaceNullWithBedrock() {
        IFloorPreset withNull = new IFloorPreset() {
            @Override
            public int getWidth() {
                return 2;
            }

            @Override
            public int getDepth() {
                return 1;
            }

            @Override
            public BlockState getState(int worldX, int worldZ) {
                return worldX == 0 ? STONE : null;
            }
        };

        FloorPreset sampled = FloorPreset.from(withNull);

        assertThat(sampled.getState(1, 0)).isEqualTo(Blocks.BEDROCK.defaultBlockState());
    }

    @Test
    void fromShouldSampleFloorStyleCorrectly() {
        // 2×1 区块网格的组合样式,采样后应为 32×16
        FloorStyle style = new FloorStyle(2, 1,
                new com.github.aeddddd.ae2enhanced.api.dimension.IFloorTile[] {
                        (x, z) -> STONE,
                        (x, z) -> DIRT
                });

        FloorPreset sampled = FloorPreset.from(style);

        assertThat(sampled.width).isEqualTo(32);
        assertThat(sampled.depth).isEqualTo(16);
        assertThat(sampled.palette).containsExactly(STONE, DIRT);
        assertThat(sampled.getState(0, 0)).isSameAs(STONE);
        assertThat(sampled.getState(16, 0)).isSameAs(DIRT);
        // 采样结果是 FloorPreset,其自身平铺语义应与原样式一致
        assertThat(sampled.getState(32, 0)).isSameAs(STONE);
    }
}
