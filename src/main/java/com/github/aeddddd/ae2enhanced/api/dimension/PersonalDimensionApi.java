package com.github.aeddddd.ae2enhanced.api.dimension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 个人维度对外 API:地板样式(世界生成样式)注册入口.
 *
 * <p>样式以 16×16 的区块单元({@link IFloorTile})为最小单元,由
 * {@link com.github.aeddddd.ae2enhanced.dimension.FloorStyle} 按区块网格拼合;
 * 其他模组在 common setup 阶段调用 {@link #registerFloorTile}/{@link #registerFloorPreset}
 * 注册命名单元与样式.用户在 config 的 {@code personalDimension.presetPath} 中填写
 * {@code "namespace:path"} 即可选用.本模组内置马路({@link #ROAD_TILE_ID})与
 * 平台({@link #PLATFORM_TILE_ID})两种单元,默认组合样式注册为 {@link #DEFAULT_PRESET_ID}.</p>
 */
public final class PersonalDimensionApi {

    /** 内置默认地板样式的注册 id(马路+平台组合样式). */
    public static final ResourceLocation DEFAULT_PRESET_ID = new ResourceLocation(AE2Enhanced.MOD_ID, "default");

    /** 内置马路区块单元的注册 id. */
    public static final ResourceLocation ROAD_TILE_ID = new ResourceLocation(AE2Enhanced.MOD_ID, "road");

    /** 内置平台区块单元的注册 id. */
    public static final ResourceLocation PLATFORM_TILE_ID = new ResourceLocation(AE2Enhanced.MOD_ID, "platform");

    private static final Map<ResourceLocation, IFloorPreset> FLOOR_PRESETS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, IFloorTile> FLOOR_TILES = new LinkedHashMap<>();

    private PersonalDimensionApi() {
    }

    /**
     * 注册命名区块单元.重复 id 将覆盖旧值并记录警告.
     */
    public static synchronized void registerFloorTile(ResourceLocation id, IFloorTile tile) {
        if (id == null || tile == null) {
            throw new IllegalArgumentException("Floor tile id and tile must not be null");
        }
        if (FLOOR_TILES.put(id, tile) != null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Floor tile {} was overridden by another registration.", id);
        }
    }

    /**
     * 按 id 查询已注册的区块单元.
     */
    public static synchronized Optional<IFloorTile> getFloorTile(ResourceLocation id) {
        return Optional.ofNullable(FLOOR_TILES.get(id));
    }

    /**
     * 已注册区块单元的只读视图.
     */
    public static synchronized Map<ResourceLocation, IFloorTile> getRegisteredFloorTiles() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(FLOOR_TILES));
    }

    /**
     * 注册命名地板样式.重复 id 将覆盖旧值并记录警告.
     */
    public static synchronized void registerFloorPreset(ResourceLocation id, IFloorPreset preset) {
        if (id == null || preset == null) {
            throw new IllegalArgumentException("Floor preset id and preset must not be null");
        }
        if (preset.getWidth() <= 0 || preset.getDepth() <= 0) {
            throw new IllegalArgumentException("Floor preset size must be positive: " + id);
        }
        if (FLOOR_PRESETS.put(id, preset) != null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Floor preset {} was overridden by another registration.", id);
        }
    }

    /**
     * 按 id 查询已注册的地板样式.
     */
    public static synchronized Optional<IFloorPreset> getFloorPreset(ResourceLocation id) {
        return Optional.ofNullable(FLOOR_PRESETS.get(id));
    }

    /**
     * 已注册地板样式的只读视图.
     */
    public static synchronized Map<ResourceLocation, IFloorPreset> getRegisteredFloorPresets() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(FLOOR_PRESETS));
    }
}
