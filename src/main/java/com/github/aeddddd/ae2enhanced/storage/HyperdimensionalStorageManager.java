package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.BootConfig;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** 服务器级常驻会话注册表；注册表锁不覆盖任何加载、冲刷、关闭或 quarantine IO。 */
public final class HyperdimensionalStorageManager {

    private static SessionFactory sessionFactory = NexusSession::new;
    private static ScheduledFuture<?> scanTask;

    private static final Map<String, NexusSession> SESSIONS = new HashMap<>();

    public static NexusSession acquire(World world, UUID nexusId) {
        String key = keyFor(world, nexusId);
        File worldDir = world.getSaveHandler().getWorldDirectory();
        for (;;) {
            boolean quarantined = StorageQuarantine.exists(worldDir, nexusId);
            NexusSession session;
            synchronized (SESSIONS) {
                session = SESSIONS.get(key);
                if (session != null && !quarantined && !session.file.isCloseBlocked()
                        && !session.file.isClosed()) {
                    session.attachCount++;
                    return session;
                }
            }

            if (session != null) {
                synchronized (SESSIONS) {
                    if (session.attachCount > 0) {
                        throw new IllegalStateException("仓储会话正在关闭或隔离，仍有活动 attach: " + key);
                    }
                }
                if (!closeSession(session, "acquire-retry")) {
                    throw new IllegalStateException("仓储会话关闭恢复失败，已保留隔离: " + key);
                }
                synchronized (SESSIONS) {
                    if (SESSIONS.get(key) == session) SESSIONS.remove(key);
                }
                continue;
            }

            if (quarantined) {
                validateQuarantineKey(worldDir, nexusId, key);
                NexusSession recovery = sessionFactory.create(key, world, nexusId);
                if (!closeSession(recovery, "restart-recovery")) {
                    throw new IllegalStateException("持久 quarantine 恢复失败，拒绝新写者: " + key);
                }
                continue;
            }

            NexusSession created = sessionFactory.create(key, world, nexusId);
            synchronized (SESSIONS) {
                session = SESSIONS.get(key);
                if (session == null) {
                    SESSIONS.put(key, created);
                    created.attachCount = 1;
                    AE2Enhanced.LOGGER.info("[AE2E] 创建超维度仓储会话: {}", nexusId);
                    return created;
                }
            }
            if (!closeSession(created, "concurrent-create-discard")) {
                throw new IllegalStateException("并发创建的冗余仓储会话无法安全关闭: " + key);
            }
        }
    }

