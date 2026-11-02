package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 命名地板样式注册表；旧 presetPath 始终作为 default 样式保留。 */
public final class FloorStyleRegistry {
    private static final Map<String, FloorPreset> STYLES = new ConcurrentHashMap<>();

    private FloorStyleRegistry() {
    }

    public static void register(String styleId, FloorPreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("Floor preset must not be null");
        }
        String normalized = normalizeStyleId(styleId);
        if (FloorDefinition.DEFAULT_STYLE.equals(normalized)) {
            throw new IllegalArgumentException("default floor style is reserved for presetPath compatibility");
        }
        STYLES.put(normalized, preset);
    }

    public static FloorPreset resolve(FloorDefinition definition) {
        FloorDefinition actual = definition == null ? new FloorDefinition() : definition;
        FloorPreset base = FloorDefinition.DEFAULT_STYLE.equals(actual.getFloorStyleId())
                ? PresetLoader.getPreset() : STYLES.get(actual.getFloorStyleId());
        if (base == null) {
            base = PresetLoader.getPreset();
        }
        return recolor(base, actual.getColorScheme());
    }

    public static String normalizeStyleId(String styleId) {
        if (styleId == null || styleId.trim().isEmpty()) {
            return FloorDefinition.DEFAULT_STYLE;
        }
        String normalized = styleId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid floor style id: " + styleId);
        }
        return normalized;
    }

    private static FloorPreset recolor(FloorPreset base, int[] colors) {
        IBlockState[] palette = base.palette.clone();
        for (int i = 0; i < palette.length; i++) {
            IBlockState state = palette[i];
            if (state != null && state.getBlock() == Blocks.CONCRETE) {
                int original = state.getBlock().getMetaFromState(state);
                int replacement = original >= 0 && original < colors.length ? colors[original] : original;
                palette[i] = Blocks.CONCRETE.getStateFromMeta(replacement);
            }
        }
        return new FloorPreset(base.width, base.depth, palette, base.stateList.clone());
    }
}
