package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 加载个人维度地板预设。
 */
public final class PresetLoader {

    private PresetLoader() {}

    private static volatile FloorPreset cached;

    public static FloorPreset getPreset() {
        FloorPreset preset = cached;
        if (preset == null) {
            cached = load(ServerConfig.current().personalDimension.presetPath);
            preset = cached;
        }
        return preset;
    }

    /**
     * 将默认预设从 jar 内 assets 复制到 config 目录，方便用户修改。
     */
    public static void copyDefaultPresetToConfigIfMissing() {
        File configDir = net.minecraftforge.fml.common.Loader.instance().getConfigDir();
        File target = new File(configDir, ServerConfig.current().personalDimension.presetPath);
        if (target.isFile()) return;
        target.getParentFile().mkdirs();
        try (InputStream in = AE2Enhanced.class.getResourceAsStream("/assets/ae2enhanced/presets/personal_dimension_floor.json")) {
            if (in == null) {
                AE2Enhanced.LOGGER.warn("[AE2E] Default personal dimension preset not found in jar assets.");
                return;
            }
            java.nio.file.Files.copy(in, target.toPath());
            AE2Enhanced.LOGGER.info("[AE2E] Copied default personal dimension preset to {}", target.getAbsolutePath());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy default preset to config", e);
        }
    }

    public static void reload() {
        cached = null;
    }

    public static FloorPreset load(String path) {
        if (path == null || path.isEmpty()) {
            return fallback();
        }
        FloorPreset fromFile = loadFromFile(path);
        if (fromFile != null) return fromFile;
        FloorPreset fromAsset = loadFromAsset(path);
        if (fromAsset != null) return fromAsset;
        AE2Enhanced.LOGGER.warn("[AE2E] Failed to load personal dimension preset from {}, using fallback.", path);
        return fallback();
    }

    private static FloorPreset loadFromFile(String path) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                file = new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), path);
            }
            if (!file.isFile()) return null;
            try (InputStream in = new FileInputStream(file)) {
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
            if (in == null) return null;
            return parse(in);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to load preset asset {}", asset, e);
            return null;
        }
    }

    private static FloorPreset parse(InputStream in) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
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
            IBlockState[] palette = new IBlockState[map.size()];
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
                AE2Enhanced.LOGGER.warn("[AE2E] Preset statelist size {} does not match expected {}", list.size(), expectedSize);
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

    private static final java.util.Map<String, Integer> CONCRETE_COLORS = new java.util.HashMap<>();
    static {
        CONCRETE_COLORS.put("white", 0);
        CONCRETE_COLORS.put("orange", 1);
        CONCRETE_COLORS.put("magenta", 2);
        CONCRETE_COLORS.put("light_blue", 3);
        CONCRETE_COLORS.put("yellow", 4);
        CONCRETE_COLORS.put("lime", 5);
        CONCRETE_COLORS.put("pink", 6);
        CONCRETE_COLORS.put("gray", 7);
        CONCRETE_COLORS.put("silver", 8);
        CONCRETE_COLORS.put("light_gray", 8);
        CONCRETE_COLORS.put("cyan", 9);
        CONCRETE_COLORS.put("purple", 10);
        CONCRETE_COLORS.put("blue", 11);
        CONCRETE_COLORS.put("brown", 12);
        CONCRETE_COLORS.put("green", 13);
        CONCRETE_COLORS.put("red", 14);
        CONCRETE_COLORS.put("black", 15);
    }

    private static IBlockState resolveState(String name) {
        // GTCEu 未加载时映射到本 Mod 的替代方块
        if ("gtceu:yellow_stripes_block_b".equals(name)) {
            if (Block.getBlockFromName("ae2enhanced:yellow_stripes_block_b") != null) {
                return Block.getBlockFromName("ae2enhanced:yellow_stripes_block_b").getDefaultState();
            }
        }
        // 1.13+ 的彩色混凝土名称在 1.12 中对应 minecraft:concrete 的 metadata
        if (name.startsWith("minecraft:") && name.endsWith("_concrete")) {
            String color = name.substring("minecraft:".length(), name.length() - "_concrete".length());
            Integer meta = CONCRETE_COLORS.get(color);
            if (meta != null) {
                Block concrete = Block.REGISTRY.getObject(new ResourceLocation("minecraft:concrete"));
                if (concrete != null) {
                    return concrete.getStateFromMeta(meta);
                }
            }
        }
        ResourceLocation rl = new ResourceLocation(name);
        Block block = Block.REGISTRY.getObject(rl);
        if (block != null) {
            return block.getDefaultState();
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Preset block {} not found, falling back to bedrock.", name);
        return Blocks.BEDROCK.getDefaultState();
    }

    private static FloorPreset fallback() {
        IBlockState[] palette = new IBlockState[] {
                Block.getBlockFromName("ae2enhanced:yellow_stripes_block_b") != null
                        ? Block.getBlockFromName("ae2enhanced:yellow_stripes_block_b").getDefaultState()
                        : Blocks.BEDROCK.getDefaultState()
        };
        int[] states = new int[96 * 96];
        return new FloorPreset(96, 96, palette, states);
    }
}
