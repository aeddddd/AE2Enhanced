package com.github.aeddddd.ae2enhanced.clientapi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** 不引用 DTO 或客户端实现的客户端包执行端点。 */
public final class ClientPacketHandlers {

    public static final String CHAMBER_SYNC = "chamberSync";
    public static final String CHUNK_POWER_HIGHLIGHT = "chunkPowerHighlight";
    public static final String DIMENSION_RULES = "dimensionRules";
    public static final String SPECIAL_PLAN = "specialPlan";
    public static final String DISPLAY_TICK = "displayTick";
    public static final String GUIDE_SYNC = "guideSync";
    public static final String DIMENSION_INFO = "dimensionInfo";
    public static final String DIMENSION_FLOOR = "dimensionFloor";

    private static final Map<String, Consumer<Object>> HANDLERS = new ConcurrentHashMap<>();

    private ClientPacketHandlers() {
    }

    public static void register(String key, Consumer<Object> handler) {
        HANDLERS.put(key, handler);
    }

    public static void accept(String key, Object message) {
        Consumer<Object> handler = HANDLERS.get(key);
        if (handler != null) {
            handler.accept(message);
        }
    }
}
