package com.github.aeddddd.ae2enhanced.api;

import appeng.api.networking.security.IActionHost;

/** 网络 Mana 对外 API；具体存储通道由 storage 层经 {@link NetworkResourcePort} 提供。 */
public final class NetworkManaApi {

    private NetworkManaApi() {
    }

    public static long getStoredMana(IActionHost host) {
        return extractMana(host, Long.MAX_VALUE, true);
    }

    public static long extractMana(IActionHost host, long amount, boolean simulate) {
        return NetworkResourcePort.extract(NetworkResourcePort.Kind.MANA, host, amount, simulate);
    }
}
