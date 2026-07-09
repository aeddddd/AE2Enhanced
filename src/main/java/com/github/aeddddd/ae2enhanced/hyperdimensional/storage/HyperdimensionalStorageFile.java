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
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.ItemDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.Descriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.EnergyDescriptor;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.FluidDescriptor;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/**
 * 超维度仓储持久化文件管理。
 * <p>每个 Nexus 对应一个独立目录：{@code <world>/ae2enhanced/storage/<nexusId>/}，
 * 其中每个通道分别保存为二进制文件（items.bin、fluids.bin、energy.bin）。
 * 旧版单文件 {@code <nexusId>.dat} 在首次加载时自动迁移到二进制格式并备份为 {@code .backup}。</p>
 *
 * <p>二进制格式（版本 2 / 3）：</p>
 * <ul>
 *   <li>Magic: 4 bytes ('A','E','2','E')</li>
 *   <li>Version: 4 bytes int</li>
 *   <li>Flags: 4 bytes int</li>
 *   <li>Entry count: 4 bytes int</li>
 *   <li>Entries: 每个条目为 [descriptor length][descriptor bytes][sign][magnitude length][magnitude bytes]</li>
 *   <li>CRC32: 4 bytes（覆盖 Magic 到 Entries 末尾）</li>
 * </ul>
 *
 * <p>条目描述符：</p>
 * <ul>
 *   <li>Type: 1 byte（1=物品, 2=流体, 3=能量）</li>
 *   <li>Key: 物品/流体由对应 {@link DescriptorCodec} 写入完整 NBT；能量写入标记字符串</li>
 * </ul>
 *
 * <p>版本 1 的格式仍可读，但新文件统一以版本 2 写入。加载失败时进入安全模式，
 * 并将损坏文件备份为 {@code .corrupt}，防止进一步破坏数据。</p>
 */
public final class HyperdimensionalStorageFile {

    // 二进制文件格式常量
    private static final byte[] MAGIC = new byte[] { 'A', 'E', '2', 'E' };
    private static final int BINARY_VERSION = 2;
    private static final int BINARY_VERSION_1 = 1;

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
    private static final int MAX_ENTRY_COUNT = 100_000_000;

    private final Path directory;
    private final UUID nexusId;
    private final Set<StorageSection> dirtySections = EnumSet.noneOf(StorageSection.class);
    private volatile boolean safeMode = false;

    private HyperdimensionalStorageFile(Path directory, UUID nexusId) {
        this.directory = directory;
        this.nexusId = nexusId;
    }

    /**
     * 创建指定 Nexus 的持久化管理器。
     */
    public static HyperdimensionalStorageFile forNexus(MinecraftServer server, UUID nexusId) {
        return new HyperdimensionalStorageFile(getStorageDirectory(server, nexusId), nexusId);
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
     * 尝试迁移旧版 NBT 文件到二进制文件。
     */
    public void tryMigrateLegacy() {
        Path legacy = directory.resolveSibling(nexusId + ".dat");
        if (Files.exists(legacy)) {
            migrateFromLegacyNbt(legacy);
        }
    }

    /**
     * 加载指定 section 的二进制文件，对每个有效条目调用 consumer。
     * <p>由 {@link com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.AbstractStorageAdapter}
     * 在初始化时调用。</p>
     */
    public <D extends Descriptor> void loadSection(
            StorageSection section, byte type, DescriptorCodec<D> codec, BiConsumer<D, BigInteger> consumer) {
        if (safeMode || section == null || codec == null || consumer == null) {
            return;
        }
        Path file = getSectionFile(section);
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            byte[] data = readFileLocked(file);
            parseBinaryFile(data, type, codec, consumer, file, section.getName());
        } catch (Exception e) {
            enterSafeMode("loadSection " + section.getName(), e);
        }
    }

    /**
     * 将指定 section 的条目写入二进制文件。
     * <p>仅当对应 section 为脏时才会实际写入，写入成功后标记该 section 已保存。</p>
     */
    public <D extends Descriptor> void saveSection(
            StorageSection section, byte type, DescriptorCodec<D> codec, Map<D, BigInteger> entries) {
        if (safeMode || section == null || codec == null || entries == null) {
            return;
        }
        if (!isDirty(section)) {
            return;
        }
        try {
            ensureDirectory();
            Path file = getSectionFile(section);
            if (file == null) {
                throw new IOException("Unknown section file: " + section.getName());
            }
            Path temp = directory.resolve(file.getFileName() + ".tmp");
            byte[] data = serializeBinaryFile(type, codec, entries);
            writeFileLocked(temp, data);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            markClean(section);
        } catch (Exception e) {
            enterSafeMode("saveSection " + section.getName(), e);
        }
    }

    /**
     * 标记当前 Nexus 的所有 section 已脏（兼容旧的全局调用）。
     */
    public void markDirty() {
        dirtySections.addAll(EnumSet.allOf(StorageSection.class));
    }

    /**
     * 标记指定 section 已脏。
     *
     * @param section 发生变更的 section
     */
    public void markDirty(StorageSection section) {
        if (section == null) {
            return;
        }
        dirtySections.add(section);
    }

