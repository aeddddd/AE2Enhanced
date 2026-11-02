package com.github.aeddddd.ae2enhanced.client.gui.dock;

public final class Ae2Theme {
    public static final int BUTTON_HEIGHT = 20;
    public static final int FIELD_HEIGHT = 12;
    public static final int FIELD_PADDING = 2;
    public static final int SLOT_SIZE = 18;
    public static final int ICON_SIZE = 16;

    public static final int TEXT = 0xFFF2F2F2;
    public static final int TEXT_HOVER = 0xFF517497;
    public static final int TEXT_DISABLED = 0xFF413F54;
    public static final int TITLE = 0xFF413F54;
    public static final int MUTED = 0xFF878FA5;
    public static final int SELECTION = 0xFFACE9FF;

    private Ae2Theme() {
    }

    public static int buttonTextColor(boolean enabled, boolean hovered) {
        if (!enabled) {
            return TEXT_DISABLED;
        }
        return hovered ? TEXT_HOVER : TEXT;
    }
}
