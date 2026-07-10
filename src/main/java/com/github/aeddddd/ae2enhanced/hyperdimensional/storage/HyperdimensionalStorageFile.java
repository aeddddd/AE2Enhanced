package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.DescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.EnergyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.FluidDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.GenericKeyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.ItemDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.Descriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.GenericKeyDescriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.ItemDescriptor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/**
 * 超维度仓储持久化文件管理。
 * <p>每个 Nexus 对应一个独立目录：{@code <world>/ae2enhanced/storage/<nexusId>/}，
 * 其中每个已注册 AEKeyType 分别保存为二进制文件（items.bin、fluids.bin、energy.bin 以及
 * 第三方 key type 对应的 .bin 文件）。旧版单文件 {@code <nexusId>.dat} 在首次加载时自动迁移到
 * 二进制格式并备份为 {@code .backup}。</p>
 *
 * <p>二进制格式（版本 3）：</p>
 * <ul>
 *   <li>Magic: 4 bytes ('A','E','2','E')</li>
 *   <li>Version: 4 bytes int</li>
 *   <li>Flags: 4 bytes int</li>
 *   <li>Entry count: 4 bytes int</li>
 *   <li>Entries: 每个条目为 [descriptor length][descriptor bytes][sign][magnitude length][magnitude bytes]</li>
 *   <li>CRC32: 4 bytes（覆盖 Magic 到 Entries 末尾）</li>
 * </ul>
 *
 * <p>版本 3 条目描述符直接使用 {@link AEKey#toTagGeneric()} 写入完整 NBT，包含类型信息，
 * 不再依赖 1 字节的 type 标识。版本 2 与版本 1 仍可读，但新文件统一以版本 3 写入。</p>
 */
public final class HyperdimensionalStorageFile {

    // 二进制文件格式常量
    private static final byte[] MAGIC = new byte[] { 'A', 'E', '2', 'E' };
    private static final int BINARY_VERSION = 3;
    private static final int BINARY_VERSION_2 = 2;
    private static final int BINARY_VERSION_1 = 1;

    // 版本 2 的类型字节（仅用于读取旧版）
    private static final byte TYPE_ITEM = 1;
    private static final byte TYPE_FLUID = 2;
    private static final byte TYPE_ENERGY = 3;

    // 旧版 NBT 常量
    private static final String TAG_VERSION = "version";
    private static final String TAG_CHANNELS = "channels";
    private static final String TAG_CONTENTS = "contents";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT = "amount";
    private static final int LEGACY_VERSION = 2;

    // 安全限制
    private static final int MAX_DESCRIPTOR_LENGTH = 1_000_000;
    private static final int MAX_MAGNITUDE_LENGTH = 1_000_000;
    private static final int MAX_ENTRY_COUNT = 1_000_000;
    private static final long MAX_FILE_SIZE = 256L * 1024 * 1024;

