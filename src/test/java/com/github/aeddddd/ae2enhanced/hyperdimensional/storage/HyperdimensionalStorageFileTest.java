package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.EnergyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.ItemDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;
import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HyperdimensionalStorageFile} 单元测试.
 * <p>使用 mock 的 {@link MinecraftServer}：世界目录指向临时文件夹,
 * {@code execute} 同步执行回调,以便等待异步写入完成.</p>
 */
class HyperdimensionalStorageFileTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    @TempDir
    Path worldDir;

    /**
     * 等待静态异步 I/O 线程排空:异步写入未结束时文件句柄仍被占用,
     * Windows 下 @TempDir 清理会失败(负载相关偶发);执行器为懒重建,关停无副作用.
     */
    @AfterEach
    void awaitAsyncWrites() {
        HyperdimensionalStorageFile.shutdown();
    }

    private MinecraftServer server;
    private UUID nexusId;
    private Path storageDir;

    @BeforeEach
    void setUp() {
        server = mock(MinecraftServer.class);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(worldDir);
        // 保存完成后的主线程回调直接同步执行
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(server).execute(any(Runnable.class));

        nexusId = UUID.randomUUID();
        storageDir = HyperdimensionalStorageFile.getStorageDirectory(server, nexusId);
    }

    @Test
    void testStorageDirectoryPaths() {
        // 目录结构为 <world>/ae2enhanced/storage/<nexusId>
        Path expected = worldDir.resolve("ae2enhanced").resolve("storage").resolve(nexusId.toString());
        assertEquals(expected, storageDir);
        assertEquals(worldDir.resolve("ae2enhanced").resolve("storage").resolve(nexusId + ".dat"),
                HyperdimensionalStorageFile.getLegacyStoragePath(server, nexusId));
    }

    @Test
    void testDirtyGenerationLifecycle() {
        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);

        assertFalse(file.isDirty());
        assertEquals(0, file.getDirtyGeneration(AEKeyType.items()));

        // 每次 markDirty 递增代际
        file.markDirty(AEKeyType.items());
        assertEquals(1, file.getDirtyGeneration(AEKeyType.items()));
        file.markDirty(AEKeyType.items());
        assertEquals(2, file.getDirtyGeneration(AEKeyType.items()));
        assertTrue(file.isDirty());
        assertTrue(file.isDirty(AEKeyType.items(), 2));
        // 旧代际不再视为脏
        assertFalse(file.isDirty(AEKeyType.items(), 1));

        // 代际不匹配时 markClean 不清除脏状态
        file.markClean(AEKeyType.items(), 1);
        assertTrue(file.isDirty(AEKeyType.items(), 2));

        // 代际匹配时清除
        file.markClean(AEKeyType.items(), 2);
        assertFalse(file.isDirty());

        // 全局 markClean 清空所有 section
        file.markDirty(AEKeyType.items());
        file.markDirty(AEKeyType.fluids());
        file.markClean();
        assertFalse(file.isDirty());

        // null 类型安全忽略
        file.markDirty(null);
        file.markClean(null, 1);
        assertEquals(0, file.getDirtyGeneration(null));
    }

    @Test
    void testSaveLoadSectionRoundTripV3() throws Exception {
        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        ItemDescriptor stone = new ItemDescriptor(AEItemKey.of(Items.STONE));
        Map<ItemDescriptor, BigInteger> entries = Map.of(stone, BigInteger.valueOf(64L));

        file.markDirty(AEKeyType.items());
        int generation = file.getDirtyGeneration(AEKeyType.items());
        file.saveSection(AEKeyType.items(), generation, ItemDescriptorCodec.INSTANCE, entries);
        file.awaitPendingWrites();

        // 保存成功后脏标记被清除,并生成 items.bin
        assertFalse(file.isDirty());
        assertTrue(Files.exists(storageDir.resolve("items.bin")));

        // 读回的条目与写入一致
        Map<ItemDescriptor, BigInteger> loaded = new HashMap<>();
        file.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, loaded::put);
        assertEquals(BigInteger.valueOf(64L), loaded.get(stone));
    }

    @Test
    void testSaveSectionSkippedWhenNotDirty() {
        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        ItemDescriptor stone = new ItemDescriptor(AEItemKey.of(Items.STONE));

        // 未标脏（代际为 0）时保存被跳过,不产生任何文件
        file.saveSection(AEKeyType.items(), 0, ItemDescriptorCodec.INSTANCE,
                Map.of(stone, BigInteger.ONE));

        assertFalse(Files.exists(storageDir.resolve("items.bin")));
    }

    @Test
    void testSafeModeBlocksSave() throws Exception {
        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        enterSafeModeWithCorruptFile(file);

        // 安全模式下保存请求被忽略
        file.markDirty(AEKeyType.items());
        int generation = file.getDirtyGeneration(AEKeyType.items());
        file.saveSection(AEKeyType.items(), generation, ItemDescriptorCodec.INSTANCE,
                Map.of(new ItemDescriptor(AEItemKey.of(Items.STONE)), BigInteger.ONE));

        assertFalse(Files.exists(storageDir.resolve("items.bin")));
    }

    @Test
    void testCorruptFileEntersSafeModeAndBackup() throws Exception {
        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        Path itemsBin = enterSafeModeWithCorruptFile(file);

        assertTrue(file.isSafeMode());
        // 损坏文件被移动到 .corrupt 备份,原路径不再存在
        assertFalse(Files.exists(itemsBin));
        assertTrue(Files.exists(storageDir.resolve("items.bin.corrupt")));
    }

    @Test
    void testCrcMismatchEntersSafeMode() throws Exception {
        // 先做一次合法保存
        HyperdimensionalStorageFile writer = HyperdimensionalStorageFile.forNexus(server, nexusId);
        writer.markDirty(AEKeyType.items());
        writer.saveSection(AEKeyType.items(), writer.getDirtyGeneration(AEKeyType.items()),
                ItemDescriptorCodec.INSTANCE,
                Map.of(new ItemDescriptor(AEItemKey.of(Items.STONE)), BigInteger.valueOf(64L)));
        writer.awaitPendingWrites();

        // 篡改文件内容中的一个字节,使 CRC32 校验失败
        Path itemsBin = storageDir.resolve("items.bin");
        byte[] data = Files.readAllBytes(itemsBin);
        data[10] ^= 0xFF;
        Files.write(itemsBin, data);

        // 使用新实例加载（脏代际与 safeMode 均为实例级状态）
        HyperdimensionalStorageFile reader = HyperdimensionalStorageFile.forNexus(server, nexusId);
        Map<ItemDescriptor, BigInteger> loaded = new HashMap<>();
        reader.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, loaded::put);

        assertTrue(reader.isSafeMode());
        assertTrue(Files.exists(storageDir.resolve("items.bin.corrupt")));
    }

    @Test
    void testUnsupportedBinaryVersionEntersSafeMode() throws Exception {
        Files.createDirectories(storageDir);
        // 构造 magic 正确但版本号不受支持的文件
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.write(new byte[] { 'A', 'E', '2', 'E' });
        out.writeInt(99); // 不支持的版本
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(0);
        out.flush();
        Files.write(storageDir.resolve("items.bin"), buffer.toByteArray());

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, (d, a) -> {
        });

        assertTrue(file.isSafeMode());
        assertTrue(Files.exists(storageDir.resolve("items.bin.corrupt")));
    }

    @Test
    void testLegacyMigrationV2ChannelsFormat() throws Exception {
        // 旧版 v2 NBT：channels -> <typeId> -> contents -> [{key, amount}]
        AEItemKey stone = AEItemKey.of(Items.STONE);
        CompoundTag root = new CompoundTag();
        root.putInt("version", 2);
        CompoundTag channels = new CompoundTag();
        CompoundTag itemChannel = new CompoundTag();
        ListTag contents = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("key", stone.toTagGeneric());
        entry.putString("amount", "150");
        contents.add(entry);
        itemChannel.put("contents", contents);
        channels.put(AEKeyType.items().getId().toString(), itemChannel);
        root.put("channels", channels);
        writeLegacyDat(root);

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();

        assertFalse(file.isSafeMode());
        // 旧文件备份为 .backup,数据迁移为二进制格式
        Path legacy = HyperdimensionalStorageFile.getLegacyStoragePath(server, nexusId);
        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(legacy.resolveSibling(legacy.getFileName() + ".backup")));
        assertTrue(Files.exists(storageDir.resolve("items.bin")));

        Map<ItemDescriptor, BigInteger> loaded = new HashMap<>();
        file.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, loaded::put);
        assertEquals(BigInteger.valueOf(150L), loaded.get(new ItemDescriptor(stone)));
    }

    @Test
    void testLegacyMigrationV1ContentsFormat() throws Exception {
        // 旧版 v1 NBT：根节点直接挂 contents 列表,按 key type 分组迁移
        AEItemKey stone = AEItemKey.of(Items.STONE);
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag contents = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("key", stone.toTagGeneric());
        entry.putString("amount", "33");
        contents.add(entry);
        root.put("contents", contents);
        writeLegacyDat(root);

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();

        assertFalse(file.isSafeMode());
        Map<ItemDescriptor, BigInteger> loaded = new HashMap<>();
        file.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, loaded::put);
        assertEquals(BigInteger.valueOf(33L), loaded.get(new ItemDescriptor(stone)));
    }

    @Test
    void testLegacyMigrationUnknownVersionEntersSafeMode() throws Exception {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 99);
        writeLegacyDat(root);

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();

        // 无法识别的旧版格式：进入安全模式,旧文件仍被移走备份
        assertTrue(file.isSafeMode());
        Path legacy = HyperdimensionalStorageFile.getLegacyStoragePath(server, nexusId);
        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(legacy.resolveSibling(legacy.getFileName() + ".backup")));
    }

    @Test
    void testTryMigrateLegacyWithoutFileIsNoOp() {
        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();
        assertFalse(file.isSafeMode());
    }

    @Test
    void testLegacyMigrationV2EnergyChannel() throws Exception {
        // 回归:能量 key type 未注册进 AE2 注册表,fromTagGeneric 无法解析能量条目;
        // 旧版能量通道(key tag 为空标签)必须兜底迁移,否则能量数据静默丢失
        CompoundTag root = new CompoundTag();
        root.putInt("version", 2);
        CompoundTag channels = new CompoundTag();
        CompoundTag energyChannel = new CompoundTag();
        ListTag contents = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("key", new CompoundTag()); // 旧版 EnergyDescriptor.toNBT 为空标签
        entry.putString("amount", "987654321");
        contents.add(entry);
        energyChannel.put("contents", contents);
        channels.put(EnergyKey.ENERGY_KEY_TYPE.getId().toString(), energyChannel);
        root.put("channels", channels);
        writeLegacyDat(root);

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();

        assertFalse(file.isSafeMode());
        assertTrue(Files.exists(storageDir.resolve("energy.bin")));

        Map<EnergyDescriptor, BigInteger> loaded = new HashMap<>();
        file.loadSection(EnergyKey.ENERGY_KEY_TYPE, (byte) 3, EnergyDescriptorCodec.INSTANCE, loaded::put);
        assertEquals(new BigInteger("987654321"), loaded.get(EnergyDescriptor.INSTANCE));
    }

    @Test
    void testLegacyMigrationV1EnergyEntryByMarker() throws Exception {
        // 回归:v1 根列表中按 #c/id 类型标记识别能量条目
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag contents = new ListTag();
        CompoundTag entry = new CompoundTag();
        CompoundTag keyTag = new CompoundTag();
        keyTag.putString("#c", EnergyKey.ID.toString());
        entry.put("key", keyTag);
        entry.putString("amount", "42");
        contents.add(entry);
        root.put("contents", contents);
        writeLegacyDat(root);

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();

        assertFalse(file.isSafeMode());
        Map<EnergyDescriptor, BigInteger> loaded = new HashMap<>();
        file.loadSection(EnergyKey.ENERGY_KEY_TYPE, (byte) 3, EnergyDescriptorCodec.INSTANCE, loaded::put);
        assertEquals(BigInteger.valueOf(42L), loaded.get(EnergyDescriptor.INSTANCE));
    }

    @Test
    void testLegacyMigrationCompressedDat() throws Exception {
        // 回归:1.12 时代的旧版 .dat 以 gzip 压缩格式写出,迁移必须兼容
        AEItemKey stone = AEItemKey.of(Items.STONE);
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag contents = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("key", stone.toTagGeneric());
        entry.putString("amount", "77");
        contents.add(entry);
        root.put("contents", contents);

        Path legacy = HyperdimensionalStorageFile.getLegacyStoragePath(server, nexusId);
        Files.createDirectories(legacy.getParent());
        NbtIo.writeCompressed(root, legacy.toFile());

        HyperdimensionalStorageFile file = HyperdimensionalStorageFile.forNexus(server, nexusId);
        file.tryMigrateLegacy();

        assertFalse(file.isSafeMode());
        Map<ItemDescriptor, BigInteger> loaded = new HashMap<>();
        file.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, loaded::put);
        assertEquals(BigInteger.valueOf(77L), loaded.get(new ItemDescriptor(stone)));
    }

    /**
     * 写入垃圾内容触发损坏处理,使给定实例进入安全模式,返回损坏文件路径.
     */
    private Path enterSafeModeWithCorruptFile(HyperdimensionalStorageFile file) throws Exception {
        Files.createDirectories(storageDir);
        Path itemsBin = storageDir.resolve("items.bin");
        Files.write(itemsBin, "garbage".getBytes());
        file.loadSection(AEKeyType.items(), (byte) 1, ItemDescriptorCodec.INSTANCE, (d, a) -> {
        });
        return itemsBin;
    }

    /**
     * 以非压缩 NBT 写入旧版 .dat 文件.
     * <p>{@code migrateFromLegacyNbt} 兼容非压缩与 gzip 压缩两种格式,
     * 压缩格式的覆盖见 {@link #testLegacyMigrationCompressedDat}.</p>
     */
    private void writeLegacyDat(CompoundTag root) throws Exception {
        Path legacy = HyperdimensionalStorageFile.getLegacyStoragePath(server, nexusId);
        Files.createDirectories(legacy.getParent());
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.io.BufferedOutputStream(Files.newOutputStream(legacy)))) {
            NbtIo.write(root, out);
        }
    }
}
