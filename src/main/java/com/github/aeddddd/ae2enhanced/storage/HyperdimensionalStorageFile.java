package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.storage.codec.DescriptorCodec;
import com.github.aeddddd.ae2enhanced.storage.codec.EnergyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.storage.codec.FluidDescriptorCodec;
import com.github.aeddddd.ae2enhanced.storage.codec.ItemDescriptorCodec;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.io.*;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.zip.CRC32;

/**
 * 超维度仓储中枢的外部文件持久化层（v2：分区快照 + WAL 预写日志）.
 *
 * <p>每个结构一个独立目录，数据不写入 NBT/WorldSavedData。持久化模型参照
 * NeoECOAE FileBackedInfiniteStorageEngine 的 WAL+快照思想，按 7 个分区独立：</p>
 *
 * <ul>
 *   <li><b>base 快照</b> {@code <section>.bin}：自定义二进制格式（v2 头部带 checkpointRev），
 *     tmp 文件 + ATOMIC_MOVE 原子替换。仅在分区空闲超过
 *     {@link AE2EnhancedConfig.Storage#checkpointIdleSeconds} 或 WAL 超过
 *     {@link AE2EnhancedConfig.Storage#walCheckpointThresholdBytes} 时后台重写。</li>
 *   <li><b>WAL</b> {@code <section>.wal}：变更以绝对值帧（[len][crc32][payload]）追加，
 *     每 tick 由控制器驱动批量异步写入并 force，耐久窗口从 5s 降到约 1 tick。
 *     回放时按 record.rev &gt; base.checkpointRev 过滤，尾部损坏自动截断修复。</li>
 * </ul>
 *
 * <p><b>线程模型</b>：所有文件 IO（首次加载、WAL 追加、checkpoint 快照、截断）都在
 * 共享单线程 {@link #FLUSH_EXECUTOR} 上串行执行；主线程只做 recordChange（pending 缓冲）
 * 与 adapter Map 修改。会话创建时各 adapter 只注册目标 Map（{@code loadXxx}），
 * {@link #beginLoad()} 把首次全量读提交到 IO 线程异步执行；存取操作通过
 * {@link #awaitLoaded()} 等待首加载完成——机械硬盘上不再因 chunk 加载顶住主线程。</p>
 *
 * <p>快照一致性靠修订号：快照期间的新变更使 mutationRev 超过 snapshotRev，分区保持脏
 * 下轮重拍；WAL 只在该分区收敛（checkpointRev==mutationRev）后截断，且回放按 rev 过滤
 * 已固化记录，因此快照无需阻塞写入。持续写入导致乐观快照多轮不收敛时，退化为有界停顿
 * 快照：mutationLock 内只做内存级条目拷贝，磁盘写在锁外进行（不停顶主线程）。</p>
 *
 * <p><b>WAL 故障策略</b>：瞬时 IO 故障（磁盘抖动、杀软锁文件等）指数退避重试且
 * <b>不</b>触发安全模式——数据保留在 pending，checkpoint 仍可经 base 落盘；只有永久性
 * 错误（帧大小非法、序列化失败、加载期损坏）才置分区安全模式。</p>
 *
 * <h2>base 文件格式（v2）</h2>
 * <pre>
 * Header:
 *   Magic[4]      = "AE2E"
 *   Version       = 2 (int32)          // v1 文件: version=1, 此处为 flags(int32), 无 checkpointRev
 *   CheckpointRev = int64              // 仅 v2
 *   EntryCount    = N (int32)
 * Entries:
 *   DescriptorLength  int32 (1..MAX_ENTRY_BYTES)
 *   DescriptorBytes   byte[DescriptorLength]
 *   CountSign         byte
 *   CountMagLength    int32
 *   CountMagnitude    byte[CountMagLength]   // BigInteger 兼容编码（HugeCount.toByteArray）
 * </pre>
 *
 * <h2>WAL 帧格式</h2>
 * <pre>
 * Frame:   [payloadLen int32][crc32 int32][payload]
 * Payload: [version int32 = 1][recordCount int32] records
 * Record:  [descLen int32][descBytes][countSign byte][countMagLen int32][countMag][rev int64]
 *          count == 0 表示该 key 已删除（回放时移除）
 * </pre>
 */
public class HyperdimensionalStorageFile {

    public static final int CURRENT_VERSION = 2;
    private static final int WAL_VERSION = 1;
    private static final byte[] MAGIC = "AE2E".getBytes(StandardCharsets.US_ASCII);
    /** WAL 单帧上限，超过视为损坏 */
    private static final int MAX_WAL_FRAME_BYTES = 16 * 1024 * 1024;
    /** WAL 瞬时写失败的退避上限（指数退避从 1s 起，封顶 60s）；瞬时故障不进安全模式 */
    private static final long MAX_WAL_BACKOFF_MS = 60_000L;
    /** WAL 强制 checkpoint 时乐观快照的最大轮数，仍不收敛则锁定变异做有界停顿快照 */
    private static final int MAX_OPTIMISTIC_ROUNDS = 3;

    /** 永久性 WAL 错误（数据本身无法落盘，重试无意义）：立即置分区安全模式 */
    private static final class PermanentWalException extends IOException {
        PermanentWalException(String message) {
            super(message);
        }
    }