    /**
     * @return 当前是否有任何 section 处于脏状态
     */
    public boolean isDirty() {
        return !dirtySections.isEmpty();
    }

    /**
     * @param section 指定 section
     * @return 该 section 是否处于脏状态
     */
    public boolean isDirty(StorageSection section) {
        return section != null && dirtySections.contains(section);
    }

    /**
     * 标记当前 Nexus 的所有 section 已保存。
     */
    public void markClean() {
        dirtySections.clear();
    }

    /**
     * 标记指定 section 已保存。
     *
     * @param section 已持久化的 section
     */
    public void markClean(StorageSection section) {
        if (section == null) {
            return;
        }
        dirtySections.remove(section);
    }

    /**
     * 触发 flush 钩子。
     * <p>本类采用原子写入，实际写入由 {@link HyperdimensionalStorage#persist()} 执行。</p>
     */
    public void flush() {
        // 二进制文件写入已完成，无需额外 flush。
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
    private Path getSectionFile(StorageSection section) {
        AEKeyType type = section.getKeyType();
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
            markDirty(StorageSection.ITEMS);
            saveSection(StorageSection.ITEMS, TYPE_ITEM, ItemDescriptorCodec.INSTANCE, map);
        } else if (type == AEKeyType.fluids()) {
            Map<FluidDescriptor, BigInteger> map = new HashMap<>();
            for (Map.Entry<AEKey, BigInteger> e : entries.entrySet()) {
                if (e.getKey() instanceof AEFluidKey fluidKey) {
                    map.put(new FluidDescriptor(fluidKey), e.getValue());
                }
            }
            markDirty(StorageSection.FLUIDS);
            saveSection(StorageSection.FLUIDS, TYPE_FLUID, FluidDescriptorCodec.INSTANCE, map);
        } else if (type == EnergyKey.ENERGY_KEY_TYPE) {
            BigInteger total = BigInteger.ZERO;
            for (BigInteger amount : entries.values()) {
                total = total.add(amount);
            }
            Map<EnergyDescriptor, BigInteger> map = Map.of(EnergyDescriptor.INSTANCE, total);
            markDirty(StorageSection.ENERGY);
            saveSection(StorageSection.ENERGY, TYPE_ENERGY, EnergyDescriptorCodec.INSTANCE, map);
        }
    }

    // ===== 二进制文件读写 =====

    private <D extends Descriptor> byte[] serializeBinaryFile(
            byte type, DescriptorCodec<D> codec, Map<D, BigInteger> entries) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(content)) {
            // 头部 16 字节
            out.write(MAGIC);
            out.writeInt(BINARY_VERSION);
            out.writeInt(0); // flags
            if (entries.size() > MAX_ENTRY_COUNT) {
                throw new IOException("Too many entries: " + entries.size());
            }
            out.writeInt(entries.size());
            for (Map.Entry<D, BigInteger> entry : entries.entrySet()) {
                byte[] descriptor = writeDescriptor(type, codec, entry.getKey());
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
        byte[] crcBytes = new byte[4];
        long crcValue = crc.getValue();
        crcBytes[0] = (byte) (crcValue >>> 24);
        crcBytes[1] = (byte) (crcValue >>> 16);
        crcBytes[2] = (byte) (crcValue >>> 8);
        crcBytes[3] = (byte) crcValue;

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(contentBytes);
        result.write(crcBytes);
        return result.toByteArray();
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
            if (version != BINARY_VERSION && version != BINARY_VERSION_1) {
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
            if (version == BINARY_VERSION) {
                int remaining = in.available();
                if (remaining != 4) {
                    throw new IOException("CRC32 missing or extra trailing bytes: " + remaining);
                }
                byte[] storedCrc = new byte[4];
                in.readFully(storedCrc);
                long storedCrcValue = ((storedCrc[0] & 0xFFL) << 24)
                        | ((storedCrc[1] & 0xFFL) << 16)
                        | ((storedCrc[2] & 0xFFL) << 8)
                        | (storedCrc[3] & 0xFFL);
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

    private long computeCrcOfContent(byte[] data) {
        // data 的最后 4 个字节是 CRC32，其余为内容
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

    private <D extends Descriptor> byte[] writeDescriptor(
            byte type, DescriptorCodec<D> codec, D descriptor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(baos)) {
            out.writeByte(type);
            codec.write(out, descriptor);
        }
        return baos.toByteArray();
    }

    @Nullable
    private <D extends Descriptor> D readDescriptor(
            byte[] descriptor, byte expectedType, DescriptorCodec<D> codec, int version) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(descriptor))) {
            int type = in.readByte();
            if (version == BINARY_VERSION_1) {
                return readDescriptorV1(type, in, codec);
            }
            if (type != expectedType) {
                AE2Enhanced.LOGGER.warn("[AE2E] Descriptor type mismatch: expected {}, got {}", expectedType, type);
                return null;
            }
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
    // 注：Minecraft 服务端逻辑为单线程，且所有调用均发生在服务端主线程，
    // 因此不需要 FileLock。移除 FileLock 与 channel.force 可避免 Windows 上
    // 因外部进程或系统服务持有文件锁而导致的保存卡顿/死锁。

    private static byte[] readFileLocked(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
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
