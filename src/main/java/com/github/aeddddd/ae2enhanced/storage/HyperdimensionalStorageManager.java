package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 超维度仓储中枢会话注册表：服务器级常驻 + 闲置关闭.
 *
 * <p>解决的问题：v1 架构把 HyperdimensionalStorageFile 的生命周期绑死在
 * TileEntity 的 chunk load/unload 上——玩家每进出一次区域就在主线程全量读/写一次，
 * 极大存储下是主要卡顿源。本注册表参照 SavedData 的生命周期思想：
 * 会话（文件 + 全部 adapter + 搜索索引）创建后在服务器级常驻，chunk 卸载只解除
 * 回调绑定（detach），重新进入时 attach 零成本复用；闲置超过
 * {@link AE2EnhancedConfig.Storage#sessionIdleCloseSeconds} 才 close 并冲刷落盘。</p>
 *
 * <p>线程模型：acquire/release 由主线程调用；闲置扫描在独立调度线程；
 * close 的全部文件 IO 委托给文件的 IO 执行器。</p>
 */
public final class HyperdimensionalStorageManager {

    /** 一个 nexus 的常驻会话：文件 + 全部 adapter（含搜索索引等重资产） */
    public static final class NexusSession {
        private final String key;
        private final HyperdimensionalStorageFile file;

        private final ItemStorageAdapter itemAdapter;
        private final FluidStorageAdapter fluidAdapter;
        private final IStorageAdapter energyAdapter;
        private final IStorageAdapter manaAdapter;           // nullable
        private final StarlightStorageAdapter starlightAdapter; // nullable
        private final OptionalStorageManager optionalStorage;
        private final SimpleMEMonitor itemMonitor;

        private int attachCount;
        private long lastDetachMs;

        private NexusSession(String key, World world, UUID nexusId) {
            this.key = key;
            this.file = new HyperdimensionalStorageFile(world, nexusId);

            this.itemAdapter = new ItemStorageAdapter(file);
            file.setStorageRef(itemAdapter.getStorageMap());
            this.itemMonitor = new SimpleMEMonitor(itemAdapter);

            this.fluidAdapter = new FluidStorageAdapter(file);
            file.setFluidStorageRef(fluidAdapter.getStorageMap());

            this.energyAdapter = StorageAdapterFactory.createEnergyAdapter(file);
            @SuppressWarnings("unchecked")
            Map<EnergyDescriptor, HugeCount> energyMap =
                (Map<EnergyDescriptor, HugeCount>) (Map<?, ?>) energyAdapter.getStorageMap();
            file.setEnergyStorageRef(energyMap);

            this.manaAdapter = StorageAdapterFactory.createManaAdapter(file);
            if (manaAdapter != null) {
                file.setManaStorageRef(manaAdapter.getStorageMap());
            }

            if (Loader.isModLoaded("astralsorcery")) {
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
                if (map instanceof Map) {
                    file.setGasStorageRef((Map<?, HugeCount>) map);
                }
            }
            Object essentiaAdapter = optionalStorage.getEssentiaAdapter();
            if (essentiaAdapter != null) {
                Object map = invokeNoArg(essentiaAdapter, "getStorageMap");
                if (map instanceof Map) {
                    file.setEssentiaStorageRef((Map<?, HugeCount>) map);
                }
            }

            // 全部 adapter 注册完目标 Map 后，首次全量读提交到 IO 线程异步执行：
            // 机械硬盘上不再因 chunk 加载顶住主线程（存取操作经 awaitLoaded 闸门等待）
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

    private static final Map<String, NexusSession> SESSIONS = new HashMap<>();

    private static final ScheduledExecutorService IDLE_SCANNER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AE2E-Storage-SessionScan");
            t.setDaemon(true);
            return t;
        });
    private static ScheduledFuture<?> scanTask = null;

    private HyperdimensionalStorageManager() {}

    /**
     * 获取或创建会话并 attach。主线程调用。
     * 首次创建时同步加载磁盘数据（唯一的一次全量读）；之后 attach 为零成本复用。
     */
    public static synchronized NexusSession acquire(World world, UUID nexusId) {
        String key = keyFor(world, nexusId);
        NexusSession session = SESSIONS.get(key);
        if (session == null) {
            session = new NexusSession(key, world, nexusId);
            SESSIONS.put(key, session);
            AE2Enhanced.LOGGER.info("[AE2E] 创建超维度仓储会话: {}", nexusId);
        }
        session.attachCount++;
        return session;
    }

    /**
     * 解除 attach（chunk 卸载/结构解体时调用）。会话继续常驻；
     * 闲置超过 sessionIdleCloseSeconds 后才落盘关闭。
     */
    public static synchronized void release(NexusSession session) {
        if (session == null) return;
        session.attachCount--;
        if (session.attachCount < 0) {
            session.attachCount = 0;
            AE2Enhanced.LOGGER.warn("[AE2E] 会话 attach 计数异常: {}", session.key);
        }
        if (session.attachCount == 0) {
            session.lastDetachMs = System.currentTimeMillis();
        }
    }

    /** 闲置扫描：关闭长期无人 attach 的会话（数据已落盘后释放内存）。 */
    private static synchronized void scanIdleSessions() {
        long now = System.currentTimeMillis();
        long idleMs = AE2EnhancedConfig.storage.sessionIdleCloseSeconds * 1000L;
        Iterator<NexusSession> it = SESSIONS.values().iterator();
        while (it.hasNext()) {
            NexusSession session = it.next();
            if (session.attachCount > 0) continue;
            if (session.lastDetachMs == 0 || now - session.lastDetachMs < idleMs) continue;
            AE2Enhanced.LOGGER.info("[AE2E] 闲置关闭超维度仓储会话: {}", session.key);
            closeSession(session);
            it.remove();
        }
    }

    private static void closeSession(NexusSession session) {
        try {
            session.optionalStorage.close();
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] 关闭可选存储失败: {}", session.key, t);
        }
        try {
            session.file.close();
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.error("[AE2E] 关闭仓储会话失败: {}", session.key, t);
        }
    }

    /** 服务器停止时调用：全部会话落盘关闭并清空注册表。 */
    public static synchronized void closeAll() {
        for (NexusSession session : SESSIONS.values()) {
            closeSession(session);
        }
        SESSIONS.clear();
    }

    /** 注册闲置扫描任务（mod init 时调用一次）。 */
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

    private static String keyFor(World world, UUID nexusId) {
        File dir = world.getSaveHandler().getWorldDirectory();
        return dir.getAbsoluteFile().toPath().normalize() + ":" + nexusId;
    }
}
