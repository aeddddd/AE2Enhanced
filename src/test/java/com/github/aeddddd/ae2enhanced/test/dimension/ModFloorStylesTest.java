package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorTile;
import com.github.aeddddd.ae2enhanced.api.dimension.PersonalDimensionApi;
import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.FloorStyle;
import com.github.aeddddd.ae2enhanced.dimension.ModFloorStyles;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModFloorStyles} 与 {@link PersonalDimensionApi} 注册表单元测试.
 *
 * <p>测试环境内置 asset 可用,register() 走"切片内置预设"分支而非兜底分支,
 * 因此不会触碰未注册的警示方块.</p>
 */
class ModFloorStylesTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
        ModFloorStyles.register();
    }

    @Test
    void registerShouldPublishBuiltinTilesAndPreset() {
        assertThat(PersonalDimensionApi.getFloorTile(PersonalDimensionApi.ROAD_TILE_ID)).isPresent();
        assertThat(PersonalDimensionApi.getFloorTile(PersonalDimensionApi.PLATFORM_TILE_ID)).isPresent();
        assertThat(PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID)).isPresent();

        assertThat(PersonalDimensionApi.getRegisteredFloorTiles())
                .containsKey(PersonalDimensionApi.ROAD_TILE_ID)
                .containsKey(PersonalDimensionApi.PLATFORM_TILE_ID);
        assertThat(PersonalDimensionApi.getRegisteredFloorPresets())
                .containsKey(PersonalDimensionApi.DEFAULT_PRESET_ID);
    }

    @Test
    void builtinTilesShouldBe16x16() {
        IFloorTile road = PersonalDimensionApi.getFloorTile(PersonalDimensionApi.ROAD_TILE_ID).orElseThrow();
        IFloorTile platform = PersonalDimensionApi.getFloorTile(PersonalDimensionApi.PLATFORM_TILE_ID)
                .orElseThrow();

        // 单元来自 96×96 预设切片,均为 16×16 FloorPreset
        assertThat(road).isInstanceOf(FloorPreset.class);
        assertThat(platform).isInstanceOf(FloorPreset.class);
        assertThat(((FloorPreset) road).width).isEqualTo(IFloorTile.SIZE);
        assertThat(((FloorPreset) road).depth).isEqualTo(IFloorTile.SIZE);
        assertThat(((FloorPreset) platform).width).isEqualTo(IFloorTile.SIZE);
        assertThat(((FloorPreset) platform).depth).isEqualTo(IFloorTile.SIZE);
    }

    @Test
    void defaultPresetShouldBe96x96FloorStyle() {
        IFloorPreset preset = PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID)
                .orElseThrow();

        assertThat(preset).isInstanceOf(FloorStyle.class);
        assertThat(preset.getWidth()).isEqualTo(ModFloorStyles.GRID_SIZE * IFloorTile.SIZE);
        assertThat(preset.getDepth()).isEqualTo(ModFloorStyles.GRID_SIZE * IFloorTile.SIZE);
    }

    @Test
    void defaultPresetShouldMatchBuiltinAssetSampling() {
        // 组合样式由内置 96×96 预设切片而来,逐格采样应与原预设一致
        FloorPreset source = PresetLoader.loadBuiltinAsset();
        assertThat(source).isNotNull();
        IFloorPreset preset = PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID)
                .orElseThrow();

        int[][] samplePoints = {
                { 0, 0 }, { 3, 0 }, { 7, 0 }, { 16, 16 }, { 47, 47 }, { 95, 95 }, { 33, 80 }, { 80, 33 }
        };
        for (int[] point : samplePoints) {
            assertThat(preset.getState(point[0], point[1]))
                    .as("state at (%d, %d)", point[0], point[1])
                    .isEqualTo(source.getState(point[0], point[1]));
        }
    }

    @Test
    void builtinTilesShouldMatchCorrespondingSourceChunks() {
        // 马路单元来自源预设的区块 (0,2),平台单元来自区块 (1,1)
        FloorPreset source = PresetLoader.loadBuiltinAsset();
        assertThat(source).isNotNull();
        IFloorTile road = PersonalDimensionApi.getFloorTile(PersonalDimensionApi.ROAD_TILE_ID).orElseThrow();
        IFloorTile platform = PersonalDimensionApi.getFloorTile(PersonalDimensionApi.PLATFORM_TILE_ID)
                .orElseThrow();

        int[][] localPoints = { { 0, 0 }, { 7, 7 }, { 15, 15 }, { 3, 12 } };
        for (int[] p : localPoints) {
            assertThat(road.getState(p[0], p[1]))
                    .as("road tile at (%d, %d)", p[0], p[1])
                    .isEqualTo(source.getState(p[0], 32 + p[1]));
            assertThat(platform.getState(p[0], p[1]))
                    .as("platform tile at (%d, %d)", p[0], p[1])
                    .isEqualTo(source.getState(16 + p[0], 16 + p[1]));
        }
        // 平台单元(1,1) 区块在源预设中为白色地砖(局部 (0,0))并含黑色点缀(局部 (7,7))
        assertThat(platform.getState(0, 0)).isEqualTo(Blocks.WHITE_CONCRETE.defaultBlockState());
        assertThat(platform.getState(7, 7)).isEqualTo(Blocks.BLACK_CONCRETE.defaultBlockState());
    }

    @Test
    void registerShouldNotOverrideExistingRegistrations() {
        IFloorTile roadBefore = PersonalDimensionApi.getFloorTile(PersonalDimensionApi.ROAD_TILE_ID)
                .orElseThrow();
        IFloorPreset presetBefore = PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID)
                .orElseThrow();

        // 重复注册不覆盖已有 id
        ModFloorStyles.register();

        assertThat(PersonalDimensionApi.getFloorTile(PersonalDimensionApi.ROAD_TILE_ID).orElseThrow())
                .isSameAs(roadBefore);
        assertThat(PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID).orElseThrow())
                .isSameAs(presetBefore);
    }

    @Test
    void registerBuiltinDefaultShouldDelegateToModFloorStyles() {
        // 幂等路径,不应抛异常
        PresetLoader.registerBuiltinDefault();

        assertThat(PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID)).isPresent();
    }

    @Test
    void loadShouldResolveRegisteredStyleId() {
        IFloorPreset expected = PersonalDimensionApi.getFloorPreset(PersonalDimensionApi.DEFAULT_PRESET_ID)
                .orElseThrow();

        // 命名样式 id 走注册表分支
        IFloorPreset loaded = PresetLoader.load("ae2enhanced:default");

        assertThat(loaded).isSameAs(expected);
    }

    @Test
    void apiShouldRejectInvalidRegistrations() {
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> PersonalDimensionApi.registerFloorTile(null, (x, z) -> null));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> PersonalDimensionApi
                        .registerFloorTile(new net.minecraft.resources.ResourceLocation("ae2enhanced", "bad"), null));

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> PersonalDimensionApi.registerFloorPreset(null, FloorStyle.filled(1, 1,
                        (x, z) -> Blocks.STONE.defaultBlockState())));
        // 非正尺寸样式被拒绝
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> PersonalDimensionApi.registerFloorPreset(
                        new net.minecraft.resources.ResourceLocation("ae2enhanced", "bad"),
                        new FloorPreset(0, 0, null, null)));
    }

    @Test
    void apiRegistryViewsShouldBeReadOnly() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PersonalDimensionApi.getRegisteredFloorTiles()
                .put(new net.minecraft.resources.ResourceLocation("ae2enhanced", "x"), (x, z) -> null))
                .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PersonalDimensionApi.getRegisteredFloorPresets()
                .put(new net.minecraft.resources.ResourceLocation("ae2enhanced", "x"),
                        FloorStyle.filled(1, 1, (x, z) -> null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
