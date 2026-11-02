package com.github.aeddddd.ae2enhanced.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 客户端连接会话；布局偏好的存储与服务器生命周期无关。 */
public final class ClientSessionScope implements AutoCloseable {
    private final UUID sessionId = UUID.randomUUID();
    private final Map<String, String> layoutPreferences;
    private final Map<String, Object> transientState = new HashMap<>();
    private boolean closed;

    public ClientSessionScope() {
        this(new HashMap<>());
    }

    ClientSessionScope(Map<String, String> layoutPreferences) {
        this.layoutPreferences = layoutPreferences;
    }

    public synchronized void putLayoutPreference(String key, String value) {
        layoutPreferences.put(key, value);
    }

    public synchronized Optional<String> layoutPreference(String key) {
        return Optional.ofNullable(layoutPreferences.get(key));
    }

    public synchronized void putTransient(String key, Object value) {
        if (closed) {
            throw new IllegalStateException("Client session is closed");
        }
        transientState.put(key, value);
    }

    public synchronized Optional<Object> transientValue(String key) {
        return Optional.ofNullable(transientState.get(key));
    }

    @Override
    public synchronized void close() {
        closed = true;
        transientState.clear();
    }

    public UUID sessionId() {
        return sessionId;
    }

    public synchronized boolean closed() {
        return closed;
    }
}
