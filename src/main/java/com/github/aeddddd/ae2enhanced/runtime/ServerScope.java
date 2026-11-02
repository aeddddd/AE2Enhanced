package com.github.aeddddd.ae2enhanced.runtime;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/** 单个服务器生命周期的可变状态与通用服务注册表。 */
public final class ServerScope {
    private final UUID scopeId = UUID.randomUUID();
    private final Map<Class<?>, Object> services = new LinkedHashMap<>();
    private Path saveRoot;
    private boolean acceptingWork = true;
    private boolean closed;

    private static void close(Object service) throws Exception {
        if (service instanceof AutoCloseable) {
            ((AutoCloseable) service).close();
        }
    }

    public synchronized void bindSaveRoot(Path root) {
        ensureAcceptingWork();
        if (saveRoot != null) {
            throw new IllegalStateException("Server save root is already bound");
        }
        saveRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public synchronized Optional<Path> saveRoot() {
        return Optional.ofNullable(saveRoot);
    }

    public synchronized <T> T service(Class<T> type, Supplier<? extends T> factory) {
        ensureAcceptingWork();
        Object existing = services.get(type);
        if (existing != null) {
            return type.cast(existing);
        }
        T created = type.cast(Objects.requireNonNull(factory.get(), "factory result"));
        services.put(type, created);
        return created;
    }

    /** 停止阶段允许只读已存在的服务，但不得为关闭中的 scope 创建新状态。 */
    public synchronized <T> Optional<T> existingService(Class<T> type) {
        return Optional.ofNullable(services.get(type)).map(type::cast);
    }

    public synchronized void stopAcceptingWork() {
        acceptingWork = false;
    }

    public void close() {
        List<Object> closeOrder;
        synchronized (this) {
            if (closed) {
                return;
            }
            stopAcceptingWork();
            closeOrder = new ArrayList<>(services.values());
            Collections.reverse(closeOrder);
            services.clear();
            saveRoot = null;
            closed = true;
        }
        RuntimeException failure = null;
        for (Object service : closeOrder) {
            try {
                close(service);
            } catch (Exception exception) {
                RuntimeException wrapped = exception instanceof RuntimeException
                        ? (RuntimeException) exception : new RuntimeException(exception);
                if (failure == null) {
                    failure = wrapped;
                } else {
                    failure.addSuppressed(wrapped);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public UUID scopeId() {
        return scopeId;
    }

    public synchronized boolean acceptingWork() {
        return acceptingWork;
    }

    public synchronized boolean closed() {
        return closed;
    }

    public synchronized int serviceCount() {
        return services.size();
    }

    private void ensureAcceptingWork() {
        if (!acceptingWork || closed) {
            throw new IllegalStateException("Server scope does not accept new work");
        }
    }

}