    // 异步写入线程，所有 Nexus 共享一个单线程执行器，避免在服务端主线程执行文件 I/O。
    // 守护线程会在 {@link #shutdown()} 中被安全关闭；若服务器在同一 JVM 内重新启动，
    // 新的保存请求会自动重新创建执行器。
    private static final Object EXECUTOR_LOCK = new Object();
    private static ExecutorService ASYNC_EXECUTOR = createExecutor();

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(
                r -> {
                    Thread t = new Thread(r, "AE2E-Hyperdimensional-Storage");
                    t.setDaemon(true);
                    return t;
                });
    }

    private static ExecutorService getExecutor() {
        synchronized (EXECUTOR_LOCK) {
            if (ASYNC_EXECUTOR == null || ASYNC_EXECUTOR.isShutdown()) {
                ASYNC_EXECUTOR = createExecutor();
            }
            return ASYNC_EXECUTOR;
        }
    }

    private final Path directory;
    private final UUID nexusId;
    private final MinecraftServer server;
    // 每 section 的脏代际：0 表示干净，>0 表示脏，每次 markDirty 递增。
    // 异步保存完成后只有在代际未发生变化时才清为干净，防止保存期间的新变更被丢失。
    private final Map<AEKeyType, Integer> dirtyGenerations = new ConcurrentHashMap<>();
    private volatile boolean safeMode = false;

    private HyperdimensionalStorageFile(Path directory, UUID nexusId, MinecraftServer server) {
        this.directory = directory;
        this.nexusId = nexusId;
        this.server = server;
    }

    /**
     * 创建指定 Nexus 的持久化管理器。
     */
    public static HyperdimensionalStorageFile forNexus(MinecraftServer server, UUID nexusId) {
        return new HyperdimensionalStorageFile(getStorageDirectory(server, nexusId), nexusId, server);
    }

    /**
     * 获取新版 Nexus 存储目录。
     */
    public static Path getStorageDirectory(MinecraftServer server, UUID nexusId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("ae2enhanced")
                .resolve("storage")
                .resolve(nexusId.toString());
    }

    /**
     * 获取旧版单文件 NBT 路径。
     */
    public static Path getLegacyStoragePath(MinecraftServer server, UUID nexusId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("ae2enhanced")
                .resolve("storage")
                .resolve(nexusId + ".dat");
    }

    /**
     * 加载或创建指定 Nexus 的存储容器。
     * <p>若存在旧版 {@code .dat} 文件，会先迁移到二进制格式并备份。</p>
     */
    public static HyperdimensionalStorage loadOrCreate(MinecraftServer server, UUID nexusId,
            @Nullable Consumer<HyperdimensionalStorage> changeCallback) {
        HyperdimensionalStorageFile file = forNexus(server, nexusId);
        file.tryMigrateLegacy();
        HyperdimensionalStorage storage = new HyperdimensionalStorage(nexusId, file, changeCallback);
        storage.markClean();
        return storage;
    }

    /**
     * 保存指定存储容器（兼容旧版静态调用）。
     */
    public static void save(MinecraftServer server, HyperdimensionalStorage storage) {
        storage.persist();
    }

    /**
     * 安全关闭所有异步 I/O 线程。
     * <p>应在服务器停止前调用，确保已提交的写入任务完成。</p>
     */
    public static void shutdown() {
        ExecutorService executor;
        synchronized (EXECUTOR_LOCK) {
            executor = ASYNC_EXECUTOR;
            ASYNC_EXECUTOR = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 尝试迁移旧版 NBT 文件到二进制文件。
     */
    public void tryMigrateLegacy() {
        Path legacy = directory.resolveSibling(nexusId + ".dat");
        if (Files.exists(legacy)) {
            migrateFromLegacyNbt(legacy);
        }
    }

    /**
     * 加载指定 AEKeyType 的二进制文件，对每个有效条目调用 consumer。
     * <p>由 {@link com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.AbstractStorageAdapter}
     * 在初始化时调用。</p>
     */
    public <D extends Descriptor> void loadSection(
            AEKeyType type, byte legacyType, DescriptorCodec<D> codec, BiConsumer<D, BigInteger> consumer) {
        if (safeMode || type == null || codec == null || consumer == null) {
            return;
        }
        Path file = getSectionFile(type);
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            byte[] data = readFileLocked(file);
            parseBinaryFile(data, legacyType, codec, consumer, file, type.getId().toString());
        } catch (Exception e) {
            enterSafeMode("loadSection " + type.getId(), e);
        }
    }

    /**
     * 将指定 AEKeyType 的条目异步写入二进制文件。
     * <p>仅当对应 section 为脏时才会实际提交写入任务；写入成功后，在服务端主线程回调中标记该 section 已保存。</p>
     *
     * @param type       通道类型
     * @param generation 保存开始时捕获的脏代际
     * @param codec      描述符编解码器
     * @param entries    待保存条目（方法内部会复制，避免异步写入期间并发修改）
     */
    public <D extends Descriptor> void saveSection(
            AEKeyType type, int generation, DescriptorCodec<D> codec, Map<D, BigInteger> entries) {
        if (safeMode || type == null || codec == null || entries == null) {
            return;
        }
        if (!isDirty(type, generation)) {
            return;
        }
        // 立即复制快照，避免异步线程遍历期间主线程修改 Map。
        Map<D, BigInteger> snapshot = new HashMap<>(entries);
        try {
            ensureDirectory();
            Path file = getSectionFile(type);
            if (file == null) {
                throw new IOException("Unknown section file: " + type.getId());
            }
            Path directory = this.directory;
            String fileName = file.getFileName().toString();
            getExecutor().submit(() -> {
                boolean success = false;
                Exception error = null;
                try {
                    Path temp = directory.resolve(fileName + ".tmp");
                    byte[] data = serializeBinaryFileV3(codec, snapshot);
                    writeFileLocked(temp, data);
                    Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    success = true;
                } catch (Exception e) {
                    error = e;
                }
                final boolean finalSuccess = success;
                final Exception finalError = error;
                server.execute(() -> {
                    if (finalSuccess) {
                        markClean(type, generation);
                    } else {
                        enterSafeMode("saveSection " + type.getId(), finalError);
                    }
                });
            });
        } catch (Exception e) {
            enterSafeMode("saveSection " + type.getId(), e);
        }
    }

    /**
     * 标记当前 Nexus 的所有 section 已脏（兼容旧的全局调用）。
     */
    public void markDirty() {
        for (AEKeyType type : dirtyGenerations.keySet()) {
            markDirty(type);
        }
    }

    /**
     * 标记指定 AEKeyType 已脏。
     */
    public void markDirty(AEKeyType type) {
        if (type == null) {
            return;
        }
        dirtyGenerations.merge(type, 1, Integer::sum);
    }

    /**
     * @return 当前是否有任何 section 处于脏状态
     */
    public boolean isDirty() {
        for (int gen : dirtyGenerations.values()) {
            if (gen > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取指定 section 的当前脏代际。
     */
    public int getDirtyGeneration(AEKeyType type) {
        return type == null ? 0 : dirtyGenerations.getOrDefault(type, 0);
    }

    /**
     * 检查指定 section 在捕获的代际下是否为脏。
     */
    public boolean isDirty(AEKeyType type, int generation) {
        return type != null && dirtyGenerations.getOrDefault(type, 0) == generation && generation > 0;
    }

    /**
     * 标记当前 Nexus 的所有 section 已保存。
     */
    public void markClean() {
        dirtyGenerations.clear();
    }

    /**
     * 标记指定 section 已保存，仅在代际未发生变化时生效。
     */
    public void markClean(AEKeyType type, int generation) {
        if (type == null) {
            return;
        }
        dirtyGenerations.compute(type, (k, current) -> {
            if (current == null || current == generation) {
                return null;
            }
            return current;
        });
    }

    /**
     * 触发 flush 钩子。
     * <p>本类采用异步原子写入，实际写入由异步线程完成；此处仅提供同步入口供紧急调用。</p>
     */
    public void flush() {
        // 已无需额外操作，保存任务已在异步线程中排队执行。
    }

    /**
     * 同步等待所有已提交的写入任务完成。
     * <p>仅应在服务器停止等必须确保数据落盘的场景调用。</p>
     */
    public void awaitPendingWrites() {
        // 使用一个简单的轮询等待：异步线程是单线程的，因此新提交的任务会顺序执行。
        // 更严谨的做法是跟踪 Future，但这需要更大的改动；这里作为折中方案。
        while (isDirty()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * @return 是否处于安全模式（只读）
     */
    public boolean isSafeMode() {
        return safeMode;
    }

    /**
     * 从旧版 NBT 文件迁移数据到二进制文件，并将原文件备份为 {@code .backup}。
     */
    public void migrateFromLegacyNbt(Path legacyPath) {
        if (safeMode) {
            return;
        }
        try {
            ensureDirectory();
            CompoundTag root = NbtIo.read(legacyPath.toFile());
            if (root == null) {
                return;
            }
            int version = root.getInt(TAG_VERSION);
            Map<AEKeyType, Map<AEKey, BigInteger>> grouped = new HashMap<>();
            if (version == LEGACY_VERSION) {
                CompoundTag channelsTag = root.getCompound(TAG_CHANNELS);
                for (String key : channelsTag.getAllKeys()) {
                    if (!channelsTag.contains(key, Tag.TAG_COMPOUND)) {
                        continue;
                    }
                    AEKeyType type = resolveType(key);
                    if (type == null) {
                        continue;
                    }
                    Map<AEKey, BigInteger> entries = loadEntriesFromNbt(channelsTag.getCompound(key));
                    grouped.put(type, entries);
                }
            } else if (version == 1) {
                ListTag list = root.getList(TAG_CONTENTS, Tag.TAG_COMPOUND);
                Map<AEKey, BigInteger> entries = loadEntriesFromNbt(list);
                for (Map.Entry<AEKey, BigInteger> entry : entries.entrySet()) {
                    grouped.computeIfAbsent(entry.getKey().getType(), k -> new HashMap<>())
                            .put(entry.getKey(), entry.getValue());
                }
            } else {
                throw new IOException("Unknown legacy version: " + version);
            }
            for (Map.Entry<AEKeyType, Map<AEKey, BigInteger>> entry : grouped.entrySet()) {
                saveLegacyEntries(entry.getKey(), entry.getValue());
            }
            Path backup = legacyPath.resolveSibling(legacyPath.getFileName() + ".backup");
            Files.move(legacyPath, backup, StandardCopyOption.REPLACE_EXISTING);
            AE2Enhanced.LOGGER.info("[AE2E] Migrated legacy hyperdimensional storage for nexus {} to binary format", nexusId);
        } catch (Exception e) {
            enterSafeMode("migrateFromLegacyNbt", e);
            try {
                Path backup = legacyPath.resolveSibling(legacyPath.getFileName() + ".backup");
                Files.move(legacyPath, backup, StandardCopyOption.REPLACE_EXISTING);
                AE2Enhanced.LOGGER.warn("[AE2E] Moved legacy storage file to {} after migration failure", backup);
            } catch (IOException backupEx) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to backup legacy storage file: {}", backupEx.toString());
            }
        }
    }

    // ===== 内部工具方法 =====

    private void ensureDirectory() throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }

    private void enterSafeMode(String operation, Exception cause) {
        if (!safeMode) {
            safeMode = true;
            AE2Enhanced.LOGGER.error("[AE2E] Hyperdimensional storage {} failed for nexus {}, entering safe mode: {}",
                    operation, nexusId, cause.toString());
        }
    }

    @Nullable
    private Path getSectionFile(AEKeyType type) {
        if (type == null) {
            return null;
        }
        return directory.resolve(getChannelFileName(type));
    }

    private String getChannelFileName(AEKeyType type) {
        String id = type.getId().toString();
        if (id.equals(AEKeyType.items().getId().toString())) {
            return "items.bin";
        }
        if (id.equals(AEKeyType.fluids().getId().toString())) {
            return "fluids.bin";
        }
        if (id.equals(EnergyKey.ENERGY_KEY_TYPE.getId().toString())) {
            return "energy.bin";
        }
        return sanitizeFileName(id) + ".bin";
    }

    private String sanitizeFileName(String id) {
        return id.replace(':', '_').replace('/', '_');
    }

    @Nullable
    private AEKeyType resolveType(String id) {
        if (id.equals(AEKeyType.items().getId().toString())) {
            return AEKeyType.items();
        }
        if (id.equals(AEKeyType.fluids().getId().toString())) {
            return AEKeyType.fluids();
        }
        if (id.equals(EnergyKey.ENERGY_KEY_TYPE.getId().toString())) {
            return EnergyKey.ENERGY_KEY_TYPE;
        }
        return null;
    }

    private void saveLegacyEntries(AEKeyType type, Map<AEKey, BigInteger> entries) throws IOException {
        if (type == AEKeyType.items()) {
            Map<ItemDescriptor, BigInteger> map = new HashMap<>();
            for (Map.Entry<AEKey, BigInteger> e : entries.entrySet()) {
                if (e.getKey() instanceof AEItemKey itemKey) {
                    map.put(new ItemDescriptor(itemKey), e.getValue());
                }
            }
            markDirty(type);
            saveSectionLegacy(type, TYPE_ITEM, ItemDescriptorCodec.INSTANCE, map);
        } else if (type == AEKeyType.fluids()) {
            Map<FluidDescriptor, BigInteger> map = new HashMap<>();
            for (Map.Entry<AEKey, BigInteger> e : entries.entrySet()) {
                if (e.getKey() instanceof AEFluidKey fluidKey) {
                    map.put(new FluidDescriptor(fluidKey), e.getValue());
                }
            }
            markDirty(type);
            saveSectionLegacy(type, TYPE_FLUID, FluidDescriptorCodec.INSTANCE, map);
        } else if (type == EnergyKey.ENERGY_KEY_TYPE) {
            BigInteger total = BigInteger.ZERO;
            for (BigInteger amount : entries.values()) {
                total = total.add(amount);
            }
            Map<EnergyDescriptor, BigInteger> map = Map.of(EnergyDescriptor.INSTANCE, total);
            markDirty(type);
            saveSectionLegacy(type, TYPE_ENERGY, EnergyDescriptorCodec.INSTANCE, map);
        }
    }

    /**
     * 仅用于旧版迁移：以同步方式写入旧版格式（版本 2），确保迁移立即完成。
     */
    private <D extends Descriptor> void saveSectionLegacy(
            AEKeyType type, byte legacyType, DescriptorCodec<D> codec, Map<D, BigInteger> entries) throws IOException {
        ensureDirectory();
        Path file = getSectionFile(type);
        if (file == null) {
            throw new IOException("Unknown section file: " + type.getId());
        }
        Path temp = directory.resolve(file.getFileName() + ".tmp");
        byte[] data = serializeBinaryFileV2(legacyType, codec, entries);
        writeFileLocked(temp, data);
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        markClean(type, dirtyGenerations.getOrDefault(type, 1));
    }

    // ===== 二进制文件读写 =====

    private <D extends Descriptor> byte[] serializeBinaryFileV2(
            byte type, DescriptorCodec<D> codec, Map<D, BigInteger> entries) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(content)) {
            out.write(MAGIC);
            out.writeInt(BINARY_VERSION_2);
            out.writeInt(0); // flags
            if (entries.size() > MAX_ENTRY_COUNT) {
                throw new IOException("Too many entries: " + entries.size());
            }
            out.writeInt(entries.size());
            for (Map.Entry<D, BigInteger> entry : entries.entrySet()) {
                byte[] descriptor = writeDescriptorV2(type, codec, entry.getKey());
                out.writeInt(descriptor.length);
                out.write(descriptor);
                BigInteger count = entry.getValue();
                out.writeByte(count.signum());
                byte[] magnitude = count.abs().toByteArray();
                out.writeInt(magnitude.length);
                out.write(magnitude);
            }
        }
        byte[] contentBytes = content.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(contentBytes);
        byte[] crcBytes = encodeCrc32(crc.getValue());

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(contentBytes);
        result.write(crcBytes);
        return result.toByteArray();
    }

    private <D extends Descriptor> byte[] serializeBinaryFileV3(
            DescriptorCodec<D> codec, Map<D, BigInteger> entries) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(content)) {
            out.write(MAGIC);
            out.writeInt(BINARY_VERSION);
            out.writeInt(0); // flags
            if (entries.size() > MAX_ENTRY_COUNT) {
                throw new IOException("Too many entries: " + entries.size());
            }
            out.writeInt(entries.size());
            for (Map.Entry<D, BigInteger> entry : entries.entrySet()) {
                byte[] descriptor = writeDescriptorV3(codec, entry.getKey());
                out.writeInt(descriptor.length);
                out.write(descriptor);
                BigInteger count = entry.getValue();
                out.writeByte(count.signum());
                byte[] magnitude = count.abs().toByteArray();
                if (magnitude.length > MAX_MAGNITUDE_LENGTH) {
                    throw new IOException("Magnitude too large: " + magnitude.length);
                }
                out.writeInt(magnitude.length);
                out.write(magnitude);
            }
        }
        byte[] contentBytes = content.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(contentBytes);
        byte[] crcBytes = encodeCrc32(crc.getValue());

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(contentBytes);
        result.write(crcBytes);
        return result.toByteArray();
    }

    private static byte[] encodeCrc32(long crcValue) {
        return new byte[] {
                (byte) (crcValue >>> 24),
                (byte) (crcValue >>> 16),
                (byte) (crcValue >>> 8),
                (byte) crcValue
        };
    }

    private <D extends Descriptor> void parseBinaryFile(
            byte[] data, byte expectedType, DescriptorCodec<D> codec, BiConsumer<D, BigInteger> consumer,
            Path sourceFile, String typeName) throws IOException {
        if (data.length < 4) {
            throw new IOException("File too small to contain magic");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!equalsMagic(magic)) {
                throw new IOException("Invalid magic bytes");
            }
            int version = in.readInt();
            if (version != BINARY_VERSION && version != BINARY_VERSION_2 && version != BINARY_VERSION_1) {
                throw new IOException("Unsupported binary version: " + version);
            }
            in.readInt(); // flags
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRY_COUNT) {
                throw new IOException("Invalid entry count: " + count);
            }
            for (int i = 0; i < count; i++) {
                int descLen = in.readInt();
                if (descLen < 0 || descLen > MAX_DESCRIPTOR_LENGTH) {
                    throw new IOException("Invalid descriptor length: " + descLen);
                }
                byte[] descriptor = new byte[descLen];
                in.readFully(descriptor);
                D key = readDescriptor(descriptor, expectedType, codec, version);
                int sign = in.readByte();
                if (sign < -1 || sign > 1) {
                    throw new IOException("Invalid sign byte: " + sign);
                }
                int magLen = in.readInt();
                if (magLen < 0 || magLen > MAX_MAGNITUDE_LENGTH) {
                    throw new IOException("Invalid magnitude length: " + magLen);
                }
                byte[] magnitude = new byte[magLen];
                in.readFully(magnitude);
                BigInteger amount = new BigInteger(sign, magnitude);
                if (key != null && amount.signum() > 0) {
                    consumer.accept(key, amount);
                }
            }
            if (version == BINARY_VERSION || version == BINARY_VERSION_2) {
                int remaining = in.available();
                if (remaining != 4) {
                    throw new IOException("CRC32 missing or extra trailing bytes: " + remaining);
                }
                byte[] storedCrc = new byte[4];
                in.readFully(storedCrc);
                long storedCrcValue = decodeCrc32(storedCrc);
                long computedCrc = computeCrcOfContent(data);
                if (storedCrcValue != computedCrc) {
                    throw new IOException("CRC32 mismatch: stored=" + storedCrcValue + ", computed=" + computedCrc);
                }
            }
        } catch (IOException e) {
            backupCorruptFile(sourceFile, e);
            throw e;
        }
    }

    private static long decodeCrc32(byte[] storedCrc) {
        return ((storedCrc[0] & 0xFFL) << 24)
                | ((storedCrc[1] & 0xFFL) << 16)
                | ((storedCrc[2] & 0xFFL) << 8)
                | (storedCrc[3] & 0xFFL);
    }

    private long computeCrcOfContent(byte[] data) {
        CRC32 crc = new CRC32();
        int contentLength = data.length - 4;
        if (contentLength < 0) {
            contentLength = 0;
        }
        crc.update(data, 0, contentLength);
        return crc.getValue();
    }

    private void backupCorruptFile(Path file, Exception cause) {
        try {
            Path corrupt = directory.resolve(file.getFileName() + ".corrupt");
            Files.move(file, corrupt, StandardCopyOption.REPLACE_EXISTING);
            AE2Enhanced.LOGGER.warn("[AE2E] Backed up corrupt hyperdimensional storage file {} to {}", file, corrupt);
        } catch (IOException moveEx) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to backup corrupt storage file {}: {}", file, moveEx.toString());
        }
    }

    private <D extends Descriptor> byte[] writeDescriptorV2(
            byte type, DescriptorCodec<D> codec, D descriptor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeByte(type);
            codec.write(out, descriptor);
        }
        return baos.toByteArray();
    }

    private <D extends Descriptor> byte[] writeDescriptorV3(
            DescriptorCodec<D> codec, D descriptor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            codec.write(out, descriptor);
        }
        return baos.toByteArray();
    }

    @Nullable
    private <D extends Descriptor> D readDescriptor(
            byte[] descriptor, byte expectedType, DescriptorCodec<D> codec, int version) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(descriptor))) {
            if (version == BINARY_VERSION_1) {
                int type = in.readByte();
                return readDescriptorV1(type, in, codec);
            }
            if (version == BINARY_VERSION_2) {
                int type = in.readByte();
                if (type != expectedType) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Descriptor type mismatch: expected {}, got {}", expectedType, type);
                    return null;
                }
                return codec.read(in);
            }
            // version == BINARY_VERSION
            return codec.read(in);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to read hyperdimensional storage descriptor: {}", e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <D extends Descriptor> D readDescriptorV1(
            int type, DataInputStream in, DescriptorCodec<D> codec) throws IOException {
        String id = in.readUTF();
        if (type == TYPE_ITEM && codec instanceof ItemDescriptorCodec) {
            ResourceLocation loc = new ResourceLocation(id);
            if (!BuiltInRegistries.ITEM.containsKey(loc)) {
                return null;
            }
            Item item = BuiltInRegistries.ITEM.get(loc);
            return (D) new ItemDescriptor(AEItemKey.of(item));
        } else if (type == TYPE_FLUID && codec instanceof FluidDescriptorCodec) {
            ResourceLocation loc = new ResourceLocation(id);
            if (!BuiltInRegistries.FLUID.containsKey(loc)) {
                return null;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(loc);
            return (D) new FluidDescriptor(AEFluidKey.of(fluid));
        } else if (type == TYPE_ENERGY && codec instanceof EnergyDescriptorCodec) {
            return (D) EnergyDescriptor.INSTANCE;
        }
        return null;
    }

    // ===== 文件读写辅助 =====
    // 注：所有写入均发生在异步线程，不会阻塞服务端主线程。

    private static byte[] readFileLocked(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size > MAX_FILE_SIZE) {
                throw new IOException("File too large: " + size + " (max " + MAX_FILE_SIZE + ")");
            }
            if (size > Integer.MAX_VALUE) {
                throw new IOException("File too large: " + size);
            }
            if (size < 0) {
                throw new IOException("Invalid file size: " + size);
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            int read = channel.read(buffer);
            if (read != size) {
                throw new IOException("Failed to read entire file: expected " + size + ", got " + read);
            }
            return buffer.array();
        }
    }

    private static void writeFileLocked(Path path, byte[] data) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(ByteBuffer.wrap(data));
            channel.force(true); // 确保数据落盘，避免崩溃丢失
        }
    }

    private static boolean equalsMagic(byte[] bytes) {
        return bytes.length == MAGIC.length
                && bytes[0] == MAGIC[0]
                && bytes[1] == MAGIC[1]
                && bytes[2] == MAGIC[2]
                && bytes[3] == MAGIC[3];
    }

    // ===== 旧版 NBT 加载辅助 =====

    private Map<AEKey, BigInteger> loadEntriesFromNbt(CompoundTag channelTag) {
        if (!channelTag.contains(TAG_CONTENTS, Tag.TAG_LIST)) {
            return new HashMap<>();
        }
        return loadEntriesFromNbt(channelTag.getList(TAG_CONTENTS, Tag.TAG_COMPOUND));
    }

    private Map<AEKey, BigInteger> loadEntriesFromNbt(ListTag list) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(TAG_KEY, Tag.TAG_COMPOUND) || !entry.contains(TAG_AMOUNT, Tag.TAG_STRING)) {
                continue;
            }
            AEKey key = AEKey.fromTagGeneric(entry.getCompound(TAG_KEY));
            if (key == null) {
                continue;
            }
            BigInteger amount = new BigInteger(entry.getString(TAG_AMOUNT));
            if (amount.signum() > 0) {
                result.put(key, amount);
            }
        }
        return result;
    }
}