    /** 全部 nexus 实例共享的单线程 IO 执行器：WAL/checkpoint/截断全部串行于此线程 */
    private static final ScheduledExecutorService FLUSH_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AE2E-Storage-Flush");
            t.setDaemon(true);
            return t;
        });

    private static final long DEFAULT_CLOSE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    private final UUID nexusId;
    private final File baseDir;
    private final File oldFile;
    private final ScheduledExecutorService flushExecutor;
    private final HyperdimensionalStorageTestHooks testHooks;
    private final long closeTimeoutMs;
    private final ScheduledFuture<?> flushTask;
    private volatile boolean closed = false;
    private final Object closeStateLock = new Object();
    private final Object ioScheduleLock = new Object();
    private final List<IoCompletion> pendingCompletions = new ArrayList<>();
    private volatile boolean closeBlocked = false;
    private volatile CloseResult lastCloseResult;
    private boolean ioQueued;
    private boolean ioRunning;
    private boolean loadRequested;
    private boolean flushRequested;
    private boolean forceFlushRequested;
    // 全局安全模式：仅迁移失败等无法定位到具体分区的加载失败才置位
    private volatile boolean safeMode = false;
    /** 加载失败的分区集合：对应分区拒绝注入/提取，base/WAL 都不会被覆写 */
    private final Set<StorageSection> failedSections = ConcurrentHashMap.newKeySet();
    /** 加载锁：attach 读取 base+WAL 时与后台 checkpoint 写互斥 */
    private final Object loadLock = new Object();
    /** 主线程 tick 快速判空用：pending 中的记录总数（近似，允许偶尔多空转） */
    private volatile int pendingTotal = 0;
    private volatile long lastMutationNanos = Long.MIN_VALUE;
    private volatile boolean closeLogged = false;

    // ---- 异步首加载 ----

    /** 首加载完成闩锁：存取操作与 recordChange 在其上等待；加载在 FLUSH_EXECUTOR 上异步执行 */
    private final CountDownLatch loadLatch = new CountDownLatch(1);
    /** 已注册待加载的 (分区, 目标 Map)；beginLoad 后关闭注册 */
    private final List<LoadRegistration> loadRegistrations = new ArrayList<>();
    /** 首加载完成后在 IO 线程执行的回调（recalcTotal 等线程安全操作） */
    private final List<Runnable> postLoadHooks = new ArrayList<>();
    private boolean loadRegistrationClosed = false;
    private volatile boolean loadScheduled = false;
    private volatile boolean loadFinished = false;
    private volatile boolean loadFailed = false;

    private static final class LoadRegistration {
        final SectionIO section;
        final Map<Object, HugeCount> target;

        LoadRegistration(SectionIO section, Map<Object, HugeCount> target) {
            this.section = section;
            this.target = target;
        }
    }

    // ---- 分区状态 ----

    /** WAL 待写记录：绝对值 + 修订号（回放按 rev &gt; base.checkpointRev 过滤） */
    private static final class WalVal {
        final HugeCount count;
        final long rev;

        WalVal(HugeCount count, long rev) {
            this.count = count;
            this.rev = rev;
        }
    }

    /** 变更批次：从 pending 取出的 (key, count, rev) 三元组列表，序列化在 IO 线程执行 */
    private static final class WalBatch {
        final SectionIO section;
        final List<Object> keys;
        final List<WalVal> vals;
        /** 写入前 WAL 文件长度，写失败时回滚截断到此处，防止撕裂帧留在文件中部 */
        long walSizeBefore;

        WalBatch(SectionIO section, List<Object> keys, List<WalVal> vals) {
            this.section = section;
            this.keys = keys;
            this.vals = vals;
        }
    }

    /** 直接以世界目录构造（便于无头测试与服务器级注册表复用）。 */
    public HyperdimensionalStorageFile(File worldDir, UUID nexusId) {
        this(worldDir, nexusId, FLUSH_EXECUTOR, HyperdimensionalStorageTestHooks.NONE, DEFAULT_CLOSE_TIMEOUT_MS);
    }

    HyperdimensionalStorageFile(File worldDir, UUID nexusId, ScheduledExecutorService flushExecutor,
                                HyperdimensionalStorageTestHooks testHooks, long closeTimeoutMs) {
        this.nexusId = nexusId;
        this.flushExecutor = flushExecutor;
        this.testHooks = testHooks;
        this.closeTimeoutMs = closeTimeoutMs;
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            AE2Enhanced.LOGGER.warn("Failed to create storage directory: {}", storageDir.getAbsolutePath());
        }

        this.baseDir = new File(storageDir, nexusId.toString());
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            AE2Enhanced.LOGGER.warn("Failed to create storage base directory: {}", baseDir.getAbsolutePath());
        }

        this.oldFile = new File(storageDir, nexusId.toString() + ".dat");

        this.itemSection = new SectionIO(StorageSection.ITEM,
            new File(baseDir, "items.bin"), new File(baseDir, "items.wal"),
            ItemDescriptorCodec.INSTANCE, null, null);
        this.fluidSection = new SectionIO(StorageSection.FLUID,
            new File(baseDir, "fluids.bin"), new File(baseDir, "fluids.wal"),
            FluidDescriptorCodec.INSTANCE, null, null);
        this.energySection = new SectionIO(StorageSection.ENERGY,
            new File(baseDir, "energy.bin"), new File(baseDir, "energy.wal"),
            EnergyDescriptorCodec.INSTANCE, null, null);

        initConditionalCodecs();
        for (SectionIO s : new SectionIO[]{itemSection, fluidSection, energySection,
                gasSection, essentiaSection, manaSection, starlightSection}) {
            if (s != null) allSections.add(s);
        }

        // Migrate old single-file NBT format if present
        if (oldFile.exists()) {
            migrateFromOldFormat();
        }

        int flushInterval = com.github.aeddddd.ae2enhanced.config.ServerConfig.current().storage.flushIntervalSeconds;
        this.flushTask = flushExecutor.scheduleWithFixedDelay(this::periodicFlush, flushInterval, flushInterval, TimeUnit.SECONDS);
    }

    /** 序列化按帧切片，避免单帧超过 WAL 帧上限（大批量同 tick 变更场景） */
    private static final int WAL_BATCH_TARGET_BYTES = 8 * 1024 * 1024;

    /**
     * 关闭加载注册并把首次全量读提交到 FLUSH_EXECUTOR 异步执行（幂等）.
     * 必须在全部 adapter 构造（注册目标 Map）完成之后调用。
     */
    public void beginLoad() {
        synchronized (loadRegistrations) {
            loadRegistrationClosed = true;
            if (loadScheduled) return;
            loadScheduled = true;
        }
        synchronized (ioScheduleLock) {
            loadRequested = true;
            ensureIoTaskLocked();
        }
    }

    private final SectionIO itemSection;
    private final SectionIO fluidSection;
    private final SectionIO energySection;
    private SectionIO gasSection;
    private SectionIO essentiaSection;
    private SectionIO manaSection;
    private SectionIO starlightSection;
    private final List<SectionIO> allSections = new ArrayList<>();

    public HyperdimensionalStorageFile(World world, UUID nexusId) {
        this(world.getSaveHandler().getWorldDirectory(), nexusId);
    }

    /** 首加载任务（IO 线程）：逐分区读 base + 回放 WAL，再执行 postLoad 钩子。 */
    private void performLoad() {
        List<LoadRegistration> regs;
        synchronized (loadRegistrations) {
            regs = new ArrayList<>(loadRegistrations);
        }
        try {
            for (LoadRegistration reg : regs) {
                doLoadSection(reg.section, reg.target);
            }
            List<Runnable> hooks;
            synchronized (loadRegistrations) {
                hooks = new ArrayList<>(postLoadHooks);
            }
            for (Runnable hook : hooks) {
                try {
                    hook.run();
                } catch (Throwable t) {
                    AE2Enhanced.LOGGER.error("[AE2E] postLoad 钩子执行失败: {}", nexusId, t);
                }
            }
        } catch (Throwable t) {
            // 加载任务整体异常：已注册分区全部置安全模式（只读保护），闩锁照常放行避免永久挂起
            AE2Enhanced.LOGGER.error("[AE2E] 首加载任务异常终止: {}", nexusId, t);
            for (LoadRegistration reg : regs) {
                markSectionFailed(reg.section.section);
            }
        } finally {
            loadFailed = isSafeMode();
            loadFinished = true;
            loadLatch.countDown();
        }
    }

    /** 挽救路径专用：尽快在 IO 线程固化一次 checkpoint（不等待空闲窗口）。 */
    private void scheduleSalvageCheckpoint(SectionIO io) {
        requestIoFlush(true, null);
    }

    private void initConditionalCodecs() {
        // GasDescriptorCodec / EssentiaDescriptorCodec / ManaDescriptorCodec / StarlightDescriptorCodec
        // 类本身不硬引用可选 Mod 类,但为了绝对安全(JVM 链接阶段行为不确定),仍通过反射加载.
        gasSection = createReflectiveSection(StorageSection.GAS,
            "com.github.aeddddd.ae2enhanced.storage.codec.GasDescriptorCodec", "gases");
        essentiaSection = createReflectiveSection(StorageSection.ESSENTIA,
            "com.github.aeddddd.ae2enhanced.storage.codec.EssentiaDescriptorCodec", "essentias");
        manaSection = createReflectiveSection(StorageSection.MANA,
            "com.github.aeddddd.ae2enhanced.storage.codec.ManaDescriptorCodec", "mana");
        starlightSection = createReflectiveSection(StorageSection.STARLIGHT,
            "com.github.aeddddd.ae2enhanced.storage.codec.StarlightDescriptorCodec", "starlight");
    }

    private SectionIO createReflectiveSection(StorageSection section, String codecClassName, String fileBase) {
        try {
            Class<?> clazz = Class.forName(codecClassName);
            Object codec = clazz.getField("INSTANCE").get(null);
            java.lang.reflect.Method read = clazz.getMethod("read", DataInput.class);
            java.lang.reflect.Method write = clazz.getMethod("write", DataOutput.class, Descriptor.class);
            return new SectionIO(section,
                new File(baseDir, fileBase + ".bin"), new File(baseDir, fileBase + ".wal"),
                codec, read, write);
        } catch (Throwable e) {
            return null;
        }
    }

    private SectionIO sectionFor(StorageSection section) {
        switch (section) {
            case ITEM: return itemSection;
            case FLUID: return fluidSection;
            case GAS: return gasSection;
            case ESSENTIA: return essentiaSection;
            case ENERGY: return energySection;
            case MANA: return manaSection;
            case STARLIGHT: return starlightSection;
            default: return null;
        }
    }

    // ---- Load ----

    public void load(Map<ItemDescriptor, HugeCount> target) {
        loadSection(itemSection, target);
    }

    public void loadFluids(Map<FluidDescriptor, HugeCount> target) {
        loadSection(fluidSection, target);
    }

    @SuppressWarnings("unchecked")
    public void loadGases(Map<?, HugeCount> target) {
        loadSection(gasSection, (Map<Object, HugeCount>) target);
    }

    @SuppressWarnings("unchecked")
    public void loadEssentias(Map<?, HugeCount> target) {
        loadSection(essentiaSection, (Map<Object, HugeCount>) target);
    }

    public void loadEnergy(Map<EnergyDescriptor, HugeCount> target) {
        loadSection(energySection, target);
    }

    @SuppressWarnings("unchecked")
    public void loadMana(Map<?, HugeCount> target) {
        loadSection(manaSection, (Map<Object, HugeCount>) target);
    }

    @SuppressWarnings("unchecked")
    public void loadStarlight(Map<?, HugeCount> target) {
        loadSection(starlightSection, (Map<Object, HugeCount>) target);
    }

    /**
     * 注册一个分区的加载目标（仅登记，不读盘）.
     * 实际的首次全量读由 {@link #beginLoad()} 提交到 IO 线程异步执行。
     */
    private void loadSection(SectionIO section, Map<?, HugeCount> target) {
        if (section == null || !section.available() || target == null) return;
        @SuppressWarnings("unchecked")
        Map<Object, HugeCount> rawTarget = (Map<Object, HugeCount>) target;
        synchronized (loadRegistrations) {
            if (loadRegistrationClosed) {
                throw new IllegalStateException("beginLoad 后不得再注册加载目标: " + section.section);
            }
            loadRegistrations.add(new LoadRegistration(section, rawTarget));
        }
    }

    /**
     * 注册首加载完成后的回调（在 IO 线程执行，仅放线程安全的操作，如 recalcTotal）.
     */
    public void registerPostLoadHook(Runnable hook) {
        synchronized (loadRegistrations) {
            if (loadRegistrationClosed) {
                throw new IllegalStateException("beginLoad 后不得再注册回调");
            }
            postLoadHooks.add(hook);
        }
    }

    /** 读取条目的描述符（带长度上界校验，防止损坏文件触发巨额分配 OOM）。
     *  注意 len == 0 是合法编码：Energy/Mana/Starlight 等单例描述符无字段,codec 不写任何字节。 */
    private Object readDescriptorEntry(SectionIO section, DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > StorageConstants.MAX_ENTRY_BYTES) {
            throw new IOException("描述符长度越界: " + len + " (" + section.section + ")");
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        try (DataInputStream descIn = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Object descriptor = section.readDescriptor(descIn);
            return descriptor != null ? descriptor : new PreservedDescriptor(bytes);
        }
    }

    /** 首加载等待必须在分区锁之前完成；回调内完成 Map、索引、总数与 WAL 的同一提交。 */
    public <R> R mutateAtomically(StorageSection section, Supplier<R> mutation) {
        awaitLoaded();
        SectionIO io = sectionFor(section);
        if (io == null || !io.available()) return mutation.get();
        io.mutationLock.lock();
        try {
            return mutation.get();
        } finally {
            io.mutationLock.unlock();
        }
    }

    /**
     * 等待首次全量加载完成。存取操作与 recordChange 的入口闸门：
     * 加载在 IO 线程异步进行，只有真正触碰存储的调用才需要等待（且仅首加载窗口内）。
     */
    public void awaitLoaded() {
        if (loadFinished) return;
        try {
            while (!loadLatch.await(30, TimeUnit.SECONDS)) {
                AE2Enhanced.LOGGER.warn("[AE2E] nexus {} 首加载超过 30s 仍在进行（慢磁盘?）", nexusId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void doLoadSection(SectionIO section, Map<Object, HugeCount> rawTarget) {
        synchronized (loadLock) {
            long baseRev = readBaseSnapshot(section, rawTarget);
            if (failedSections.contains(section.section)) {
                return; // 头部损坏：不回放 WAL（此时 WAL 是唯一的完整副本，留给人工恢复）
            }
            section.checkpointRev = baseRev;
            replayWal(section, rawTarget, baseRev);
            if (section.salvagedCorruptBase && !failedSections.contains(section.section)) {
                // 挽救：隔离损坏文件（保留取证），标脏并尽快固化一份新 base
                quarantineCorruptBase(section);
                if (section.mutationRev <= baseRev) {
                    // 无 WAL 记录时也要触发 checkpoint,否则挽救出的内存态不落盘
                    section.mutationRev = baseRev + 1;
                }
                section.lastMutationNanos = System.nanoTime();
                scheduleSalvageCheckpoint(section);
            }
        }
    }

    /** 把截断/损坏的 base 文件改名隔离（保留取证），后续 checkpoint 会写入全新的 base。 */
    private void quarantineCorruptBase(SectionIO io) {
        File quarantined = new File(io.baseFile.getAbsolutePath() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(io.baseFile.toPath(), quarantined.toPath(), StandardCopyOption.REPLACE_EXISTING);
            AE2Enhanced.LOGGER.warn("[AE2E] 已隔离损坏的 {} base 文件 -> {}", io.section, quarantined.getName());
        } catch (IOException e) {
            AE2Enhanced.LOGGER.error("[AE2E] 无法隔离损坏的 {} base 文件: {}", io.section, io.baseFile, e);
        }
    }

    /** 保留旧入口；新 adapter 必须走 mutateAtomically + recordChangeLocked。 */
    public void recordChange(StorageSection section, Object descriptor, HugeCount newCount) {
        mutateAtomically(section, () -> {
            recordChangeLocked(section, descriptor, newCount);
            return null;
        });
    }

    /**
     * 读取 base 快照，返回其中的 checkpointRev（文件不存在或 v1 头部时为 0）。
     * <p>失败分两档处理：</p>
     * <ul>
     *   <li><b>头部损坏</b>（magic/version/entryCount 不可信）：分区安全模式，不做挽救
     *       （布局不可信时"部分读取"可能把错位垃圾当成真数据）。</li>
     *   <li><b>条目循环中途截断</b>（EOF 或长度越界，头部完好）：已读入的完整条目一定有效，
     *       标记 {@code salvagedCorruptBase} 进入挽救流程（WAL 重放 + 隔离重建），不进安全模式。</li>
     * </ul>
     */
    private long readBaseSnapshot(SectionIO section, Map<Object, HugeCount> target) {
        File file = section.baseFile;
        if (!file.exists()) return 0L;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            long checkpointRev = readAndValidateHeader(in, section);
            int entryCount = in.readInt();
            if (entryCount < 0) {
                throw new IOException("条目数为负: " + entryCount);
            }
            int read = 0;
            try {
                for (; read < entryCount; read++) {
                    Object descriptor = readDescriptorEntry(section, in);
                    HugeCount count = readCount(in);
                    if (descriptor != null) {
                        target.put(descriptor, count);
                    }
                }
                return checkpointRev;
            } catch (Exception e) {
                // 截断/中途损坏：按帧解析的完整条目必然有效（条目要么完整读入要么整体跳过）
                section.salvagedCorruptBase = true;
                AE2Enhanced.LOGGER.warn("[AE2E] {} base 文件在第 {}/{} 条目处读取失败 ({})。已保留 {} 条完整条目，"
                        + "将进入挽救流程（WAL 重放 + 隔离重建）: {}",
                    section.section, read, entryCount, e, target.size(), file.getAbsolutePath());
                return checkpointRev;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to load {} storage from file: {}. Entering safe mode (read-only).",
                section.section, file.getAbsolutePath(), e);
            markSectionFailed(section.section);
            return 0L;
        }
    }

    /**
     * 回放 WAL：只应用 rev &gt; baseRev 的记录（绝对值 last-wins，count=0 删除）。
     * 尾部不完整帧截断修复；中部损坏（长度/CRC 非法且不在末尾）置分区安全模式。
     */
    private void replayWal(SectionIO section, Map<Object, HugeCount> target, long baseRev) {
        File wal = section.walFile;
        if (!wal.exists() || wal.length() == 0) return;
        long maxAppliedRev = baseRev;
        long repairOffset = -1L;
        boolean corrupted = false;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(wal)))) {
            long fileSize = wal.length();
            long offset = 0;
            while (offset < fileSize) {
                long frameStart = offset;
                if (fileSize - offset < 8) {
                    repairOffset = frameStart; // 帧头都不完整 → 尾部截断
                    break;
                }
                int len = in.readInt();
                int crc = in.readInt();
                offset += 8;
                if (len <= 0 || len > MAX_WAL_FRAME_BYTES) {
                    if (offset == fileSize) repairOffset = frameStart; else corrupted = true;
                    break;
                }
                if (fileSize - offset < len) {
                    repairOffset = frameStart;
                    break;
                }
                byte[] payload = new byte[len];
                in.readFully(payload);
                offset += len;
                CRC32 crc32 = new CRC32();
                crc32.update(payload);
                if ((int) crc32.getValue() != crc) {
                    if (offset == fileSize) repairOffset = frameStart; else corrupted = true;
                    break;
                }
                maxAppliedRev = Math.max(maxAppliedRev, applyWalPayload(section, payload, target, baseRev));
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to replay {} WAL: {}", section.section, wal.getAbsolutePath(), e);
            markSectionFailed(section.section);
            return;
        }
        if (corrupted) {
            AE2Enhanced.LOGGER.error("[AE2E] {} WAL 中部损坏，分区进入安全模式: {}", section.section, wal.getAbsolutePath());
            markSectionFailed(section.section);
            return;
        }
        if (repairOffset >= 0) {
            repairWalTail(section, repairOffset);
        }
        // 有记录被回放 → 标记脏，等待空闲 checkpoint 固入 base 以截断 WAL
        if (maxAppliedRev > baseRev) {
            section.mutationRev = maxAppliedRev;
            section.lastMutationNanos = System.nanoTime();
            lastMutationNanos = System.nanoTime();
        }
    }

    /** 解析一帧 WAL payload 并应用，返回帧内最大 rev。 */
    private long applyWalPayload(SectionIO section, byte[] payload, Map<Object, HugeCount> target, long baseRev) throws IOException {
        long maxRev = baseRev;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != WAL_VERSION) {
                throw new IOException("未知 WAL 版本: " + version);
            }
            int recordCount = in.readInt();
            if (recordCount < 0) {
                throw new IOException("WAL 记录数为负");
            }
            for (int i = 0; i < recordCount; i++) {
                Object descriptor = readDescriptorEntry(section, in);
                HugeCount count = readCount(in);
                long rev = in.readLong();
                if (descriptor == null) {
                    continue;
                }
                if (rev <= baseRev) {
                    continue; // 已固入 base 的历史记录
                }
                if (count.isZero()) {
                    target.remove(descriptor);
                } else {
                    target.put(descriptor, count);
                }
                maxRev = Math.max(maxRev, rev);
            }
        }
        return maxRev;
    }

    /** 截断修复 WAL 尾部的不完整帧（崩溃在 force 中途的常见形态）。 */
    private void repairWalTail(SectionIO section, long validLength) {
        try (FileChannel channel = FileChannel.open(section.walFile.toPath(), StandardOpenOption.WRITE)) {
            channel.truncate(validLength);
            channel.force(true);
            AE2Enhanced.LOGGER.warn("[AE2E] 已截断 {} WAL 尾部不完整帧 @{}", section.section, validLength);
        } catch (IOException e) {
            AE2Enhanced.LOGGER.error("[AE2E] 无法修复 {} WAL: {}", section.section, section.walFile, e);
            markSectionFailed(section.section);
        }
    }

    /**
     * 将加载失败的分区记录为失败状态：该分区拒绝注入/提取，checkpoint 与 WAL 截断都跳过，
     * 避免"部分加载的 Map"在后续保存时覆写未能完整解析的文件导致永久数据丢失。
     */
    private void markSectionFailed(StorageSection section) {
        failedSections.add(section);
    }

    private long readAndValidateHeader(DataInputStream in, SectionIO section) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Invalid magic for " + section.section);
        }
        int version = in.readInt();
        if (version > CURRENT_VERSION) {
            throw new IOException("Version " + version + " > current " + CURRENT_VERSION + " for " + section.section);
        }
        if (version >= CURRENT_VERSION) {
            return in.readLong(); // checkpointRev
        }
        in.readInt(); // v1: flags, reserved
        return 0L;
    }

    public void recordChangeLocked(StorageSection section, Object descriptor, HugeCount newCount) {
        SectionIO io = sectionFor(section);
        if (io == null || !io.available()) return;
        if (!io.mutationLock.isHeldByCurrentThread()) {
            throw new IllegalStateException("WAL 绝对值必须与 Map mutation 持有同一分区锁");
        }
        if (closed || closeBlocked) {
            if (!closeLogged) {
                closeLogged = true;
                AE2Enhanced.LOGGER.warn("[AE2E] nexus {} 关闭中或已隔离，拒绝新的变更记录", nexusId);
            }
            return;
        }
        if (failedSections.contains(section)) return;
        long rev = ++io.mutationRev;
        if (!io.pending.containsKey(descriptor)) pendingTotal++;
        io.pending.put(descriptor, new WalVal(newCount, rev));
        io.lastMutationNanos = System.nanoTime();
        lastMutationNanos = System.nanoTime();
    }

    private HugeCount readCount(DataInputStream in) throws IOException {
        in.readByte(); // sign (BigInteger.toByteArray() 内嵌符号,跳过显式 sign)
        int magLen = in.readInt();
        if (magLen <= 0 || magLen > StorageConstants.MAX_ENTRY_BYTES) {
            throw new IOException("数量长度越界: " + magLen);
        }
        byte[] mag = new byte[magLen];
        in.readFully(mag);
        return HugeCount.fromByteArray(mag);
    }

    // ---- 变更记录（adapter 在 MODULATE 路径调用） ----

    /** 主线程 tick 只标记待冲刷；执行中或已排队时不会重复 submit。 */
    public void onServerTick() {
        if (closed || closeBlocked || pendingTotal == 0) return;
        requestIoFlush(false, null);
    }

    private void requestIoFlush(boolean force, IoCompletion completion) {
        synchronized (ioScheduleLock) {
            flushRequested = true;
            forceFlushRequested |= force;
            if (completion != null && !pendingCompletions.contains(completion)) {
                pendingCompletions.add(completion);
            }
            ensureIoTaskLocked();
        }
    }

    private void ensureIoTaskLocked() {
        if (ioQueued || ioRunning) return;
        ioQueued = true;
        try {
            flushExecutor.submit(this::runMergedIo);
        } catch (RuntimeException e) {
            ioQueued = false;
            AE2Enhanced.LOGGER.error("[AE2E] IO 周期提交失败（下轮重试）: {}", nexusId, e);
            for (IoCompletion completion : pendingCompletions) {
                completion.submissionFailed();
            }
            pendingCompletions.clear();
            if (loadRequested) {
                loadRequested = false;
                for (SectionIO io : allSections) markSectionFailed(io.section);
                loadFailed = true;
                loadFinished = true;
                loadLatch.countDown();
            }
        }
    }

    // ---- IO 周期（全部文件 IO 在 FLUSH_EXECUTOR 串行执行） ----

    /** 周期兜底与 tick/close 走完全相同的合并入口。 */
    private void periodicFlush() {
        if (closed || closeBlocked) return;
        try {
            requestIoFlush(false, null);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.error("[AE2E] Periodic storage flush failed for nexus {}", nexusId, t);
        }
    }

    private void runMergedIo() {
        final boolean runLoad;
        final boolean runFlush;
        final boolean force;
        final List<IoCompletion> completions;
        synchronized (ioScheduleLock) {
            ioQueued = false;
            ioRunning = true;
            runLoad = loadRequested;
            runFlush = flushRequested;
            force = forceFlushRequested;
            loadRequested = false;
            flushRequested = false;
            forceFlushRequested = false;
            completions = new ArrayList<>(pendingCompletions);
            pendingCompletions.clear();
        }
        IoCycleReport report = new IoCycleReport();
        try {
            if (runLoad) performLoad();
            if (runFlush && loadFinished) {
                writeWalBatches(drainAllPending(force), report);
                checkpointIfNeeded(force, report);
            }
        } catch (Throwable t) {
            report.walFailed = true;
            report.checkpointFailed = true;
            report.truncationFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] 合并 IO 周期失败: {}", nexusId, t);
        } finally {
            for (IoCompletion completion : completions) {
                if (completion.closeStreams) {
                    closeWalStreams(report);
                    break;
                }
            }
            for (IoCompletion completion : completions) completion.complete(report);
            synchronized (ioScheduleLock) {
                ioRunning = false;
                if (loadRequested || flushRequested) ensureIoTaskLocked();
            }
        }
    }

    private void closeWalStreams(IoCycleReport report) {
        for (SectionIO io : allSections) {
            if (io.walOut == null) continue;
            try {
                io.walOut.close();
            } catch (IOException e) {
                report.walFailed = true;
                AE2Enhanced.LOGGER.error("[AE2E] 关闭 {} WAL 流失败: {}", io.section, nexusId, e);
            } finally {
                io.walOut = null;
                io.walFileOut = null;
            }
        }
    }

    private List<WalBatch> drainAllPending(boolean force) {
        List<WalBatch> batches = new ArrayList<>();
        for (SectionIO io : allSections) {
            if (failedSections.contains(io.section)) continue;
            if (!force && System.nanoTime() < io.nextRetryNanos) continue;
            io.mutationLock.lock();
            List<Object> keys;
            List<WalVal> vals;
            try {
                if (io.pending.isEmpty()) continue;
                keys = new ArrayList<>(io.pending.size());
                vals = new ArrayList<>(io.pending.size());
                for (Map.Entry<Object, WalVal> e : io.pending.entrySet()) {
                    keys.add(e.getKey());
                    vals.add(e.getValue());
                }
                io.pending.clear();
                pendingTotal -= keys.size();
            } finally {
                io.mutationLock.unlock();
            }
            batches.add(new WalBatch(io, keys, vals));
        }
        return batches;
    }

    private void writeWalBatches(List<WalBatch> batches, IoCycleReport report) {
        for (WalBatch batch : batches) {
            SectionIO io = batch.section;
            if (failedSections.contains(io.section)) {
                if (report != null) report.walFailed = true;
                continue;
            }
            batch.walSizeBefore = io.walFile.exists() ? io.walFile.length() : 0L;
            try {
                testHooks.beforeWalWrite(io.section);
                if (testHooks.failWalPermanently(io.section)) {
                    throw new PermanentWalException("注入的永久 WAL 故障");
                }
                for (byte[] payload : serializeWalBatch(io, batch.keys, batch.vals)) {
                    writeWalFrame(io, payload);
                }
                forceWal(io); // 整批写完统一 force 一次，减少机械盘 fsync 次数
                io.walFailures = 0;
                io.nextRetryNanos = 0L;
            } catch (PermanentWalException e) {
                if (report != null) report.walFailed = true;
                rollbackWalTail(io, batch.walSizeBefore);
                AE2Enhanced.LOGGER.error("[AE2E] WAL 永久性写入失败，分区进入安全模式: {} ({})",
                    io.section, nexusId, e);
                markSectionFailed(io.section);
            } catch (IOException | RuntimeException e) {
                if (report != null) report.walFailed = true;
                if (failedSections.contains(io.section)) continue;
                rollbackWalTail(io, batch.walSizeBefore);
                io.walFailures++;
                long backoffMs = Math.min(1000L << Math.min(io.walFailures - 1, 6), MAX_WAL_BACKOFF_MS);
                io.nextRetryNanos = System.nanoTime() + backoffMs * 1_000_000L;
                AE2Enhanced.LOGGER.error("[AE2E] WAL 写入失败(第 {} 次, {} ms 后重试; 瞬时故障不触发安全模式, 数据保留在内存): {} ({})",
                    io.walFailures, backoffMs, io.section, nexusId, e);
                requeueBatch(io, batch.keys, batch.vals);
            }
        }
    }

    /** 写失败的批次并回 pending（同 key 较新 rev 优先）。 */
    private void requeueBatch(SectionIO io, List<Object> keys, List<WalVal> vals) {
        io.mutationLock.lock();
        try {
            for (int i = 0; i < keys.size(); i++) {
                WalVal existing = io.pending.get(keys.get(i));
                if (existing == null || existing.rev < vals.get(i).rev) {
                    if (existing == null) pendingTotal++;
                    io.pending.put(keys.get(i), vals.get(i));
                }
            }
        } finally {
            io.mutationLock.unlock();
        }
    }

    // ---- WAL 写入 ----

    /**
     * 判断并执行 checkpoint（仅在 IO 线程调用）：
     * <ul>
     *   <li>分区空闲超过 checkpointIdleSeconds 且有脏 → 正常轮（单轮乐观快照，不收敛则下轮）</li>
     *   <li>WAL 体积超过 walCheckpointThresholdBytes → 强制轮（多轮乐观快照，
     *       超过 {@value MAX_OPTIMISTIC_ROUNDS} 轮仍不收敛则持有 mutationLock 做有界停顿快照）</li>
     * </ul>
     * 每个收敛的分区独立截断其 WAL（记录 rev 均 &lt;= checkpointRev，回放会被跳过）。
     */
    private void checkpointIfNeeded(boolean forceClose, IoCycleReport report) {
        for (SectionIO io : allSections) {
            if (!io.available()) continue;
            if (failedSections.contains(io.section)) {
                if (forceClose && report != null) report.checkpointFailed = true;
                continue;
            }
            if (!io.isDirty()) continue;
            boolean idleEnough =
                (System.nanoTime() - io.lastMutationNanos) / 1_000_000L
                    >= com.github.aeddddd.ae2enhanced.config.ServerConfig.current().storage.checkpointIdleSeconds * 1000L;
            boolean overThreshold = io.walFile.exists()
                && io.walFile.length() >= com.github.aeddddd.ae2enhanced.config.ServerConfig.current().storage.walCheckpointThresholdBytes;
            if (!forceClose && !idleEnough && !overThreshold) continue;
            checkpointSection(io, forceClose || (overThreshold && !idleEnough));
            if (forceClose && io.checkpointRev != io.mutationRev && report != null) {
                report.checkpointFailed = true;
            }
        }
        for (SectionIO io : allSections) {
            if (io.available() && !failedSections.contains(io.section)
                    && io.checkpointRev == io.mutationRev
                    && io.walFile.exists() && io.walFile.length() > 0) {
                if (!truncateWal(io) && report != null) report.truncationFailed = true;
            }
        }
    }

    private void checkpointSection(SectionIO io, boolean busyForced) {
        for (int round = 1; ; round++) {
            long snapshotRev;
            List<Map.Entry<Object, HugeCount>> frozen;
            io.mutationLock.lock();
            try {
                snapshotRev = io.mutationRev;
                Map<Object, HugeCount> src = io.dataRef;
                frozen = new ArrayList<>(src == null ? 0 : src.size());
                if (src != null) {
                    for (Map.Entry<Object, HugeCount> entry : src.entrySet()) {
                        frozen.add(new java.util.AbstractMap.SimpleImmutableEntry<>(entry));
                    }
                }
            } finally {
                io.mutationLock.unlock();
            }
            if (!writeBaseSnapshot(io, snapshotRev, frozen)) return;
            if (io.mutationRev == snapshotRev) return;
            if (!busyForced) return;
        }
    }

    /**
     * 把一批变更序列化为若干 WAL 帧（按 {@link #WAL_BATCH_TARGET_BYTES} 切片，
     * 防止单 tick 大批量变更超过单帧上限）。序列化失败置分区安全模式并抛出。
     */
    private List<byte[]> serializeWalBatch(SectionIO io, List<Object> keys, List<WalVal> vals) throws IOException {
        try {
            List<byte[]> frames = new ArrayList<>();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(WAL_VERSION);
            out.writeInt(0); // recordCount 占位，切片时回填
            int frameRecords = 0;
            for (int i = 0; i < keys.size(); i++) {
                ByteArrayOutputStream descBaos = new ByteArrayOutputStream();
                DataOutputStream descOut = new DataOutputStream(descBaos);
                io.writeDescriptor(descOut, keys.get(i));
                descOut.flush();
                byte[] descBytes = descBaos.toByteArray();

                if (baos.size() + descBytes.length + 32 > WAL_BATCH_TARGET_BYTES && frameRecords > 0) {
                    frames.add(finishWalFrame(baos, frameRecords));
                    baos = new ByteArrayOutputStream(4096);
                    out = new DataOutputStream(baos);
                    out.writeInt(WAL_VERSION);
                    out.writeInt(0);
                    frameRecords = 0;
                }
                out.writeInt(descBytes.length);
                out.write(descBytes);
                writeCount(out, vals.get(i).count);
                out.writeLong(vals.get(i).rev);
                frameRecords++;
            }
            if (frameRecords > 0 || frames.isEmpty()) {
                frames.add(finishWalFrame(baos, frameRecords));
            }
            return frames;
        } catch (IOException | RuntimeException e) {
            // 序列化失败是永久性的（数据无法落盘），该分区只读保护，防止内存与盘脱节
            AE2Enhanced.LOGGER.error("[AE2E] WAL 序列化失败，分区进入安全模式: {} ({})", io.section, nexusId, e);
            markSectionFailed(io.section);
            throw e;
        }
    }

    /** 回填帧头记录数并产出帧字节。 */
    private static byte[] finishWalFrame(ByteArrayOutputStream baos, int recordCount) {
        byte[] payload = baos.toByteArray();
        // recordCount 位于 [version int32] 之后
        payload[4] = (byte) (recordCount >>> 24);
        payload[5] = (byte) (recordCount >>> 16);
        payload[6] = (byte) (recordCount >>> 8);
        payload[7] = (byte) recordCount;
        return payload;
    }

    /** 写一帧：[len][crc32][payload]；落盘 force 由批次末尾的 {@link #forceWal} 统一执行。 */
    private void writeWalFrame(SectionIO io, byte[] payload) throws IOException {
        if (payload.length == 0 || payload.length > MAX_WAL_FRAME_BYTES) {
            throw new PermanentWalException("WAL 帧大小非法: " + payload.length + " (" + io.section + ")");
        }
        if (io.walOut == null) {
            io.walFileOut = new FileOutputStream(io.walFile, true);
            io.walOut = new DataOutputStream(new BufferedOutputStream(io.walFileOut, 64 * 1024));
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        io.walOut.writeInt(payload.length);
        io.walOut.writeInt((int) crc.getValue());
        io.walOut.write(payload);
    }

    /** 批次末尾统一 flush + force(false)，保证本批帧落盘。 */
    private void forceWal(SectionIO io) throws IOException {
        if (io.walOut == null) return;
        io.walOut.flush();
        io.walFileOut.getChannel().force(false);
    }

    private void rollbackWalTail(SectionIO io, long validLength) {
        try {
            if (io.walOut != null) {
                io.walOut.close();
                io.walOut = null;
                io.walFileOut = null;
            }
            if (io.walFile.exists() && io.walFile.length() > validLength) {
                try (FileChannel channel = FileChannel.open(io.walFile.toPath(), StandardOpenOption.WRITE)) {
                    channel.truncate(validLength);
                    channel.force(true);
                }
            }
        } catch (IOException e) {
            AE2Enhanced.LOGGER.error("[AE2E] WAL 回滚失败: {} ({})", io.section, nexusId, e);
        }
    }

    /** entryCount 与条目均来自锁内同一 frozen 集合；序列化和原子替换不持有 mutationLock。 */
    private boolean writeBaseSnapshot(SectionIO io, long snapshotRev, List<Map.Entry<Object, HugeCount>> frozen) {
        File tmpFile = new File(io.baseFile.getAbsolutePath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
            testHooks.beforeCheckpoint(io.section);
            writeHeader(out, snapshotRev, frozen.size());
            for (Map.Entry<Object, HugeCount> entry : frozen) {
                writeEntry(out, io, entry.getKey(), entry.getValue());
            }
            out.flush();
        } catch (IOException | RuntimeException e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to write {} temp file: {}", io.section, tmpFile.getAbsolutePath(), e);
            deleteTempFile(tmpFile);
            return false;
        }
        if (!atomicMove(tmpFile, io.baseFile, io.section)) {
            deleteTempFile(tmpFile);
            return false;
        }
        io.checkpointRev = snapshotRev;
        return true;
    }

    // ---- Checkpoint（空闲或超阈值时把脏分区固入 base） ----

    private void deleteTempFile(File tmpFile) {
        try {
            Files.deleteIfExists(tmpFile.toPath());
        } catch (IOException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] 无法删除失败快照的临时文件: {}", tmpFile, e);
        }
    }

    private boolean truncateWal(SectionIO io) {
        try {
            testHooks.beforeTruncation(io.section);
            if (io.walOut != null) {
                io.walOut.close();
                io.walOut = null;
                io.walFileOut = null;
            }
            try (FileChannel channel = FileChannel.open(io.walFile.toPath(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            AE2Enhanced.LOGGER.error("[AE2E] 截断 {} WAL 失败: {}", io.section, io.walFile, e);
            return false;
        }
    }

    private void migrateFromOldFormat() {
        AE2Enhanced.LOGGER.info("[AE2E] Migrating old NBT format storage for nexus {} to new binary format", nexusId);
        try {
            NBTTagCompound root = CompressedStreamTools.read(oldFile);
            if (root == null) return;

            migrateNbtListToBinary(root, "items", itemSection, (tag, out) -> {
                ItemDescriptor d = ItemDescriptor.fromNBT(tag);
                if (d == null) {
                    PreservedDescriptor.writeLegacyItem(tag, out);
                } else {
                    ItemDescriptorCodec.INSTANCE.write(out, d);
                }
                return true;
            });
            migrateNbtListToBinary(root, "fluids", fluidSection, (tag, out) -> {
                FluidDescriptor d = FluidDescriptor.fromNBT(tag);
                if (d == null) {
                    PreservedDescriptor.writeLegacyFluid(tag, out);
                } else {
                    FluidDescriptorCodec.INSTANCE.write(out, d);
                }
                return true;
            });
            migrateNbtListToBinary(root, "energy", energySection, (tag, out) -> {
                EnergyDescriptor d = EnergyDescriptor.fromNBT(tag);
                if (d == null) return false;
                EnergyDescriptorCodec.INSTANCE.write(out, d);
                return true;
            });
            if (gasSection != null) {
                migrateReflective(root, "gases", gasSection, "com.github.aeddddd.ae2enhanced.storage.GasDescriptor");
            }
            if (essentiaSection != null) {
                migrateReflective(root, "essentias", essentiaSection, "com.github.aeddddd.ae2enhanced.storage.EssentiaDescriptor");
            }

            File backup = new File(oldFile.getAbsolutePath() + ".backup");
            Files.move(oldFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            AE2Enhanced.LOGGER.info("[AE2E] Migration complete. Old file backed up to {}", backup.getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to migrate old storage format for nexus {}", nexusId, e);
            safeMode = true;
        }
    }

    /** 立即冲刷并等待本轮完成；等待有界，不在调用线程执行文件 IO。 */
    public void flushNow() {
        if (closed && pendingTotal == 0) return;
        IoCompletion completion = new IoCompletion(false);
        requestIoFlush(true, completion);
        awaitIo(completion, closeTimeoutMs);
    }

    private boolean awaitIo(IoCompletion completion, long timeoutMs) {
        try {
            return completion.latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void writeHeader(DataOutputStream out, long checkpointRev, int entryCount) throws IOException {
        out.write(MAGIC);
        out.writeInt(CURRENT_VERSION);
        out.writeLong(checkpointRev);
        out.writeInt(entryCount);
    }

    private void writeEntry(DataOutputStream out, SectionIO io, Object descriptor, HugeCount count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream descOut = new DataOutputStream(baos);
        io.writeDescriptor(descOut, descriptor);
        descOut.flush();
        byte[] descBytes = baos.toByteArray();

        out.writeInt(descBytes.length);
        out.write(descBytes);
        writeCount(out, count);
    }

    private void writeCount(DataOutputStream out, HugeCount count) throws IOException {
        out.writeByte(count.isZero() ? 0 : 1);
        byte[] mag = count.toByteArray();
        out.writeInt(mag.length);
        out.write(mag);
    }

    private boolean atomicMove(File tmpFile, File targetFile, StorageSection section) {
        try {
            Files.move(tmpFile.toPath(), targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // 文件系统不支持原子替换（部分网络挂载/特殊文件系统）：降级为普通替换。
            // tmp 与目标同目录,绝大多数平台下 rename 本身就具备原子性。
            try {
                Files.move(tmpFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException e2) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to save {} storage file (non-atomic fallback): {}",
                    section, targetFile.getAbsolutePath(), e2);
                return false;
            }
        } catch (IOException e) {
            // 原子移动失败不置安全模式：仅记录日志并保留 dirty，下次 checkpoint 重试
            AE2Enhanced.LOGGER.error("[AE2E] Failed to save {} storage file: {}", section, targetFile.getAbsolutePath(), e);
            return false;
        }
    }

    // ---- Migration ----

    /**
     * 测试钩子：同步冲刷 WAL（不做 checkpoint），用于模拟"WAL 已写但未固化"的崩溃场景。
     */
    void debugFlushWalSync() {
        IoCompletion completion = new IoCompletion(false);
        requestIoFlush(false, completion);
        awaitIo(completion, closeTimeoutMs);
    }

    private void migrateReflective(NBTTagCompound root, String nbtKey, SectionIO section, String descriptorClassName) {
        try {
            Class<?> descClass = Class.forName(descriptorClassName);
            java.lang.reflect.Method fromNBT = descClass.getMethod("fromNBT", NBTTagCompound.class);
            migrateNbtListToBinary(root, nbtKey, section, (tag, out) -> {
                Object d = fromNBT.invoke(null, tag);
                if (d == null) return false;
                section.writeDescriptor(out, d);
                return true;
            });
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to migrate {} section", nbtKey, e);
        }
    }

    @FunctionalInterface
    private interface NbtEntryWriter {
        boolean write(NBTTagCompound tag, DataOutput out) throws Exception;
    }

    private void migrateNbtListToBinary(NBTTagCompound root, String nbtKey, SectionIO section, NbtEntryWriter writer) throws Exception {
        if (!root.hasKey(nbtKey, 9) || !section.available()) return;
        NBTTagList list = root.getTagList(nbtKey, 10);
        // 先把有效条目序列化到临时缓冲并统计实际条数，再按实际条数写头部；
        // 直接按 list.tagCount() 写头部再跳过无效条目会导致计数不符，下次加载 EOF
        ByteArrayOutputStream entriesBaos = new ByteArrayOutputStream();
        DataOutputStream entriesOut = new DataOutputStream(entriesBaos);
        int written = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            HugeCount count;
            try {
                count = HugeCount.of(new BigInteger(tag.getString("Count")));
            } catch (NumberFormatException e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Skipping migration entry {} of {}: invalid Count '{}'",
                        i, nbtKey, tag.getString("Count"));
                continue;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream descOut = new DataOutputStream(baos);
            if (!writer.write(tag, descOut)) {
                continue; // skip invalid entry
            }
            descOut.flush();
            byte[] descBytes = baos.toByteArray();
            entriesOut.writeInt(descBytes.length);
            entriesOut.write(descBytes);
            writeCount(entriesOut, count);
            written++;
        }
        entriesOut.flush();
        // 全部序列化成功后才写 tmp 并原子 rename，目标文件在迁移失败时保持不被破坏
        File tmpFile = new File(section.baseFile.getAbsolutePath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
            writeHeader(out, 0L, written);
            out.write(entriesBaos.toByteArray());
            out.flush();
        }
        Files.move(tmpFile.toPath(), section.baseFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- Lifecycle ----

    /** 关闭成功才释放 session；失败或超时保留现场，可由 Manager 再次调用恢复。 */
    public CloseResult close() {
        if (closed && lastCloseResult != null) return lastCloseResult;
        closeBlocked = true;
        if (flushTask != null) flushTask.cancel(false);
        beginLoad();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(closeTimeoutMs);
        CloseResult.Status loadStatus;
        try {
            long remaining = deadline - System.nanoTime();
            boolean loaded = remaining > 0 && loadLatch.await(remaining, TimeUnit.NANOSECONDS);
            if (!loaded) {
                return finishClose(new CloseResult(CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT,
                    CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT));
            }
            loadStatus = loadFailed ? CloseResult.Status.FAILED : CloseResult.Status.SUCCESS;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return finishClose(new CloseResult(CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT,
                CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT));
        }

        IoCompletion completion = new IoCompletion(true);
        requestIoFlush(true, completion);
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0 || !awaitIo(completion, TimeUnit.NANOSECONDS.toMillis(remaining))) {
            return finishClose(new CloseResult(loadStatus, CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT,
                CloseResult.Status.TIMEOUT, CloseResult.Status.TIMEOUT));
        }
        if (completion.submissionFailed) {
            return finishClose(new CloseResult(loadStatus, CloseResult.Status.FAILED, CloseResult.Status.FAILED,
                CloseResult.Status.FAILED, CloseResult.Status.FAILED));
        }
        IoCycleReport report = completion.report;
        CloseResult.Status pendingStatus = hasAnyPending() ? CloseResult.Status.FAILED : CloseResult.Status.SUCCESS;
        return finishClose(new CloseResult(loadStatus, pendingStatus,
            report.walFailed ? CloseResult.Status.FAILED : CloseResult.Status.SUCCESS,
            report.checkpointFailed ? CloseResult.Status.FAILED : CloseResult.Status.SUCCESS,
            report.truncationFailed ? CloseResult.Status.FAILED : CloseResult.Status.SUCCESS));
    }

    private CloseResult finishClose(CloseResult result) {
        synchronized (closeStateLock) {
            if (result.isSuccess()) {
                closed = true;
                lastCloseResult = result;
            } else if (!closed) {
                lastCloseResult = result;
            }
        }
        return result;
    }

    /**
     * 测试钩子：模拟进程崩溃——取消周期任务、关闭 WAL 流，但不写任何数据。
     * 等价于 kill -9 后文件系统里的残留状态（WAL 已 force 的部分保留）。
     */
    void debugSimulateCrash() {
        closed = true;
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        for (SectionIO io : allSections) {
            if (io.walOut != null) {
                try {
                    io.walOut.close();
                } catch (IOException ignored) {}
                io.walOut = null;
                io.walFileOut = null;
            }
        }
    }

    private boolean hasAnyPending() {
        for (SectionIO io : allSections) {
            if (io.hasPending()) return true;
        }
        return false;
    }

    public boolean isCloseBlocked() {
        return closeBlocked && !closed;
    }

    HyperdimensionalStorageTestHooks testHooks() {
        return testHooks;
    }

    private static final class IoCycleReport {
        boolean walFailed;
        boolean checkpointFailed;
        boolean truncationFailed;
    }

    public boolean isClosed() {
        return closed;
    }

    private static final class IoCompletion {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean closeStreams;
        volatile IoCycleReport report;
        volatile boolean submissionFailed;

        IoCompletion(boolean closeStreams) {
            this.closeStreams = closeStreams;
        }

        void complete(IoCycleReport result) {
            report = result;
            latch.countDown();
        }

        void submissionFailed() {
            submissionFailed = true;
            latch.countDown();
        }
    }

    public boolean isSafeMode() {
        return safeMode || !failedSections.isEmpty();
    }

    /**
     * 判断指定分区是否处于安全模式（加载失败或全局锁定）。
     * 处于安全模式的分区拒绝注入与提取，防止部分加载的数据被进一步缩水后覆盖原文件。
     */
    public boolean isSectionFailed(StorageSection section) {
        return safeMode || failedSections.contains(section);
    }

    public void setSafeMode(boolean safeMode) {
        this.safeMode = safeMode;
    }

    public UUID getNexusId() {
        return nexusId;
    }

    private static final class SectionIO {
        final StorageSection section;
        final File baseFile;
        final File walFile;
        /** 无条件分区为 DescriptorCodec 实例；条件分区为反射加载的 codec 单例 */
        final Object codec;
        /** 条件分区的反射方法（init 时缓存，避免每次读写 getMethod） */
        final java.lang.reflect.Method reflectRead;
        final java.lang.reflect.Method reflectWrite;
        /** 变更缓冲锁：pending 与 mutationRev 的访问均在此锁下 */
        final ReentrantLock mutationLock = new ReentrantLock();

        /** 权威数据 Map（由对应 adapter 持有，setXxxStorageRef 注入） */
        volatile Map<Object, HugeCount> dataRef;
        /** base 文件在条目循环中途截断/损坏（头部完好）：触发挽救流程（保留完整条目 + WAL 重放 + 隔离重建） */
        volatile boolean salvagedCorruptBase = false;
        /** WAL 待写缓冲：绝对值 last-wins 合并 */
        final Map<Object, WalVal> pending = new LinkedHashMap<>();
        /** 变更修订号（mutationLock 下递增；volatile 供快照无锁读取） */
        volatile long mutationRev;
        /** 已固入 base 的修订号 */
        volatile long checkpointRev;
        /** 该分区最近一次变更时间（空闲 checkpoint 判定用；分区级，避免热点分区饿死安静分区） */
        volatile long lastMutationNanos = Long.MIN_VALUE;
        /** 连续瞬时 WAL 写失败次数（退避用；瞬时故障不进安全模式） */
        int walFailures;
        /** 瞬时失败后下一次允许重试的时间（System.nanoTime 域） */
        long nextRetryNanos;
        /** WAL 输出流（跨 tick 保持打开，批量后 flush+force；仅在 IO 线程访问） */
        DataOutputStream walOut;
        FileOutputStream walFileOut;

        SectionIO(StorageSection section, File baseFile, File walFile, Object codec,
                  java.lang.reflect.Method reflectRead, java.lang.reflect.Method reflectWrite) {
            this.section = section;
            this.baseFile = baseFile;
            this.walFile = walFile;
            this.codec = codec;
            this.reflectRead = reflectRead;
            this.reflectWrite = reflectWrite;
        }

        boolean available() {
            return baseFile != null && codec != null;
        }

        boolean isDirty() {
            return checkpointRev != mutationRev || hasPending();
        }

        boolean hasPending() {
            mutationLock.lock();
            try {
                return !pending.isEmpty();
            } finally {
                mutationLock.unlock();
            }
        }

        void writeDescriptor(DataOutput out, Object descriptor) throws IOException {
            if (descriptor instanceof PreservedDescriptor) {
                ((PreservedDescriptor) descriptor).write(out);
                return;
            }
            if (reflectWrite == null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                DescriptorCodec typed = (DescriptorCodec) codec;
                typed.write(out, (Descriptor) descriptor);
            } else {
                try {
                    reflectWrite.invoke(codec, out, descriptor);
                } catch (ReflectiveOperationException e) {
                    throw new IOException("反射 codec 写入失败: " + section, e);
                }
            }
        }

        Object readDescriptor(DataInput in) throws IOException {
            if (reflectRead == null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                DescriptorCodec typed = (DescriptorCodec) codec;
                return typed.read(in);
            }
            try {
                return reflectRead.invoke(codec, in);
            } catch (ReflectiveOperationException e) {
                throw new IOException("反射 codec 读取失败: " + section, e);
            }
        }
    }

    // ---- StorageRef 注入（adapter 持有的权威 Map） ----

    @SuppressWarnings("unchecked")
    private void setRef(StorageSection section, Map<?, HugeCount> ref) {
        SectionIO io = sectionFor(section);
        if (io != null) {
            io.dataRef = (Map<Object, HugeCount>) ref;
        }
    }

    public void setStorageRef(Map<ItemDescriptor, HugeCount> ref) {
        setRef(StorageSection.ITEM, ref);
    }

    public void setFluidStorageRef(Map<FluidDescriptor, HugeCount> ref) {
        setRef(StorageSection.FLUID, ref);
    }

    public void setGasStorageRef(Map<?, HugeCount> ref) {
        setRef(StorageSection.GAS, ref);
    }

    public void setEssentiaStorageRef(Map<?, HugeCount> ref) {
        setRef(StorageSection.ESSENTIA, ref);
    }

    public void setEnergyStorageRef(Map<EnergyDescriptor, HugeCount> ref) {
        setRef(StorageSection.ENERGY, ref);
    }

    public void setManaStorageRef(Map<?, HugeCount> ref) {
        setRef(StorageSection.MANA, ref);
    }

    public void setStarlightStorageRef(Map<?, HugeCount> ref) {
        setRef(StorageSection.STARLIGHT, ref);
    }
}
