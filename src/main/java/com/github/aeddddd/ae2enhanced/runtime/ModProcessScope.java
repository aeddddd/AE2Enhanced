package com.github.aeddddd.ae2enhanced.runtime;

import java.util.Objects;

/** 进程内稳定的不可变启动语义，不持有任何服务器或客户端会话状态。 */
public final class ModProcessScope {
    private final String modId;
    private final String modVersion;
    private final boolean dedicatedServer;
    private final Object bootConfig;

    public ModProcessScope(String modId, String modVersion, boolean dedicatedServer, Object bootConfig) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.modVersion = Objects.requireNonNull(modVersion, "modVersion");
        this.dedicatedServer = dedicatedServer;
        this.bootConfig = Objects.requireNonNull(bootConfig, "bootConfig");
    }

    public String modId() {
        return modId;
    }

    public String modVersion() {
        return modVersion;
    }

    public boolean dedicatedServer() {
        return dedicatedServer;
    }

    public <T> T bootConfig(Class<T> type) {
        return type.cast(bootConfig);
    }
}
