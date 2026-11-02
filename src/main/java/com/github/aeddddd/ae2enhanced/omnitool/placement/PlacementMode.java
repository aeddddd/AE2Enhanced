package com.github.aeddddd.ae2enhanced.omnitool.placement;

public enum PlacementMode {
    SINGLE,
    BULK;

    public static PlacementMode fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return SINGLE;
        }
        return values()[ordinal];
    }
}
