package com.github.aeddddd.ae2enhanced.dimension;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.api.dimension.PersonalDimensionApi;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

/**
 * 加载个人维度地板预设.
 *
 * <p>预设 JSON 格式与 1.12 版本一致（startpos/endpos/blockstatemap/statelist）;
 * 1.20 中方块名直接按注册表解析,彩色混凝土等 1.13+ 扁平化名称可原生使用.
 * 配置值也可以是 {@link PersonalDimensionApi} 注册的命名样式 id（如 {@code ae2enhanced:default}）.</p>
 */
public final class PresetLoader {

    private PresetLoader() {
    }

    private static volatile IFloorPreset cached;

    public static IFloorPreset getPreset() {
        IFloorPreset preset = cached;
        if (preset == null) {
            cached = load(AE2EnhancedConfig.COMMON.personalDimensionPresetPath.get());
            preset = cached;
        }
        return preset;
    }

    /**
     * 将默认预设从 jar 内 assets 复制到 config 目录,作为自定义文件示例;
     * 目标文件名固定,与 presetPath 解耦(后者可为样式 id 或其他路径).
     */
    public static void copyDefaultPresetToConfigIfMissing() {
        Path target;
        try {
            target = FMLPaths.CONFIGDIR.get().resolve(EXAMPLE_PRESET_PATH);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to resolve example preset path", e);
            return;
        }
        if (Files.isRegularFile(target)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = AE2Enhanced.class
                    .getResourceAsStream("/assets/ae2enhanced/presets/personal_dimension_floor.json")) {
                if (in == null) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Default personal dimension preset not found in jar assets.");
                    return;
                }
                Files.copy(in, target);
                AE2Enhanced.LOGGER.info("[AE2E] Copied default personal dimension preset to {}", target);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy default preset to config", e);
        }
    }

    public static void reload() {
        cached = null;
    }

    /**
     * 注册内置地板样式(马路/平台区块单元与默认组合样式)到 API 注册表,
     * 供 config 以命名样式 id 引用;其他模组已占用对应 id 时不覆盖.
     */
    public static void registerBuiltinDefault() {
        ModFloorStyles.register();
    }

    /** 旧版默认 presetPath 值(文件路径形式). */
    private static final String LEGACY_DEFAULT_PATH = "ae2enhanced/personal_dimension_floor.json";

    /** 复制到 config 目录的示例预设文件名. */
    private static final String EXAMPLE_PRESET_PATH = "ae2enhanced/personal_dimension_floor.json";

    /** 新版默认 presetPath 值(内置组合样式 id). */
    private static final String DEFAULT_STYLE_ID = "ae2enhanced:default";

    /**
     * 一次性迁移旧配置:
     * <ul>
     * <li>presetPath 仍为旧默认文件路径时,切换为内置组合样式 id;
     * <li>第一轮自动复制出去的 16×16 棋盘格 JSON 若未被用户修改,更新为主分支 96×96 版本.
     * </ul>
     */
    public static void migrateLegacyPresetIfNeeded() {
        var configValue = AE2EnhancedConfig.COMMON.personalDimensionPresetPath;
        if (LEGACY_DEFAULT_PATH.equals(configValue.get())) {
            configValue.set(DEFAULT_STYLE_ID);
            AE2Enhanced.LOGGER.info("[AE2E] Migrated personal dimension presetPath to {}.", DEFAULT_STYLE_ID);
        }
        Path legacyFile = FMLPaths.CONFIGDIR.get().resolve(LEGACY_DEFAULT_PATH);
        if (Files.isRegularFile(legacyFile) && isLegacyAutoCopiedPreset(legacyFile)) {
            try (InputStream in = AE2Enhanced.class
                    .getResourceAsStream("/assets/ae2enhanced/presets/personal_dimension_floor.json")) {
                if (in != null) {
                    Files.copy(in, legacyFile, StandardCopyOption.REPLACE_EXISTING);
                    AE2Enhanced.LOGGER.info("[AE2E] Updated legacy auto-copied floor preset file {}.", legacyFile);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to update legacy floor preset file {}", legacyFile, e);
            }
        }
        reload();
    }

    /**
     * 判定文件是否为第一轮自动复制且未被修改的 16×16 黄黑棋盘格预设.
     */
    private static boolean isLegacyAutoCopiedPreset(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            FloorPreset preset = parse(in);
            if (preset == null || preset.width != 16 || preset.depth != 16 || preset.palette.length != 2) {
                return false;
            }
            boolean hasYellow = false;
            boolean hasBlack = false;
            for (BlockState state : preset.palette) {
                String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                hasYellow |= name.equals("minecraft:yellow_concrete");
                hasBlack |= name.equals("minecraft:black_concrete");
            }
            return hasYellow && hasBlack;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 加载 jar 内置的主分支 96×96 地板预设(assets/ae2enhanced/presets/personal_dimension_floor.json).
     */
    @Nullable
    public static FloorPreset loadBuiltinAsset() {
        return loadFromAsset("/assets/ae2enhanced/presets/personal_dimension_floor.json");
    }

    public static IFloorPreset load(String path) {
        if (path == null || path.isEmpty()) {
            return fallback();
        }
        FloorPreset fromFile = loadFromFile(path);
        if (fromFile != null) {
            return fromFile;
        }
        IFloorPreset fromRegistry = loadFromRegistry(path);
        if (fromRegistry != null) {
            return fromRegistry;
        }
        FloorPreset fromAsset = loadFromAsset(path);
        if (fromAsset != null) {
            return fromAsset;
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Failed to load personal dimension preset from {}, using fallback.", path);
        return fallback();
    }

    private static IFloorPreset loadFromRegistry(String path) {
        ResourceLocation id = ResourceLocation.tryParse(path);
        if (id == null) {
            return null;
        }
        return PersonalDimensionApi.getFloorPreset(id).orElse(null);
    }

    private static FloorPreset loadFromFile(String path) {
        try {
            Path file = Path.of(path);
            if (!Files.isRegularFile(file)) {
                file = FMLPaths.CONFIGDIR.get().resolve(path);
            }
            if (!Files.isRegularFile(file)) {
                return null;
            }
            try (InputStream in = Files.newInputStream(file)) {
                return parse(in);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to load preset file {}", path, e);
            return null;
        }
    }

    private static FloorPreset loadFromAsset(String path) {
        String asset = path;
        if (!asset.startsWith("/")) {
            asset = "/" + asset;
        }
        try (InputStream in = AE2Enhanced.class.getResourceAsStream(asset)) {
            if (in == null) {
                return null;
            }
            return parse(in);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to load preset asset {}", asset, e);
            return null;
        }
    }

    private static FloorPreset parse(InputStream in) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                AE2Enhanced.LOGGER.warn("[AE2E] Preset root is not a JSON object");
                return null;
            }
            JsonObject root = parsed.getAsJsonObject();

            JsonObject start = root.getAsJsonObject("startpos");
            JsonObject end = root.getAsJsonObject("endpos");
            if (start == null || end == null
                    || !start.has("X") || !start.has("Z")
                    || !end.has("X") || !end.has("Z")) {
                AE2Enhanced.LOGGER.warn("[AE2E] Preset missing startpos/endpos or X/Z keys");
                return null;
            }
            int startX = start.get("X").getAsInt();
            int startZ = start.get("Z").getAsInt();
            int endX = end.get("X").getAsInt();
            int endZ = end.get("Z").getAsInt();
            int width = endX - startX + 1;
            int depth = endZ - startZ + 1;
            if (width <= 0 || depth <= 0) {
                AE2Enhanced.LOGGER.warn("[AE2E] Preset has invalid size: width={}, depth={}", width, depth);
                return null;
            }

            JsonArray map = root.getAsJsonArray("blockstatemap");
            if (map == null || map.size() == 0) {
                AE2Enhanced.LOGGER.warn("[AE2E] Preset missing blockstatemap");
                return null;
            }
            BlockState[] palette = new BlockState[map.size()];
            for (int i = 0; i < map.size(); i++) {
                JsonElement entryElement = map.get(i);
                if (!entryElement.isJsonObject()) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Preset blockstatemap entry {} is not an object", i);
                    return null;
                }
                JsonObject entry = entryElement.getAsJsonObject();
                if (!entry.has("Name")) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Preset blockstatemap entry {} missing Name", i);
                    return null;
                }
                String name = entry.get("Name").getAsString();
                palette[i] = resolveState(name);
            }

            JsonArray list = root.getAsJsonArray("statelist");
            if (list == null) {
                AE2Enhanced.LOGGER.warn("[AE2E] Preset missing statelist");
                return null;
            }
            int expectedSize = width * depth;
            if (list.size() != expectedSize) {
                AE2Enhanced.LOGGER.warn("[AE2E] Preset statelist size {} does not match expected {}", list.size(),
                        expectedSize);
                return null;
            }
            int[] states = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                int stateIndex = list.get(i).getAsInt();
                if (stateIndex < 0 || stateIndex >= palette.length) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Preset statelist index {} out of palette bounds", stateIndex);
                    return null;
                }
                states[i] = stateIndex;
            }

            return new FloorPreset(width, depth, palette, states);
        } catch (JsonSyntaxException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to parse preset JSON syntax", e);
            return null;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to parse preset", e);
            return null;
        }
    }

    private static BlockState resolveState(String name) {
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
            return BuiltInRegistries.BLOCK.get(rl).defaultBlockState();
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Preset block {} not found, falling back to bedrock.", name);
        return Blocks.BEDROCK.defaultBlockState();
    }

    private static FloorPreset fallback() {
        // 对齐 1.12 主分支回退预设:96×96 全警示方块
        BlockState[] palette = new BlockState[] { ModBlocks.YELLOW_STRIPES_BLOCK_B.get().defaultBlockState() };
        int[] states = new int[96 * 96];
        return new FloorPreset(96, 96, palette, states);
    }
}
