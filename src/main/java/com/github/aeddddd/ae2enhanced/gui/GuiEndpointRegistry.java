package com.github.aeddddd.ae2enhanced.gui;

import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;

import java.util.EnumMap;
import java.util.Map;

/** 由两侧 proxy 分别安装 endpoint；公共路由不引用客户端实现。 */
public final class GuiEndpointRegistry {
    private static final Map<Side, IGuiHandler> ENDPOINTS = new EnumMap<Side, IGuiHandler>(Side.class);

    private GuiEndpointRegistry() {
    }

    public static synchronized void register(Side side, IGuiHandler endpoint) {
        if (side == null || endpoint == null) {
            throw new NullPointerException("GUI endpoint and side must not be null");
        }
        ENDPOINTS.put(side, endpoint);
    }

    public static synchronized IGuiHandler endpoint(Side side) {
        return ENDPOINTS.get(side);
    }
}
