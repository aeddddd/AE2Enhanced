package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.util.EnumFacing;

/**
 * Construction Wand 风格的方向锁。
 * 控制批量放置时在点击面所在平面上的扩展方向。
 */
public enum PlacementRestriction {
    NO_LOCK,
    HORIZONTAL,
    VERTICAL,
    NORTH_SOUTH,
    EAST_WEST;

    public static PlacementRestriction fromOrdinal(int ordinal) {
        PlacementRestriction[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return NO_LOCK;
        return values[ordinal];
    }

    public PlacementRestriction next() {
        PlacementRestriction[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * 判断给定方向是否被当前方向锁允许。
     *
     * @param dir 待判断方向（应为点击面法向的垂直方向）
     * @return 是否允许
     */
    public boolean allows(EnumFacing dir) {
        switch (this) {
            case HORIZONTAL:
                return dir.getYOffset() == 0;
            case VERTICAL:
                return dir.getYOffset() != 0;
            case NORTH_SOUTH:
                return dir.getAxis() == EnumFacing.Axis.Z;
            case EAST_WEST:
                return dir.getAxis() == EnumFacing.Axis.X;
            case NO_LOCK:
            default:
                return true;
        }
    }

    public String getNameKey() {
        return "gui.ae2enhanced.placement_restriction." + name().toLowerCase();
    }
}
