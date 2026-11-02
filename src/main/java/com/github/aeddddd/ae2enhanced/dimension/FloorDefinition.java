package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Arrays;

/** 每维度独立的地板样式、16 色角色映射与样式修订号。 */
public final class FloorDefinition {
    public static final String DEFAULT_STYLE = "default";
    public static final int COLOR_ROLES = 16;
    private final int[] colorScheme = new int[COLOR_ROLES];
    private String floorStyleId = DEFAULT_STYLE;
    private long revision;

    public FloorDefinition() {
        for (int i = 0; i < COLOR_ROLES; i++) {
            colorScheme[i] = i;
        }
    }

    public static int dyeMetadata(EnumDyeColor color) {
        return color == null ? 0 : color.getMetadata();
    }

    private static int[] sanitizeColors(int[] colors) {
        int[] result = new int[COLOR_ROLES];
        for (int i = 0; i < COLOR_ROLES; i++) {
            int value = colors != null && i < colors.length ? colors[i] : i;
            result[i] = value >= 0 && value < COLOR_ROLES ? value : i;
        }
        return result;
    }

    public String getFloorStyleId() {
        return floorStyleId;
    }

    public int[] getColorScheme() {
        return colorScheme.clone();
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long value) {
        revision = Math.max(0L, value);
    }

    public void setStyle(String styleId, int[] colors) {
        String normalized = FloorStyleRegistry.normalizeStyleId(styleId);
        int[] replacement = sanitizeColors(colors);
        boolean changed = !floorStyleId.equals(normalized) || !Arrays.equals(colorScheme, replacement);
        floorStyleId = normalized;
        System.arraycopy(replacement, 0, colorScheme, 0, COLOR_ROLES);
        if (changed) {
            revision++;
        }
    }

    public FloorDefinition copy() {
        FloorDefinition copy = new FloorDefinition();
        copy.floorStyleId = floorStyleId;
        System.arraycopy(colorScheme, 0, copy.colorScheme, 0, COLOR_ROLES);
        copy.revision = revision;
        return copy;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("floorStyleId", floorStyleId);
        tag.setIntArray("colorScheme", getColorScheme());
        tag.setLong("floorRevision", revision);
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("floorStyleId", 8)) {
            floorStyleId = FloorStyleRegistry.normalizeStyleId(tag.getString("floorStyleId"));
        }
        int[] colors = sanitizeColors(tag.getIntArray("colorScheme"));
        System.arraycopy(colors, 0, colorScheme, 0, COLOR_ROLES);
        revision = Math.max(0L, tag.getLong("floorRevision"));
    }
}
