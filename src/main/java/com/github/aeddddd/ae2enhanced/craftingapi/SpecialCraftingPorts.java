package com.github.aeddddd.ae2enhanced.craftingapi;

import java.util.ServiceLoader;

/** 特殊计划端口的懒加载定位器。 */
public final class SpecialCraftingPorts {

    private static volatile SpecialCraftingPort delegate;

    private SpecialCraftingPorts() {
    }

    public static SpecialCraftingPort get() {
        SpecialCraftingPort result = delegate;
        if (result == null) {
            synchronized (SpecialCraftingPorts.class) {
                result = delegate;
                if (result == null) {
                    for (SpecialCraftingPort candidate : ServiceLoader.load(SpecialCraftingPort.class)) {
                        result = candidate;
                        break;
                    }
                    if (result == null) {
                        throw new IllegalStateException("No SpecialCraftingPort implementation");
                    }
                    delegate = result;
                }
            }
        }
        return result;
    }
}
