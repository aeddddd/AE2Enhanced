package com.github.aeddddd.ae2enhanced.api;

import appeng.api.networking.security.IActionHost;

import java.util.EnumMap;
import java.util.Map;

/** 公共网络资源 API 的出站端口；存储层注册具体通道实现。 */
public final class NetworkResourcePort {

    private static final Map<Kind, Handler> HANDLERS = new EnumMap<>(Kind.class);

    private NetworkResourcePort() {
    }

    public static synchronized void register(Kind kind, Handler handler) {
        HANDLERS.put(kind, handler);
    }

    public static long extract(Kind kind, IActionHost host, long amount, boolean simulate) {
        Handler handler;
        synchronized (NetworkResourcePort.class) {
            handler = HANDLERS.get(kind);
        }
        return handler == null ? 0L : handler.extract(host, amount, simulate);
    }

    public static long insert(Kind kind, IActionHost host, long amount, boolean simulate) {
        Handler handler;
        synchronized (NetworkResourcePort.class) {
            handler = HANDLERS.get(kind);
        }
        return handler == null ? 0L : handler.insert(host, amount, simulate);
    }

    public enum Kind {
        ENERGY, MANA, STARLIGHT
    }

    public interface Handler {
        long extract(IActionHost host, long amount, boolean simulate);

        long insert(IActionHost host, long amount, boolean simulate);
    }
}
