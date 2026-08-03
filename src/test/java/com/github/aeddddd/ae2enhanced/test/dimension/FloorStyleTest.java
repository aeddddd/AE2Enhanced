package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.IFloorTile;
import com.github.aeddddd.ae2enhanced.dimension.FloorStyle;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FloorStyle} 单元测试.
 */
class FloorStyleTest {

    private static final BlockState STATE_A = Blocks.STONE.defaultBlockState();
    private static final BlockState STATE_B = Blocks.DIRT.defaultBlockState();
    private static final BlockState STATE_C = Blocks.GLASS.defaultBlockState();

    /** 全单元返回固定状态的测试单元. */
    private static IFloorTile constantTile(BlockState state) {
        return (x, z) -> state;
    }

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void constructorShouldRejectNonPositiveGrid() {
        IFloorTile tile = constantTile(STATE_A);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle(0, 1, new IFloorTile[] { tile }));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle(1, 0, new IFloorTile[] { tile }));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle(-1, 1, new IFloorTile[] { tile }));
    }

    @Test
    void constructorShouldRejectNullOrMismatchedTiles() {
        IFloorTile tile = constantTile(STATE_A);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle(1, 1, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle(2, 2, new IFloorTile[] { tile }));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle(1, 2, new IFloorTile[] { tile, null }));
    }

    @Test
    void sizeShouldBeGridTimesTileSize() {
        FloorStyle style = FloorStyle.filled(2, 3, constantTile(STATE_A));

        assertThat(style.getWidth()).isEqualTo(32);
        assertThat(style.getDepth()).isEqualTo(48);
    }

    @Test
    void getStateShouldResolveTileByChunkGrid() {
        IFloorTile tileA = constantTile(STATE_A);
        IFloorTile tileB = constantTile(STATE_B);
        // 2×1 网格: (0,0)=A, (1,0)=B
        FloorStyle style = new FloorStyle(2, 1, new IFloorTile[] { tileA, tileB });

        assertThat(style.getState(0, 0)).isSameAs(STATE_A);
        assertThat(style.getState(15, 15)).isSameAs(STATE_A);
        assertThat(style.getState(16, 0)).isSameAs(STATE_B);
        assertThat(style.getState(31, 15)).isSameAs(STATE_B);
    }

    @Test
    void getStateShouldTileInfinitely() {
        IFloorTile tileA = constantTile(STATE_A);
        IFloorTile tileB = constantTile(STATE_B);
        FloorStyle style = new FloorStyle(2, 1, new IFloorTile[] { tileA, tileB });

        // 第二个平铺周期
        assertThat(style.getState(32, 0)).isSameAs(STATE_A);
        assertThat(style.getState(48, 0)).isSameAs(STATE_B);
        // 大坐标
        assertThat(style.getState(32 * 100, 0)).isSameAs(STATE_A);
        assertThat(style.getState(32 * 100 + 16, 0)).isSameAs(STATE_B);
    }

    @Test
    void getStateShouldHandleNegativeCoordinates() {
        IFloorTile tileA = constantTile(STATE_A);
        IFloorTile tileB = constantTile(STATE_B);
        FloorStyle style = new FloorStyle(2, 1, new IFloorTile[] { tileA, tileB });

        // floorMod 语义: -1 落在 32 宽周期的 31 处,即 B 单元
        assertThat(style.getState(-1, 0)).isSameAs(STATE_B);
        assertThat(style.getState(-16, 0)).isSameAs(STATE_B);
        assertThat(style.getState(-17, 0)).isSameAs(STATE_A);
        assertThat(style.getState(-32, 0)).isSameAs(STATE_A);
    }

    @Test
    void getStateShouldPassLocalCoordinatesToTile() {
        // 记录单元收到的局部坐标
        int[] seen = new int[2];
        IFloorTile recording = (x, z) -> {
            seen[0] = x;
            seen[1] = z;
            return STATE_A;
        };
        FloorStyle style = FloorStyle.filled(1, 1, recording);

        style.getState(0, 0);
        assertThat(seen).containsExactly(0, 0);

        style.getState(16 + 5, 16 + 7);
        assertThat(seen).containsExactly(5, 7);

        // 负坐标也应折回 0-15 的局部坐标
        style.getState(-1, -16);
        assertThat(seen).containsExactly(15, 0);
    }

    @Test
    void constructorShouldDefensivelyCopyTileArray() {
        IFloorTile tileA = constantTile(STATE_A);
        IFloorTile tileB = constantTile(STATE_B);
        IFloorTile[] tiles = { tileA, tileB };
        FloorStyle style = new FloorStyle(2, 1, tiles);

        // 修改原数组不应影响已构建样式
        tiles[0] = tileB;

        assertThat(style.getState(0, 0)).isSameAs(STATE_A);
    }

    @Test
    void filledShouldUseSameTileEverywhere() {
        FloorStyle style = FloorStyle.filled(3, 2, constantTile(STATE_C));

        assertThat(style.getWidth()).isEqualTo(48);
        assertThat(style.getDepth()).isEqualTo(32);
        for (int x : new int[] { 0, 15, 16, 47, -1, 100 }) {
            assertThat(style.getState(x, 0)).isSameAs(STATE_C);
        }
    }

    @Test
    void builderSetShouldReplaceSingleChunk() {
        FloorStyle style = new FloorStyle.Builder(2, 2, constantTile(STATE_A))
                .set(1, 0, constantTile(STATE_B))
                .build();

        assertThat(style.getState(0, 0)).isSameAs(STATE_A);
        assertThat(style.getState(16, 0)).isSameAs(STATE_B);
        assertThat(style.getState(0, 16)).isSameAs(STATE_A);
        assertThat(style.getState(16, 16)).isSameAs(STATE_A);
    }

    @Test
    void builderFillRectShouldReplaceInclusiveRegion() {
        FloorStyle style = new FloorStyle.Builder(3, 3, constantTile(STATE_A))
                .fillRect(1, 1, 2, 2, constantTile(STATE_B))
                .build();

        assertThat(style.getState(0, 0)).isSameAs(STATE_A);
        for (int cx = 1; cx <= 2; cx++) {
            for (int cz = 1; cz <= 2; cz++) {
                assertThat(style.getState(cx * 16, cz * 16))
                        .as("chunk (%d, %d)", cx, cz)
                        .isSameAs(STATE_B);
            }
        }
    }

    @Test
    void builderShouldRejectInvalidInput() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle.Builder(0, 1, constantTile(STATE_A)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FloorStyle.Builder(1, 1, null));

        FloorStyle.Builder builder = new FloorStyle.Builder(2, 2, constantTile(STATE_A));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.set(2, 0, constantTile(STATE_B)));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.set(0, -1, constantTile(STATE_B)));
        assertThatIllegalArgumentException().isThrownBy(() -> builder.set(0, 0, null));
        assertThatThrownBy(() -> builder.fillRect(0, 0, 3, 0, constantTile(STATE_B)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builderShouldBeChainable() {
        FloorStyle.Builder builder = new FloorStyle.Builder(1, 1, constantTile(STATE_A));

        assertThat(builder.set(0, 0, constantTile(STATE_B))).isSameAs(builder);
        assertThat(builder.fillRect(0, 0, 0, 0, constantTile(STATE_C))).isSameAs(builder);
        assertThat(builder.build().getState(0, 0)).isSameAs(STATE_C);
    }

    @Test
    void getStateShouldReturnNullWhenTileReturnsNull() {
        FloorStyle style = FloorStyle.filled(1, 1, (x, z) -> null);

        assertThat(style.getState(0, 0)).isNull();
    }
}
