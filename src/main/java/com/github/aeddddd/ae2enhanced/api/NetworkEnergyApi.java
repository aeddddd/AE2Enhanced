package com.github.aeddddd.ae2enhanced.api;

import appeng.api.networking.security.IActionHost;

/** 网络 RF 能源对外 API；具体存储通道由 storage 层经 {@link NetworkResourcePort} 提供。 */
public final class NetworkEnergyApi {

    private NetworkEnergyApi() {
    }

    public static long getStoredEnergy(IActionHost host) {
        return extractEnergy(host, Long.MAX_VALUE, true);
    }

    public static long extractEnergy(IActionHost host, long amount, boolean simulate) {
        return NetworkResourcePort.extract(NetworkResourcePort.Kind.ENERGY, host, amount, simulate);
    }

    public static long insertEnergy(IActionHost host, long amount, boolean simulate) {
        return NetworkResourcePort.insert(NetworkResourcePort.Kind.ENERGY, host, amount, simulate);
    }
}
