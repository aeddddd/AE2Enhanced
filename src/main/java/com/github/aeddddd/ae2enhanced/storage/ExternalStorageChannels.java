package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

import java.util.EnumMap;
import java.util.Map;

/** 外部存储通道出站端口；可选 mod 适配器在 preInit 注册。 */
public final class ExternalStorageChannels {

    private static final Map<Kind, Provider> PROVIDERS = new EnumMap<>(Kind.class);

    private ExternalStorageChannels() {
    }

    public static synchronized void register(Kind kind, Provider provider) {
        PROVIDERS.put(kind, provider);
    }

    public static boolean isAvailable(Kind kind) {
        Provider provider = PROVIDERS.get(kind);
        return provider != null && provider.isAvailable();
    }

    public static IStorageChannel<?> channel(Kind kind) {
        Provider provider = PROVIDERS.get(kind);
        return provider == null ? null : provider.channel();
    }

    public static IAEStack createStack(Kind kind, long amount) {
        Provider provider = PROVIDERS.get(kind);
        return provider == null ? null : provider.createStack(amount);
    }

    public enum Kind {
        ENERGY, MANA
    }

    public interface Provider {
        boolean isAvailable();
        IStorageChannel<?> channel();

        default IAEStack createStack(long amount) {
            return null;
        }
    }
}
