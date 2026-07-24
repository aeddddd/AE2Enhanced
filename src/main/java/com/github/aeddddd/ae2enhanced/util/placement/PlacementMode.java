package com.github.aeddddd.ae2enhanced.util.placement;

public enum PlacementMode {
    SINGLE,
    BULK,
    CABLE;

    public static PlacementMode fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return SINGLE;
        }
        return values()[ordinal];
    }
}
