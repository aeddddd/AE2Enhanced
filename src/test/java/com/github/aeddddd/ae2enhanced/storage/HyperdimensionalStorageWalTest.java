package com.github.aeddddd.ae2enhanced.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * 超维度仓储持久化 v2（base 快照 + WAL）端到端测试.
 *
 * <p>全部用真实生产类（HyperdimensionalStorageFile / ItemStorageAdapter）驱动，
 * 覆盖：WAL 崩溃重放、checkpoint 收敛与截断、优雅关闭、删除语义、
 * 尾部撕裂修复、中部损坏进安全模式。</p>
 */
public class HyperdimensionalStorageWalTest {

    @TempDir
    public Path tempDir;

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    // ---- 工具 ----

    private static final class Fixture {
        final HyperdimensionalStorageFile file;
        final ItemStorageAdapter adapter;

        Fixture(Path worldDir, UUID id) {
            this.file = new HyperdimensionalStorageFile(worldDir.toFile(), id);
            this.adapter = new ItemStorageAdapter(file);
            file.setStorageRef(adapter.getStorageMap());
            file.beginLoad();    // 注册完成后启动异步首加载
            file.awaitLoaded();  // 测试语义保持同步：构造返回时加载已完成
        }

        void inject(String ignored, int meta, long count) {
            inject(new ItemStack(Items.DYE, 1, meta), count);
        }

        void inject(ItemStack stack, long count) {
            IAEItemStack ae = channel().createStack(stack);
            ae.setStackSize(count);
            adapter.injectItems(ae, Actionable.MODULATE, null);
        }

        void injectNbt(String key, String value, long count) {
            ItemStack stack = new ItemStack(Items.DYE, 1, 7);
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setString(key, value);
            stack.setTagCompound(tag);
            inject(stack, count);
        }

        long countOf(int meta) {
            IAEItemStack probe = channel().createStack(new ItemStack(Items.DYE, 1, meta));
            probe.setStackSize(Long.MAX_VALUE);
            IAEItemStack got = adapter.extractItems(probe, Actionable.SIMULATE, null);
            return got == null ? 0L : got.getStackSize();
        }

        long countOfNbt(String key, String value) {
            ItemStack stack = new ItemStack(Items.DYE, 1, 7);
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setString(key, value);
            stack.setTagCompound(tag);
            IAEItemStack probe = channel().createStack(stack);
            probe.setStackSize(Long.MAX_VALUE);
            IAEItemStack got = adapter.extractItems(probe, Actionable.SIMULATE, null);
            return got == null ? 0L : got.getStackSize();
        }

        void extractAll(int meta) {
            IAEItemStack probe = channel().createStack(new ItemStack(Items.DYE, 1, meta));
            probe.setStackSize(Long.MAX_VALUE);
            adapter.extractItems(probe, Actionable.MODULATE, null);
        }

        void extractSome(int meta, long amount) {
            IAEItemStack probe = channel().createStack(new ItemStack(Items.DYE, 1, meta));
            probe.setStackSize(amount);
            adapter.extractItems(probe, Actionable.MODULATE, null);
        }

        void tickAndFlushWal() {
            file.onServerTick();
            file.debugFlushWalSync();
        }

        private static IItemStorageChannel channel() {
            return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        }
    }

    private Path walPath(UUID id) {
        return tempDir.resolve("ae2enhanced/storage/" + id + "/items.wal");
    }

    private Path basePath(UUID id) {
        return tempDir.resolve("ae2enhanced/storage/" + id + "/items.bin");
    }

    // ---- 用例 ----

