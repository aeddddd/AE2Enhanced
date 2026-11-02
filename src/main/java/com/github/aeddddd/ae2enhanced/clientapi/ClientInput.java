package com.github.aeddddd.ae2enhanced.clientapi;

import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/** 公共物品提示与客户端输入之间的中性端口。 */
public final class ClientInput {

    private static BooleanSupplier select = () -> false;
    private static BooleanSupplier clear = () -> false;
    private static BooleanSupplier tooltipDetail = () -> false;
    private static IntFunction<String> displayName = key -> "";

    private ClientInput() {
    }

    public static void register(BooleanSupplier select, BooleanSupplier clear,
            BooleanSupplier tooltipDetail, IntFunction<String> displayName) {
        ClientInput.select = select;
        ClientInput.clear = clear;
        ClientInput.tooltipDetail = tooltipDetail;
        ClientInput.displayName = displayName;
    }

    public static boolean selectDown() {
        return select.getAsBoolean();
    }

    public static boolean clearDown() {
        return clear.getAsBoolean();
    }

    public static boolean tooltipDetailDown() {
        return tooltipDetail.getAsBoolean();
    }

    public static String displayName(int key) {
        return displayName.apply(key);
    }
}
