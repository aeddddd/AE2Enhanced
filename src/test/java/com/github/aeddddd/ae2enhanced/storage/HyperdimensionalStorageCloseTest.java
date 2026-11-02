package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class HyperdimensionalStorageCloseTest {

    private final List<TestExecutor> executors = new ArrayList<>();
    private final List<Faults> managerFaults = new ArrayList<>();
    @TempDir
    Path tempDir;

    @BeforeAll
    static void boot() throws Exception {
        AE2TestBootstrap.boot();
        try {
            com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.requireProcess();
        } catch (IllegalStateException absent) {
            com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.startProcess(
                    new com.github.aeddddd.ae2enhanced.runtime.ModProcessScope(
                            "ae2enhanced", "test", false,
                            com.github.aeddddd.ae2enhanced.config.BootConfig.capture()));
        }
        Field namedMods = net.minecraftforge.fml.common.Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(net.minecraftforge.fml.common.Loader.instance()) == null) {
            namedMods.set(net.minecraftforge.fml.common.Loader.instance(), new java.util.HashMap<>());
        }
        try {
            AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
        } catch (RuntimeException absent) {
            AEApi.instance().storage().registerStorageChannel(IEnergyStorageChannel.class, new EnergyStorageChannel());
        }
    }

    private static World world(Path dir) {
        World world = mock(World.class);
        ISaveHandler saves = mock(ISaveHandler.class);
        when(world.getSaveHandler()).thenReturn(saves);
        when(saves.getWorldDirectory()).thenReturn(dir.toFile());
        return world;
    }

    @SuppressWarnings("unchecked")
    private static void clearManagerSessions() throws Exception {
        Field sessionsField = HyperdimensionalStorageManager.class.getDeclaredField("SESSIONS");
        sessionsField.setAccessible(true);
        ((Map<String, HyperdimensionalStorageManager.NexusSession>) sessionsField.get(null)).clear();
    }

    private static void inject(ItemStorageAdapter adapter, int meta, long count) {
        IAEItemStack stack = channel().createStack(new ItemStack(Items.DYE, 1, meta));
        stack.setStackSize(count);
        adapter.injectItems(stack, Actionable.MODULATE, null);
    }

    private static long countOf(ItemStorageAdapter adapter, int meta) {
        IAEItemStack probe = channel().createStack(new ItemStack(Items.DYE, 1, meta));
        probe.setStackSize(Long.MAX_VALUE);
        IAEItemStack result = adapter.extractItems(probe, Actionable.SIMULATE, null);
        return result == null ? 0L : result.getStackSize();
    }

    private static IItemStorageChannel channel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @AfterEach
    void cleanup() throws Exception {
        for (Faults faults : managerFaults) faults.reset();
        HyperdimensionalStorageManager.closeAll();
        HyperdimensionalStorageManager.setSessionFactoryForTests(null);
        clearManagerSessions();
        for (TestExecutor executor : executors) {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IOException("storage test executor did not terminate before temp directory cleanup");
            }
        }
    }

    @Test
    void slowDiskCoalescesTicksAndCloseIntoOneTrailingTask() throws Exception {
        Fixture fixture = fixture(UUID.randomUUID());
        fixture.faults.blockWal = true;
        fixture.inject(4, 64);
        fixture.file.onServerTick();
        assertThat(fixture.faults.walEntered.await(5, TimeUnit.SECONDS)).isTrue();
        fixture.executor.submissions.set(0);

        ExecutorService closer = Executors.newSingleThreadExecutor();
        Future<CloseResult> close;
        try {
            for (int i = 0; i < 100; i++) fixture.file.onServerTick();
            close = closer.submit(fixture.file::close);
            Thread.sleep(30L);
            assertThat(fixture.executor.submissions).hasValue(0);
            assertThat(fixture.executor.getQueue()).isEmpty();
            fixture.faults.releaseWal.countDown();
            assertThat(close.get(5, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(fixture.executor.submissions.get()).isLessThanOrEqualTo(1);
        } finally {
            fixture.faults.releaseWal.countDown();
            closer.shutdownNow();
        }
    }

    @Test
    void stuckInitialLoadTimesOutWithoutDiscardingRecovery() throws Exception {
        UUID id = UUID.randomUUID();
        TestExecutor executor = executor();
        executor.gateFirstTask = true;
        Faults faults = new Faults();
        HyperdimensionalStorageFile file = new HyperdimensionalStorageFile(
            tempDir.toFile(), id, executor, faults, 50L);
        ItemStorageAdapter adapter = new ItemStorageAdapter(file);
        file.setStorageRef(adapter.getStorageMap());
        file.beginLoad();
        assertThat(executor.firstTaskEntered.await(5, TimeUnit.SECONDS)).isTrue();

        CloseResult timedOut = file.close();
        assertThat(timedOut.getInitialLoad()).isEqualTo(CloseResult.Status.TIMEOUT);
        assertThat(timedOut.getPending()).isEqualTo(CloseResult.Status.TIMEOUT);
        assertThat(file.isClosed()).isFalse();
        assertThat(file.isCloseBlocked()).isTrue();

        executor.firstTaskRelease.countDown();
        file.awaitLoaded();
        assertThat(file.close().isSuccess()).isTrue();
    }

    @Test
    void closeTimeoutDuringWalIsReportedAndRetryable() throws Exception {
        Fixture fixture = fixture(UUID.randomUUID(), 250L);
        fixture.inject(4, 42);
        fixture.faults.blockWal = true;
        CloseResult timedOut = fixture.file.close();
        assertThat(timedOut.getInitialLoad()).isEqualTo(CloseResult.Status.SUCCESS);
        assertThat(timedOut.getWal()).isEqualTo(CloseResult.Status.TIMEOUT);
        assertThat(timedOut.getCheckpoint()).isEqualTo(CloseResult.Status.TIMEOUT);
        assertThat(fixture.file.isClosed()).isFalse();

        fixture.faults.releaseWal.countDown();
        assertThat(fixture.file.close().isSuccess()).isTrue();
        Fixture reloaded = fixture(fixture.id);
        assertThat(reloaded.countOf(4)).isEqualTo(42L);
        assertThat(reloaded.file.close().isSuccess()).isTrue();
    }

    @Test
    void zeroPendingWithDirtyRevisionStillCheckpointsOnClose() throws Exception {
        Fixture fixture = fixture(UUID.randomUUID());
        fixture.inject(4, 42);
        fixture.file.onServerTick();
        fixture.file.debugFlushWalSync();
        assertThat(fixture.pendingTotal()).isZero();
        assertThat(fixture.mutationRev()).isGreaterThan(fixture.checkpointRev());

        assertThat(fixture.file.close().isSuccess()).isTrue();
        assertThat(fixture.basePath()).exists();
        assertThat(Files.size(fixture.walPath())).isZero();
    }

    @Test
    void transientWalFailureKeepsPendingAndDoesNotEnterSafeMode() {
        Fixture fixture = fixture(UUID.randomUUID());
        fixture.inject(4, 42);
        fixture.faults.transientWalFailures.set(1);

        CloseResult failed = fixture.file.close();
        assertThat(failed.getWal()).isEqualTo(CloseResult.Status.FAILED);
        assertThat(failed.getPending()).isEqualTo(CloseResult.Status.FAILED);
        assertThat(failed.getCheckpoint()).isEqualTo(CloseResult.Status.SUCCESS);
        assertThat(fixture.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        assertThat(fixture.file.isClosed()).isFalse();

        assertThat(fixture.file.close().isSuccess()).isTrue();
        Fixture reloaded = fixture(fixture.id);
        assertThat(reloaded.countOf(4)).isEqualTo(42L);
        assertThat(reloaded.file.close().isSuccess()).isTrue();
    }

    @Test
    void permanentWalFailureKeepsSafeModeAndOriginalFiles() throws Exception {
        Fixture fixture = fixture(UUID.randomUUID());
        fixture.inject(4, 42);
        fixture.file.flushNow();
        assertThat(fixture.basePath()).exists();
        long baseSize = Files.size(fixture.basePath());
        fixture.inject(1, 7);
        fixture.faults.permanentWal = true;

        CloseResult failed = fixture.file.close();
        assertThat(failed.getWal()).isEqualTo(CloseResult.Status.FAILED);
        assertThat(fixture.file.isSectionFailed(StorageSection.ITEM)).isTrue();
        assertThat(fixture.file.isClosed()).isFalse();
        assertThat(Files.size(fixture.basePath())).isEqualTo(baseSize);
        assertThat(fixture.walPath()).exists();
    }

    @Test
    void checkpointFailureIsNotCloseSuccessAndCanBeRetried() {
        Fixture fixture = fixture(UUID.randomUUID());
        fixture.inject(4, 42);
        fixture.faults.checkpointFailures.set(1);

        CloseResult failed = fixture.file.close();
        assertThat(failed.getCheckpoint()).isEqualTo(CloseResult.Status.FAILED);
        assertThat(failed.getWal()).isEqualTo(CloseResult.Status.SUCCESS);
        assertThat(fixture.file.isClosed()).isFalse();
        assertThat(fixture.file.close().isSuccess()).isTrue();
    }

    @Test
    void truncationFailureIsNotCloseSuccessAndCanBeRetried() throws Exception {
        Fixture fixture = fixture(UUID.randomUUID());
        fixture.inject(4, 42);
        fixture.faults.truncationFailures.set(1);

        CloseResult failed = fixture.file.close();
        assertThat(failed.getTruncation()).isEqualTo(CloseResult.Status.FAILED);
        assertThat(fixture.file.isClosed()).isFalse();
        assertThat(Files.size(fixture.walPath())).isGreaterThan(0L);
        assertThat(fixture.file.close().isSuccess()).isTrue();
        assertThat(Files.size(fixture.walPath())).isZero();
    }

    @Test
    void quarantineWriteFailureRetainsSessionAndNextAcquireRecovers() {
        UUID id = UUID.randomUUID();
        World world = world(tempDir);
        installManagerFactory();
        HyperdimensionalStorageManager.NexusSession first = HyperdimensionalStorageManager.acquire(world, id);
        Faults faults = managerFaults.get(managerFaults.size() - 1);
        inject(first.getItemAdapter(), 4, 42);
        HyperdimensionalStorageManager.release(first);
        faults.checkpointFailures.set(1);
        faults.quarantineFailures.set(1);

        HyperdimensionalStorageManager.closeAll();
        assertThat(HyperdimensionalStorageManager.sessionCountForTests()).isEqualTo(1);
        assertThat(StorageQuarantine.exists(tempDir.toFile(), id)).isFalse();
        assertThat(first.getFile().isClosed()).isFalse();

        HyperdimensionalStorageManager.NexusSession recovered = HyperdimensionalStorageManager.acquire(world, id);
        assertThat(recovered).isNotSameAs(first);
        assertThat(recovered.getFile().isCloseBlocked()).isFalse();
        HyperdimensionalStorageManager.release(recovered);
        HyperdimensionalStorageManager.closeAll();
        assertThat(HyperdimensionalStorageManager.sessionCountForTests()).isZero();
    }

    @Test
    void persistentQuarantineSurvivesAtoBtoARestartAndRestoresData() throws Exception {
        UUID id = UUID.randomUUID();
        Path rootA = tempDir.resolve("A");
        Path rootB = tempDir.resolve("B");
        World worldA = world(rootA);
        World worldB = world(rootB);
        installManagerFactory();

        HyperdimensionalStorageManager.NexusSession firstA = HyperdimensionalStorageManager.acquire(worldA, id);
        Faults faultsA = managerFaults.get(managerFaults.size() - 1);
        inject(firstA.getItemAdapter(), 4, 42);
        HyperdimensionalStorageManager.release(firstA);
        faultsA.checkpointFailures.set(2);
        HyperdimensionalStorageManager.closeAll();

        StorageQuarantine.Entry entry = StorageQuarantine.read(rootA.toFile(), id);
        assertThat(entry.key).endsWith(":" + id);
        assertThat(entry.reason).isEqualTo("close-all");
        assertThat(entry.result.getCheckpoint()).isEqualTo(CloseResult.Status.FAILED);
        assertThat(Files.exists(rootA.resolve("ae2enhanced/storage/" + id + "/items.wal"))).isTrue();

        HyperdimensionalStorageManager.NexusSession sessionB = HyperdimensionalStorageManager.acquire(worldB, id);
        HyperdimensionalStorageManager.release(sessionB);
        HyperdimensionalStorageManager.closeAll();
        assertThat(StorageQuarantine.exists(rootB.toFile(), id)).isFalse();
        assertThat(StorageQuarantine.exists(rootA.toFile(), id)).isTrue();

        clearManagerSessions();
        faultsA.reset();
        HyperdimensionalStorageManager.NexusSession recoveredA = HyperdimensionalStorageManager.acquire(worldA, id);
        recoveredA.getFile().awaitLoaded();
        assertThat(countOf(recoveredA.getItemAdapter(), 4)).isEqualTo(42L);
        assertThat(StorageQuarantine.exists(rootA.toFile(), id)).isFalse();
        HyperdimensionalStorageManager.release(recoveredA);
        HyperdimensionalStorageManager.closeAll();
        assertThat(HyperdimensionalStorageManager.sessionCountForTests()).isZero();
    }

    private Fixture fixture(UUID id) {
        return fixture(id, 5_000L);
    }

    private Fixture fixture(UUID id, long closeTimeoutMs) {
        TestExecutor executor = executor();
        return new Fixture(tempDir, id, executor, new Faults(), closeTimeoutMs);
    }

    private TestExecutor executor() {
        TestExecutor executor = new TestExecutor();
        executors.add(executor);
        return executor;
    }

    private void installManagerFactory() {
        HyperdimensionalStorageManager.setSessionFactoryForTests((key, world, id) -> {
            TestExecutor executor = executor();
            Faults faults = new Faults();
            managerFaults.add(faults);
            FileWorld worldFile = new FileWorld(world.getSaveHandler().getWorldDirectory());
            HyperdimensionalStorageFile file = new HyperdimensionalStorageFile(
                worldFile.dir, id, executor, faults, 200L);
            return new HyperdimensionalStorageManager.NexusSession(key, file);
        });
    }

    private static final class FileWorld {
        final java.io.File dir;

        FileWorld(java.io.File dir) {
            this.dir = dir;
        }
    }

    private static final class Faults implements HyperdimensionalStorageTestHooks {
        final AtomicInteger transientWalFailures = new AtomicInteger();
        final AtomicInteger checkpointFailures = new AtomicInteger();
        final AtomicInteger truncationFailures = new AtomicInteger();
        final AtomicInteger quarantineFailures = new AtomicInteger();
        final CountDownLatch walEntered = new CountDownLatch(1);
        final CountDownLatch releaseWal = new CountDownLatch(1);
        volatile boolean permanentWal;
        volatile boolean blockWal;

        @Override
        public void beforeWalWrite(StorageSection section) throws IOException {
            if (section != StorageSection.ITEM) return;
            if (blockWal) {
                walEntered.countDown();
                try {
                    if (!releaseWal.await(5, TimeUnit.SECONDS)) throw new IOException("test WAL latch timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException(e);
                }
            }
            if (transientWalFailures.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
                throw new IOException("injected transient WAL failure");
            }
        }

        @Override
        public boolean failWalPermanently(StorageSection section) {
            return section == StorageSection.ITEM && permanentWal;
        }

        @Override
        public void beforeCheckpoint(StorageSection section) throws IOException {
            if (section == StorageSection.ITEM
                    && checkpointFailures.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
                throw new IOException("injected checkpoint failure");
            }
        }

        @Override
        public void beforeTruncation(StorageSection section) throws IOException {
            if (section == StorageSection.ITEM
                    && truncationFailures.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
                throw new IOException("injected truncation failure");
            }
        }

        @Override
        public void beforeQuarantineWrite() throws IOException {
            if (quarantineFailures.getAndUpdate(v -> Math.max(0, v - 1)) > 0) {
                throw new IOException("injected quarantine failure");
            }
        }

        void reset() {
            transientWalFailures.set(0);
            checkpointFailures.set(0);
            truncationFailures.set(0);
            quarantineFailures.set(0);
            permanentWal = false;
            blockWal = false;
            releaseWal.countDown();
        }
    }

    private static final class TestExecutor extends ScheduledThreadPoolExecutor {
        final AtomicInteger submissions = new AtomicInteger();
        final AtomicBoolean gate = new AtomicBoolean();
        volatile boolean gateFirstTask;
        volatile CountDownLatch firstTaskEntered = new CountDownLatch(1);
        volatile CountDownLatch firstTaskRelease = new CountDownLatch(1);

        TestExecutor() {
            super(1, new DaemonThreadFactory());
            setRemoveOnCancelPolicy(true);
        }

        @Override
        public Future<?> submit(Runnable command) {
            submissions.incrementAndGet();
            return super.submit(wrap(command));
        }

        @Override
        public void execute(Runnable command) {
            submissions.incrementAndGet();
            super.execute(wrap(command));
        }

        private Runnable wrap(Runnable command) {
            return () -> {
                if (gateFirstTask && gate.compareAndSet(false, true)) {
                    firstTaskEntered.countDown();
                    try {
                        if (!firstTaskRelease.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("test initial-load latch timeout");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                command.run();
            };
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "AE2E-Close-Test");
            thread.setDaemon(true);
            return thread;
        }
    }

    private final class Fixture {
        final UUID id;
        final HyperdimensionalStorageFile file;
        final ItemStorageAdapter adapter;
        final TestExecutor executor;
        final Faults faults;

        Fixture(Path worldDir, UUID id, TestExecutor executor, Faults faults, long closeTimeoutMs) {
            this.id = id;
            this.executor = executor;
            this.faults = faults;
            this.file = new HyperdimensionalStorageFile(worldDir.toFile(), id, executor, faults, closeTimeoutMs);
            this.adapter = new ItemStorageAdapter(file);
            file.setStorageRef(adapter.getStorageMap());
            file.beginLoad();
            file.awaitLoaded();
        }

        void inject(int meta, long count) {
            HyperdimensionalStorageCloseTest.inject(adapter, meta, count);
        }

        long countOf(int meta) {
            return HyperdimensionalStorageCloseTest.countOf(adapter, meta);
        }

        Path basePath() {
            return tempDir.resolve("ae2enhanced/storage/" + id + "/items.bin");
        }

        Path walPath() {
            return tempDir.resolve("ae2enhanced/storage/" + id + "/items.wal");
        }

        int pendingTotal() throws Exception {
            Field field = HyperdimensionalStorageFile.class.getDeclaredField("pendingTotal");
            field.setAccessible(true);
            return field.getInt(file);
        }

        long mutationRev() throws Exception {
            return revision("mutationRev");
        }

        long checkpointRev() throws Exception {
            return revision("checkpointRev");
        }

        private long revision(String name) throws Exception {
            Field sectionField = HyperdimensionalStorageFile.class.getDeclaredField("itemSection");
            sectionField.setAccessible(true);
            Object section = sectionField.get(file);
            Field value = section.getClass().getDeclaredField(name);
            value.setAccessible(true);
            return value.getLong(section);
        }
    }
}