    /** WAL 已写但未 checkpoint 时崩溃：重开后从 WAL 完整回放。 */
    @Test
    public void walReplayAfterCrash() {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("dye-black", 4, 64);
        f1.inject("dye-red", 1, 128);
        f1.injectNbt("k", "v", 1000);
        f1.tickAndFlushWal();
        assertThat(basePath(id).toFile().exists()).as("未 checkpoint 时不应有 base 文件").isFalse();
        // 模拟崩溃（kill -9）：周期任务取消、WAL 流关闭，已 force 的数据保留
        f1.file.debugSimulateCrash();

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(64L);
        assertThat(f2.countOf(1)).isEqualTo(128L);
        assertThat(f2.countOfNbt("k", "v")).isEqualTo(1000L);
        assertThat(f2.adapter.getTotalCount().toLongSaturated()).isEqualTo(1192L);
        assertThat(f2.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        f2.file.close();
    }

    /** checkpoint 收敛后 WAL 截断；随后新增再崩溃：base+WAL 分层回放。 */
    @Test
    public void checkpointThenLayeredReplay() {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 4, 64);
        f1.file.flushNow(); // 强制 checkpoint
        assertThat(basePath(id).toFile().exists()).isTrue();
        assertThat(walPath(id).toFile().length()).as("收敛后 WAL 应被截断").isEqualTo(0L);

        f1.inject("b", 1, 10); // checkpoint 后的新变更只进 WAL
        f1.tickAndFlushWal();
        f1.file.debugSimulateCrash();

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(64L);
        assertThat(f2.countOf(1)).isEqualTo(10L);
        f2.file.close();
    }

