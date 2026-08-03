package com.github.aeddddd.ae2enhanced.test.dimension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PresetLoader} 单元测试.
 *
 * <p>说明: 测试环境中本模组方块未注册,内置 asset 中的 {@code ae2enhanced:caution_block}
 * 会按 resolveState 逻辑回退为基岩;同理 load() 的最终回退预设(fallback())依赖
 * 已注册的警示方块,相关失败路径不在本测试覆盖范围内.</p>
 */
class PresetLoaderTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private Path writePreset(String fileName, String json) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, json);
        return file;
    }

    @Test
    void loadBuiltinAssetShouldReturn96x96Preset() {
        FloorPreset preset = PresetLoader.loadBuiltinAsset();

        assertThat(preset).isNotNull();
        assertThat(preset.width).isEqualTo(96);
        assertThat(preset.depth).isEqualTo(96);
        assertThat(preset.stateList).hasSize(96 * 96);
        // 调色板: 警示方块(测试环境回退为基岩) + 灰/白/黑混凝土
        assertThat(preset.palette).hasSize(4);
        assertThat(preset.palette).contains(
                Blocks.BEDROCK.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                Blocks.BLACK_CONCRETE.defaultBlockState());
    }

    @Test
    void loadBuiltinAssetShouldResolveVanillaBlocks() {
        FloorPreset preset = PresetLoader.loadBuiltinAsset();

        // asset 第 0 行: 0,0,0,1,1,1,1,2,...(索引见 blockstatemap)
        // 测试环境 palette[0]=基岩(caution 回退), palette[1]=灰混凝土, palette[2]=白混凝土
        assertThat(preset.getState(0, 0)).isEqualTo(Blocks.BEDROCK.defaultBlockState());
        assertThat(preset.getState(3, 0)).isEqualTo(Blocks.GRAY_CONCRETE.defaultBlockState());
        assertThat(preset.getState(7, 0)).isEqualTo(Blocks.WHITE_CONCRETE.defaultBlockState());
    }

    @Test
    void loadShouldParsePresetFromAbsoluteFilePath() throws IOException {
        String json = """
                {
                  "startpos": {"X": 0, "Y": 0, "Z": 0},
                  "endpos": {"X": 1, "Y": 0, "Z": 1},
                  "blockstatemap": [
                    {"Name": "minecraft:stone"},
                    {"Name": "minecraft:dirt"}
                  ],
                  "statelist": [0, 1, 1, 0]
                }
                """;
        Path file = writePreset("custom.json", json);

        IFloorPreset loaded = PresetLoader.load(file.toString());

        assertThat(loaded).isInstanceOf(FloorPreset.class);
        assertThat(loaded.getWidth()).isEqualTo(2);
        assertThat(loaded.getDepth()).isEqualTo(2);
        assertThat(loaded.getState(0, 0)).isEqualTo(Blocks.STONE.defaultBlockState());
        assertThat(loaded.getState(1, 0)).isEqualTo(Blocks.DIRT.defaultBlockState());
        assertThat(loaded.getState(0, 1)).isEqualTo(Blocks.DIRT.defaultBlockState());
        assertThat(loaded.getState(1, 1)).isEqualTo(Blocks.STONE.defaultBlockState());
    }

    @Test
    void loadShouldSupportAssetStylePath() {
        // 以 jar 内 asset 路径加载内置预设
        IFloorPreset loaded = PresetLoader.load("/assets/ae2enhanced/presets/personal_dimension_floor.json");

        assertThat(loaded).isNotNull();
        assertThat(loaded.getWidth()).isEqualTo(96);
        assertThat(loaded.getDepth()).isEqualTo(96);
    }

    @Test
    void loadShouldFallbackToBedrockForUnknownBlockInFile() throws IOException {
        String json = """
                {
                  "startpos": {"X": 0, "Y": 0, "Z": 0},
                  "endpos": {"X": 0, "Y": 0, "Z": 0},
                  "blockstatemap": [
                    {"Name": "minecraft:no_such_block"}
                  ],
                  "statelist": [0]
                }
                """;
        Path file = writePreset("unknown_block.json", json);

        IFloorPreset loaded = PresetLoader.load(file.toString());

        // 文件解析成功,未知方块回退为基岩
        assertThat(loaded).isNotNull();
        assertThat(loaded.getState(0, 0)).isEqualTo(Blocks.BEDROCK.defaultBlockState());
    }
}
