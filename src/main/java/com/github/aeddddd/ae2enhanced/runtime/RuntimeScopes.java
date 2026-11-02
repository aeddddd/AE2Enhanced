package com.github.aeddddd.ae2enhanced.runtime;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Forge 入口使用的小型 scope 定位器，不引入事件总线或依赖注入容器。 */
public final class RuntimeScopes {
    private static final AtomicReference<ModProcessScope> PROCESS = new AtomicReference<>();
    private static final AtomicReference<ServerScope> SERVER = new AtomicReference<>();
    private static final AtomicReference<ClientSessionScope> CLIENT = new AtomicReference<>();
    private static final Map<String, String> CLIENT_LAYOUT_PREFERENCES = new HashMap<>();

    private RuntimeScopes() {
    }

    public static ModProcessScope startProcess(ModProcessScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (!PROCESS.compareAndSet(null, scope)) {
            throw new IllegalStateException("Mod process scope is already initialized");
        }
        return scope;
    }

    public static ModProcessScope requireProcess() {
        ModProcessScope scope = PROCESS.get();
        if (scope == null) {
            throw new IllegalStateException("Mod process scope is not initialized");
        }
        return scope;
    }

    public static ServerScope startServer() {
        ServerScope scope = new ServerScope();
        if (!SERVER.compareAndSet(null, scope)) {
            throw new IllegalStateException("A server scope is already active");
        }
        return scope;
    }

    public static ServerScope requireServer() {
        ServerScope scope = SERVER.get();
        if (scope == null) {
            throw new IllegalStateException("No active server scope");
        }
        return scope;
    }

    public static Optional<ServerScope> currentServer() {
        return Optional.ofNullable(SERVER.get());
    }

    public static void bindServerSaveRoot(Path root) {
        requireServer().bindSaveRoot(root);
    }

    public static void stopServer() {
        requireServer().stopAcceptingWork();
    }

    public static void closeServer() {
        requireServer().close();
    }

    public static void releaseServer() {
        ServerScope scope = requireServer();
        if (!scope.closed()) {
            throw new IllegalStateException("Cannot release a server scope before it is closed");
        }
        if (!SERVER.compareAndSet(scope, null)) {
            throw new IllegalStateException("Active server scope changed during release");
        }
    }

    public static synchronized ClientSessionScope connectClient() {
        ClientSessionScope previous = CLIENT.getAndSet(new ClientSessionScope(CLIENT_LAYOUT_PREFERENCES));
        if (previous != null) {
            previous.close();
        }
        return CLIENT.get();
    }

    public static synchronized void disconnectClient() {
        ClientSessionScope scope = CLIENT.getAndSet(null);
        if (scope != null) {
            scope.close();
        }
    }

    public static Optional<ClientSessionScope> currentClient() {
        return Optional.ofNullable(CLIENT.get());
    }

}