    /** 优雅关闭：close 后重开数据完整。 */
    @Test
    public void gracefulCloseReload() {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 4, 42);
        f1.file.close();

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(42L);
        assertThat(walPath(id).toFile().length()).isEqualTo(0L);
        f2.file.close();
    }

    /** 提取到 0 的删除语义可持久化（WAL count=0 记录回放时移除）。 */
    @Test
    public void extractToZeroPersistsRemoval() {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 4, 64);
        f1.file.flushNow();
        f1.extractAll(4);
        f1.tickAndFlushWal();
        f1.file.debugSimulateCrash();

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(0L);
        f2.file.close();
    }

    /** WAL 尾部撕裂（崩溃在 force 中途）：截断修复，已提交部分不丢，不进安全模式。 */
    @Test
    public void tornWalTailRepaired() throws IOException {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 4, 64);
        f1.tickAndFlushWal();
        f1.file.debugSimulateCrash();

        // 追加半截垃圾模拟撕裂尾部
        try (RandomAccessFile raf = new RandomAccessFile(walPath(id).toFile(), "rw")) {
            raf.seek(raf.length());
            raf.writeInt(100); // 声明 100B 帧
            raf.write(new byte[]{1, 2, 3}); // 实际只有 3B
        }

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(64L);
        assertThat(f2.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        f2.file.close();
    }

    /** WAL 中部 CRC 损坏（非尾部）：分区进入安全模式，数据拒绝进一步缩水。 */
    @Test
    public void midWalCorruptionEntersSafeMode() throws IOException {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 4, 64);
        f1.tickAndFlushWal();
        f1.inject("b", 1, 32); // 第二帧
        f1.tickAndFlushWal();
        f1.file.debugSimulateCrash();

        // 破坏第一帧 payload 的一个字节（帧 0 不在末尾 → 中部损坏）
        try (RandomAccessFile raf = new RandomAccessFile(walPath(id).toFile(), "rw")) {
            raf.seek(10); // 帧头 8B 之后的 payload 区域
            raf.write(raf.read() ^ 0xFF);
        }

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.file.isSectionFailed(StorageSection.ITEM)).isTrue();
        f2.file.close();
    }

    /** BigInteger 域数量（黑洞场景）在 WAL/base 间往返不失真。 */
    @Test
    public void hugeBigIntegerCountsRoundTrip() {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        // 直接注入超大数量：adapter 上限为 long,但存储语义支持 BigInteger 级
        // 通过多次注入逼近; 这里验证 long 上限边缘值
        f1.inject("a", 4, Long.MAX_VALUE - 7);
        f1.inject("a", 4, 8); // 溢出到 BigInteger 域
        f1.tickAndFlushWal();
        f1.file.debugSimulateCrash();

        Fixture f2 = new Fixture(tempDir, id);
        HugeCount total = f2.adapter.getStorageMap().values().iterator().next();
        assertThat(total.toBigInteger()).isEqualTo(
            java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE));
        assertThat(f2.countOf(4)).isEqualTo(Long.MAX_VALUE); // 视图截断
        f2.file.close();
    }

    /** 同 key 跨多 tick 多帧混合变更（注入+提取+再注入）：回放按 last-wins 精确到最终值。 */
    @Test
    public void mixedOperationsLastWinsExact() {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 4, 1000);
        f1.tickAndFlushWal();          // 帧 1: a=1000
        f1.file.flushNow();            // checkpoint: base 固化 a=1000, WAL 截断

        f1.inject("a", 4, 500);        // a=1500
        f1.tickAndFlushWal();          // 帧 2: a=1500
        f1.extractSome(4, 700);        // a=800
        f1.tickAndFlushWal();          // 帧 3: a=800
        f1.inject("a", 4, 1);          // a=801（还在 pending,未写 WAL）
        f1.file.debugSimulateCrash();  // 崩溃：帧 2/3 已 force,最后 +1 丢失（1 tick 窗口内）

        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(801L - 1L); // 最后一次注入在耐久窗口内丢失属预期
        // 帧 2/3 的正确性：再回放一次以确认无重复应用
        f2.file.close();
        Fixture f3 = new Fixture(tempDir, id);
        assertThat(f3.countOf(4)).isEqualTo(800L);
        f3.file.close();
    }

    /** v1 旧版 base 文件（无 checkpointRev 的头部）在 v2 代码下完整读取（升级路径）。 */
    @Test
    public void legacyV1BaseFileLoads() throws IOException {
        UUID id = UUID.randomUUID();
        // 手工写一个 v1 格式文件： magic, version=1, flags=0, entryCount, 条目...
        File dir = tempDir.resolve("ae2enhanced/storage/" + id).toFile();
        dir.mkdirs();
        java.io.ByteArrayOutputStream entriesBaos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream entriesOut = new java.io.DataOutputStream(entriesBaos);
        ItemDescriptor d = new ItemDescriptor(new ItemStack(Items.DYE, 1, 4));
        java.io.ByteArrayOutputStream descBaos = new java.io.ByteArrayOutputStream();
        com.github.aeddddd.ae2enhanced.storage.codec.ItemDescriptorCodec.INSTANCE
            .write(new java.io.DataOutputStream(descBaos), d);
        byte[] descBytes = descBaos.toByteArray();
        entriesOut.writeInt(descBytes.length);
        entriesOut.write(descBytes);
        entriesOut.writeByte(1); // sign
        byte[] mag = HugeCount.of(777).toByteArray();
        entriesOut.writeInt(mag.length);
        entriesOut.write(mag);
        entriesOut.flush();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(new java.io.FileOutputStream(new File(dir, "items.bin"))))) {
            out.write("AE2E".getBytes("US-ASCII"));
            out.writeInt(1); // v1
            out.writeInt(0); // flags
            out.writeInt(1); // entryCount
            out.write(entriesBaos.toByteArray());
        }

        Fixture f = new Fixture(tempDir, id);
        assertThat(f.countOf(4)).isEqualTo(777L);
        assertThat(f.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        f.file.close();
        // close 后 base 被重写为 v2 头部（下次加载走 v2 分支），数据不变
        Fixture f2 = new Fixture(tempDir, id);
        assertThat(f2.countOf(4)).isEqualTo(777L);
        f2.file.close();
    }

    /**
     * 能量分区（单例零字段描述符,descLen=0 为合法编码）端到端：
     * 注入/提取、BigInteger 域计数、崩溃重放、优雅关闭。
     * 回归防护：descLen=0 不得被判为损坏（历史事故：越界校验误伤单例分区）。
     */
    @Test
    public void energySectionRoundTrip() {
        // 无头环境 Loader.isModLoaded 不可用,直接注册能量通道
        appeng.api.AEApi.instance().storage().registerStorageChannel(
            com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel.class,
            new com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageChannel());

        UUID id = UUID.randomUUID();
        HyperdimensionalStorageFile f1 = new HyperdimensionalStorageFile(tempDir.toFile(), id);
        HyperdimensionalEnergyStorageAdapter a1 = new HyperdimensionalEnergyStorageAdapter(f1);
        f1.setEnergyStorageRef(a1.getStorageMap());
        f1.beginLoad();

        long inject1 = 5_000_000_000L;
        a1.injectItems(com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack.create(inject1),
            appeng.api.config.Actionable.MODULATE, null);
        assertThat(a1.getTotalCount().toLongSaturated()).isEqualTo(inject1);

        // SIMULATE 不扣减; MODULATE 部分提取
        com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack sim =
            a1.extractItems(com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack.create(inject1),
                appeng.api.config.Actionable.SIMULATE, null);
        assertThat(sim).isNotNull();
        assertThat(sim.getStackSize()).isEqualTo(inject1);
        a1.extractItems(com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack.create(2_000_000_000L),
            appeng.api.config.Actionable.MODULATE, null);
        assertThat(a1.getTotalCount().toLongSaturated()).isEqualTo(inject1 - 2_000_000_000L);

        // 推进 BigInteger 域后崩溃
        a1.injectItems(com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack.create(Long.MAX_VALUE),
            appeng.api.config.Actionable.MODULATE, null);
        HugeCount total = a1.getTotalCount();
        assertThat(total.isBig()).isTrue();
        f1.onServerTick();
        f1.debugFlushWalSync();
        f1.debugSimulateCrash();

        HyperdimensionalStorageFile f2 = new HyperdimensionalStorageFile(tempDir.toFile(), id);
        HyperdimensionalEnergyStorageAdapter a2 = new HyperdimensionalEnergyStorageAdapter(f2);
        f2.beginLoad();
        f2.awaitLoaded(); // 等异步首加载完成（recalcTotal 钩子执行后再读总数）
        assertThat(a2.getTotalCount().toBigInteger()).isEqualTo(total.toBigInteger());
        assertThat(f2.isSectionFailed(StorageSection.ENERGY)).isFalse();
        f2.close();

        HyperdimensionalStorageFile f3 = new HyperdimensionalStorageFile(tempDir.toFile(), id);
        HyperdimensionalEnergyStorageAdapter a3 = new HyperdimensionalEnergyStorageAdapter(f3);
        f3.beginLoad();
        f3.awaitLoaded();
        assertThat(a3.getTotalCount().toBigInteger()).isEqualTo(total.toBigInteger());
        f3.close();
    }

    /**
     * 截断 base 的自动挽救：头部完好但尾部条目截断时，
     * 完整条目保留 + WAL 重放叠加 + 损坏文件隔离 + 新 base 重建，不进安全模式。
     */
    @Test
    public void truncatedBaseSalvage() throws IOException {
        UUID id = UUID.randomUUID();
        Fixture f1 = new Fixture(tempDir, id);
        f1.inject("a", 1, 111);
        f1.inject("b", 2, 222);
        f1.inject("c", 3, 333);
        f1.file.flushNow(); // base 固化 3 条目, WAL 截断
        // WAL 新增一代（崩溃重放应能恢复它）
        f1.inject("d", 9, 999);
        f1.tickAndFlushWal();
        f1.file.debugSimulateCrash();

        // 截断 items.bin：切掉尾部 5 字节（最后一条目不完整）
        File base = basePath(id).toFile();
        try (RandomAccessFile raf = new RandomAccessFile(base, "rw")) {
            raf.setLength(raf.length() - 5);
        }

        Fixture f2 = new Fixture(tempDir, id);
        // 挽救路径：不进安全模式
        assertThat(f2.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        // 完整条目保留 2 条（尾部被截的 1 条丢失属有界损失）
        long preservedTypes = f2.adapter.getStorageMap().keySet().stream()
            .filter(d -> d.getMeta() == 1 || d.getMeta() == 2 || d.getMeta() == 3).count();
        assertThat(preservedTypes).isEqualTo(2L);
        // WAL 重放恢复最新一代
        assertThat(f2.countOf(9)).isEqualTo(999L);
        // 损坏文件被隔离（重命名保留取证）
        File[] quarantined = base.getParentFile().listFiles((dir, name) -> name.startsWith("items.bin.corrupt-"));
        assertThat(quarantined).isNotNull().isNotEmpty();

        // 挽救性 checkpoint 异步执行后,新 base 含挽救态且再次加载一致
        f2.file.flushNow();
        Fixture f3 = new Fixture(tempDir, id);
        assertThat(f3.countOf(9)).isEqualTo(999L);
        assertThat(f3.file.isSectionFailed(StorageSection.ITEM)).isFalse();
        f3.file.close();
        f2.file.close();
    }
}
