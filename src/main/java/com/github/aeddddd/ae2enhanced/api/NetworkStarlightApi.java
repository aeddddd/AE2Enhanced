package com.github.aeddddd.ae2enhanced.api;

import appeng.api.networking.security.IActionHost;

/** 网络 Starlight 对外 API；具体存储通道由 storage 层经 {@link NetworkResourcePort} 提供。 */
public final class NetworkStarlightApi {

    private NetworkStarlightApi() {
    }

    public static long getStoredStarlight(IActionHost host) {
        return extractStarlight(host, Long.MAX_VALUE, true);
    }

    public static long extractStarlight(IActionHost host, long amount, boolean simulate) {
        return NetworkResourcePort.extract(NetworkResourcePort.Kind.STARLIGHT, host, amount, simulate);
    }
}