    private static final ScheduledExecutorService IDLE_SCANNER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AE2E-Storage-SessionScan");
            t.setDaemon(true);
            return t;
        });

    private static void validateQuarantineKey(File worldDir, UUID nexusId, String key) {
        try {
            StorageQuarantine.Entry entry = StorageQuarantine.read(worldDir, nexusId);
            if (!key.equals(entry.key)) {
                throw new IllegalStateException("quarantine key 不匹配，拒绝恢复: " + key);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("quarantine 记录不可读，拒绝新写者: " + key, e);
        }
    }

    private HyperdimensionalStorageManager() {}

    public static synchronized void release(NexusSession session) {
        if (session == null) return;
        session.attachCount--;
        if (session.attachCount < 0) {
            session.attachCount = 0;
            AE2Enhanced.LOGGER.warn("[AE2E] 会话 attach 计数异常: {}", session.key);
        }
        if (session.attachCount == 0) session.lastDetachMs = System.currentTimeMillis();
    }

    private static void scanIdleSessions() {
        if (!com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.currentServer()
                .map(com.github.aeddddd.ae2enhanced.runtime.ServerScope::acceptingWork).orElse(false)) {
            return;
        }
        long now = System.currentTimeMillis();
        long idleMs = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().storage.sessionIdleCloseSeconds * 1000L;
        List<NexusSession> candidates = new ArrayList<>();
        synchronized (SESSIONS) {
            for (NexusSession session : SESSIONS.values()) {
                if (session.attachCount == 0 && session.lastDetachMs != 0
                        && now - session.lastDetachMs >= idleMs && !session.file.isClosed()) {
                    candidates.add(session);
                }
            }
        }
        for (NexusSession session : candidates) {
            AE2Enhanced.LOGGER.info("[AE2E] 闲置关闭超维度仓储会话: {}", session.key);
            if (!closeSession(session, "idle-close")) continue;
            synchronized (SESSIONS) {
                if (SESSIONS.get(session.key) == session && session.attachCount == 0) {
                    SESSIONS.remove(session.key);
                }
            }
        }
    }

    private static boolean closeSession(NexusSession session, String reason) {
        CloseResult result = session.file.close();
        if (!result.isSuccess()) {
            try {
                File worldDir = worldDirFromKey(session.key);
                UUID nexusId = session.file.getNexusId();
                StorageQuarantine.write(worldDir, nexusId, session.key, result, reason,
                    session.file.testHooks());
            } catch (IOException | RuntimeException e) {
                AE2Enhanced.LOGGER.error("[AE2E] quarantine 写入失败，session 保留且 WAL/base 不删除: {}", session.key, e);
            }
            AE2Enhanced.LOGGER.error("[AE2E] 仓储会话关闭失败，保留 session: {} {}", session.key, result);
            return false;
        }
        try {
            session.optionalStorage.close();
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] 关闭可选存储失败: {}", session.key, t);
        }
        try {
            StorageQuarantine.clear(worldDirFromKey(session.key), session.file.getNexusId());
            return true;
        } catch (IOException e) {
            AE2Enhanced.LOGGER.error("[AE2E] quarantine 清除失败，保留已关闭 session 供下次重试: {}", session.key, e);
            return false;
        }
    }

    /** 只释放成功关闭的活动引用；失败 session 与持久 quarantine 均保留。 */
    public static void closeAll() {
        List<NexusSession> sessions;
        synchronized (SESSIONS) {
            sessions = new ArrayList<>(SESSIONS.values());
        }
        for (NexusSession session : sessions) {
            if (!closeSession(session, "close-all")) continue;
            synchronized (SESSIONS) {
                if (SESSIONS.get(session.key) == session) SESSIONS.remove(session.key);
            }
        }
    }

    private static File worldDirFromKey(String key) {
        int separator = key.lastIndexOf(':');
        if (separator < 0) throw new IllegalStateException("非法仓储 session key: " + key);
        return new File(key.substring(0, separator));
    }

    static void setSessionFactoryForTests(SessionFactory factory) {
        synchronized (SESSIONS) {
            sessionFactory = factory == null ? NexusSession::new : factory;
        }
    }

    public static synchronized void startIdleScanner() {
        if (scanTask != null) return;
        scanTask = IDLE_SCANNER.scheduleWithFixedDelay(() -> {
            try {
                scanIdleSessions();
            } catch (Throwable t) {
                AE2Enhanced.LOGGER.error("[AE2E] 闲置会话扫描失败", t);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    static int sessionCountForTests() {
        synchronized (SESSIONS) {
            return SESSIONS.size();
        }
    }

    private static String keyFor(World world, UUID nexusId) {
        File dir = world.getSaveHandler().getWorldDirectory();
        return dir.getAbsoluteFile().toPath().normalize() + ":" + nexusId;
    }

    interface SessionFactory {
        NexusSession create(String key, World world, UUID nexusId);
    }

    public static final class NexusSession {
        private final String key;
        private final HyperdimensionalStorageFile file;

        private final ItemStorageAdapter itemAdapter;
        private final FluidStorageAdapter fluidAdapter;
        private final IStorageAdapter energyAdapter;
        private final IStorageAdapter manaAdapter;
        private final StarlightStorageAdapter starlightAdapter;
        private final OptionalStorageManager optionalStorage;
        private final SimpleMEMonitor itemMonitor;

        private int attachCount;
        private long lastDetachMs;

        private NexusSession(String key, World world, UUID nexusId) {
            this(key, new HyperdimensionalStorageFile(world, nexusId));
        }

        NexusSession(String key, HyperdimensionalStorageFile file) {
            this.key = key;
            this.file = file;

            this.itemAdapter = new ItemStorageAdapter(file);
            file.setStorageRef(itemAdapter.getStorageMap());
            this.itemMonitor = new SimpleMEMonitor(itemAdapter);

            this.fluidAdapter = new FluidStorageAdapter(file);
            file.setFluidStorageRef(fluidAdapter.getStorageMap());

            this.energyAdapter = StorageAdapterFactory.createEnergyAdapter(file);
            if (energyAdapter != null) {
                @SuppressWarnings("unchecked")
                Map<EnergyDescriptor, HugeCount> energyMap =
                    (Map<EnergyDescriptor, HugeCount>) (Map<?, ?>) energyAdapter.getStorageMap();
                file.setEnergyStorageRef(energyMap);
            }

            this.manaAdapter = StorageAdapterFactory.createManaAdapter(file);
            if (manaAdapter != null) file.setManaStorageRef(manaAdapter.getStorageMap());

            if (Loader.isModLoaded("astralsorcery")
                    && BootConfig.current().channelEnabled("starlight")
                    && BootConfig.current().integrationEnabled("astralsorcery")) {
                this.starlightAdapter = new StarlightStorageAdapter(file);
                file.setStarlightStorageRef(starlightAdapter.getStorageMap());
            } else {
                this.starlightAdapter = null;
            }

            this.optionalStorage = new OptionalStorageManager();
            optionalStorage.init(file);
            Object gasAdapter = optionalStorage.getGasAdapter();
            if (gasAdapter != null) {
                Object map = invokeNoArg(gasAdapter, "getStorageMap");
                if (map instanceof Map) file.setGasStorageRef((Map<?, HugeCount>) map);
            }
            Object essentiaAdapter = optionalStorage.getEssentiaAdapter();
            if (essentiaAdapter != null) {
                Object map = invokeNoArg(essentiaAdapter, "getStorageMap");
                if (map instanceof Map) file.setEssentiaStorageRef((Map<?, HugeCount>) map);
            }
            file.beginLoad();
        }

        private static Object invokeNoArg(Object target, String method) {
            try {
                return target.getClass().getMethod(method).invoke(target);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] 会话创建: 反射调用 {} 失败", method, e);
                return null;
            }
        }

        public HyperdimensionalStorageFile getFile() { return file; }
        public ItemStorageAdapter getItemAdapter() { return itemAdapter; }
        public FluidStorageAdapter getFluidAdapter() { return fluidAdapter; }
        public IStorageAdapter getEnergyAdapter() { return energyAdapter; }
        public IStorageAdapter getManaAdapter() { return manaAdapter; }
        public StarlightStorageAdapter getStarlightAdapter() { return starlightAdapter; }
        public OptionalStorageManager getOptionalStorage() { return optionalStorage; }
        public SimpleMEMonitor getItemMonitor() { return itemMonitor; }
    }
}
