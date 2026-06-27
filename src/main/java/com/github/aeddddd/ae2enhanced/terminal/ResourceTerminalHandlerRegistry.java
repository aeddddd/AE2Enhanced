package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.storage.data.IAEItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 资源终端处理器注册表.
 */
public final class ResourceTerminalHandlerRegistry {

    private static final List<IResourceTerminalHandler> HANDLERS = new ArrayList<>();

    static {
        register(new EnergyTerminalHandler());
        register(new ManaTerminalHandler());
        register(new StarlightTerminalHandler());
    }

    private ResourceTerminalHandlerRegistry() {}

    public static void register(IResourceTerminalHandler handler) {
        if (handler != null) {
            HANDLERS.add(handler);
        }
    }

    public static List<IResourceTerminalHandler> getHandlers() {
        return Collections.unmodifiableList(HANDLERS);
    }

    /**
     * 查找能处理该 AE 堆叠的处理器.
     */
    public static IResourceTerminalHandler findForStack(IAEItemStack aeStack) {
        if (aeStack == null) return null;
        for (IResourceTerminalHandler handler : HANDLERS) {
            if (handler.isResourceStack(aeStack)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 查找能处理该手持物品的处理器（数据包物品或容器）.
     */
    public static IResourceTerminalHandler findForHeldItem(net.minecraft.item.ItemStack held) {
        if (held == null || held.isEmpty()) return null;
        for (IResourceTerminalHandler handler : HANDLERS) {
            if (handler.isPacketItem(held) || handler.isContainer(held)) {
                return handler;
            }
        }
        return null;
    }
}
