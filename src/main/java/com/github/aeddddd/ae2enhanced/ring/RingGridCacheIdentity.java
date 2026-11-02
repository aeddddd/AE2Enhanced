package com.github.aeddddd.ae2enhanced.ring;

/** 网格缓存身份：ItemStack 实例与加密绑定任一变化都必须重建。 */
final class RingGridCacheIdentity {
    private final Object stackIdentity;
    private final String encryptionKey;

    RingGridCacheIdentity(Object stackIdentity, String encryptionKey) {
        this.stackIdentity = stackIdentity;
        this.encryptionKey = encryptionKey;
    }

    boolean matches(Object stackIdentity, String encryptionKey) {
        return this.stackIdentity == stackIdentity && this.encryptionKey.equals(encryptionKey);
    }
}
