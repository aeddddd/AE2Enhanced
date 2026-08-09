package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.storage.codec.*;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 超维度仓储中枢的外部文件持久化层(自定义二进制格式 v1).
 * 每个结构对应一个独立目录,数据不写入 NBT/WorldSavedData.
 *
 * <p>文件格式(单文件 .bin)：</p>
 * <pre>
 * Header (16 bytes):
 *   Magic[4]      = "AE2E"
 *   Version       = 1 (int32)
 *   Flags         = 0 (int32, reserved)
 *   EntryCount    = N (int32)
 *
 * Entries:
 *   DescriptorLength  int32
 *   DescriptorBytes   byte[DescriptorLength]
 *   CountSign         byte
 *   CountMagLength    int32
 *   CountMagnitude    byte[CountMagLength]
 * </pre>
 */
public class HyperdimensionalStorageFile {

    public static final int CURRENT_VERSION = 1;
    private static final byte[] MAGIC = "AE2E".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 16;

    private static final ScheduledExecutorService FLUSH_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AE2E-Storage-Flush");
            t.setDaemon(true);
            return t;
        });

    private final UUID nexusId;
    private final File baseDir;
    private final File oldFile;
    private final ScheduledFuture<?> flushTask;
    private volatile boolean dirty = false;
    private volatile boolean itemDirty = false;
    private volatile boolean fluidDirty = false;
    private volatile boolean gasDirty = false;
    private volatile boolean essentiaDirty = false;
    private volatile boolean energyDirty = false;
    private volatile boolean manaDirty = false;
    private volatile boolean starlightDirty = false;
    private volatile boolean closed = false;
    // 全局安全模式：仅迁移失败等无法定位到具体分区的加载失败才置位
    private volatile boolean safeMode = false;
    // 加载失败的分区集合：对应分区拒绝注入/提取，save() 绝不用部分加载的 Map 覆写其文件
    private final Set<StorageSection> failedSections = ConcurrentHashMap.newKeySet();
    // 保存锁：flush 线程与 close 线程可能并发写同一 .tmp，任何时刻只允许一个线程执行保存
    private final Object saveLock = new Object();

    private volatile Map<ItemDescriptor, BigInteger> storageRef = null;
    private volatile Map<FluidDescriptor, BigInteger> fluidStorageRef = null;
    private volatile Map<?, BigInteger> gasStorageRef = null;
    private volatile Map<?, BigInteger> essentiaStorageRef = null;
    private volatile Map<EnergyDescriptor, BigInteger> energyStorageRef = null;
    private volatile Map<?, BigInteger> manaStorageRef = null;
    private volatile Map<?, BigInteger> starlightStorageRef = null;

    // Section files
    private final File itemFile;
    private final File fluidFile;
    private final File energyFile;
    private File manaFile = null;
    private File starlightFile = null;
    private File gasFile = null;
    private File essentiaFile = null;

    // Codecs (unconditional sections use typed references)
    private final DescriptorCodec<ItemDescriptor> itemCodec = ItemDescriptorCodec.INSTANCE;
    private final DescriptorCodec<FluidDescriptor> fluidCodec = FluidDescriptorCodec.INSTANCE;
    private final DescriptorCodec<EnergyDescriptor> energyCodec = EnergyDescriptorCodec.INSTANCE;
    // Conditional codecs loaded via reflection to avoid NoClassDefFoundError
    private Object gasCodec = null;
    private Object essentiaCodec = null;
    private Object manaCodec = null;
    private Object starlightCodec = null;

    public HyperdimensionalStorageFile(World world, UUID nexusId) {
        this.nexusId = nexusId;
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists() && !storageDir.mkdirs()) {
            AE2Enhanced.LOGGER.warn("Failed to create storage directory: {}", storageDir.getAbsolutePath());
        }

        this.baseDir = new File(storageDir, nexusId.toString());
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            AE2Enhanced.LOGGER.warn("Failed to create storage base directory: {}", baseDir.getAbsolutePath());
        }

        this.oldFile = new File(storageDir, nexusId.toString() + ".dat");
        this.itemFile = new File(baseDir, "items.bin");
        this.fluidFile = new File(baseDir, "fluids.bin");
        this.energyFile = new File(baseDir, "energy.bin");

        // Migrate old single-file NBT format if present
        if (oldFile.exists()) {
            migrateFromOldFormat();
        }

        initConditionalCodecs();

        int flushInterval = AE2EnhancedConfig.storage.flushIntervalSeconds;
        this.flushTask = FLUSH_EXECUTOR.scheduleWithFixedDelay(this::flush, flushInterval, flushInterval, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private void initConditionalCodecs() {
        // GasDescriptorCodec / EssentiaDescriptorCodec / ManaDescriptorCodec / StarlightDescriptorCodec
        // 类本身不硬引用可选 Mod 类,但为了绝对安全(JVM 链接阶段行为不确定),仍通过反射加载.
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.storage.codec.GasDescriptorCodec");
            this.gasCodec = clazz.getField("INSTANCE").get(null);
            this.gasFile = new File(baseDir, "gases.bin");
        } catch (Throwable e) {
            this.gasCodec = null;
            this.gasFile = null;
        }
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.storage.codec.EssentiaDescriptorCodec");
            this.essentiaCodec = clazz.getField("INSTANCE").get(null);
            this.essentiaFile = new File(baseDir, "essentias.bin");
        } catch (Throwable e) {
            this.essentiaCodec = null;
            this.essentiaFile = null;
        }
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.storage.codec.ManaDescriptorCodec");
            this.manaCodec = clazz.getField("INSTANCE").get(null);
            this.manaFile = new File(baseDir, "mana.bin");
        } catch (Throwable e) {
            this.manaCodec = null;
            this.manaFile = null;
        }
        try {
            Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.storage.codec.StarlightDescriptorCodec");
            this.starlightCodec = clazz.getField("INSTANCE").get(null);
            this.starlightFile = new File(baseDir, "starlight.bin");
        } catch (Throwable e) {
            this.starlightCodec = null;
            this.starlightFile = null;
        }
    }

    // ---- Load ----

    public void load(Map<ItemDescriptor, BigInteger> target) {
        loadSection(itemFile, itemCodec, target, "item");
    }

    public void loadFluids(Map<FluidDescriptor, BigInteger> target) {
        loadSection(fluidFile, fluidCodec, target, "fluid");
    }

    @SuppressWarnings("unchecked")
    public void loadGases(Map<?, BigInteger> target) {
        loadSectionReflective(gasFile, gasCodec, target, "gas");
    }

    @SuppressWarnings("unchecked")
    public void loadEssentias(Map<?, BigInteger> target) {
        loadSectionReflective(essentiaFile, essentiaCodec, target, "essentia");
    }

    public void loadEnergy(Map<EnergyDescriptor, BigInteger> target) {
        loadSection(energyFile, energyCodec, target, "energy");
    }

    @SuppressWarnings("unchecked")
    public void loadMana(Map<?, BigInteger> target) {
        loadSectionReflective(manaFile, manaCodec, target, "mana");
    }

    @SuppressWarnings("unchecked")
    public void loadStarlight(Map<?, BigInteger> target) {
        loadSectionReflective(starlightFile, starlightCodec, target, "starlight");
    }

    private <D extends Descriptor> void loadSection(File file, DescriptorCodec<D> codec, Map<D, BigInteger> target, String typeName) {
        if (file == null || codec == null || target == null) return;
        if (!file.exists()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            readAndValidateHeader(in, typeName);
            int entryCount = in.readInt();
            for (int i = 0; i < entryCount; i++) {
                D descriptor = readDescriptor(in, codec);
                BigInteger count = readCount(in);
                if (descriptor != null) {
                    target.put(descriptor, count);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to load {} storage from file: {}. Entering safe mode (read-only).", typeName, file.getAbsolutePath(), e);
            markSectionFailed(typeName);
        }
    }

    /**
     * 将加载失败的分区记录为失败状态：该分区拒绝注入/提取，save() 跳过，
     * 避免"部分加载的 Map"在后续保存时覆写未能完整解析的文件导致永久数据丢失。
     */
    private void markSectionFailed(String typeName) {
        StorageSection section = sectionFor(typeName);
        if (section != null) {
            failedSections.add(section);
        } else {
            safeMode = true;
        }
    }

    private static StorageSection sectionFor(String typeName) {
        switch (typeName) {
            case "item": return StorageSection.ITEM;
            case "fluid": return StorageSection.FLUID;
            case "gas": return StorageSection.GAS;
            case "essentia": return StorageSection.ESSENTIA;
            case "energy": return StorageSection.ENERGY;
            case "mana": return StorageSection.MANA;
            case "starlight": return StorageSection.STARLIGHT;
            default: return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSectionReflective(File file, Object codec, Map<?, BigInteger> target, String typeName) {
        if (file == null || codec == null || target == null) {
            return;
        }
        if (!file.exists()) {
            return;
        }
        try {
            java.lang.reflect.Method readMethod = codec.getClass().getMethod("read", DataInput.class);
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                readAndValidateHeader(in, typeName);
                int entryCount = in.readInt();
                int loaded = 0;
                for (int i = 0; i < entryCount; i++) {
                    int len = in.readInt();
                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    try (java.io.DataInputStream descIn = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))) {
                        Object descriptor = readMethod.invoke(codec, descIn);
                        BigInteger count = readCount(in);
                        if (descriptor != null) {
                            @SuppressWarnings("unchecked")
                            Map<Object, BigInteger> rawTarget = (Map<Object, BigInteger>) (Map<?, ?>) target;
                            rawTarget.put(descriptor, count);
                            loaded++;
                        } else {
                        }
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to load {} storage from file: {}. Entering safe mode (read-only).", typeName, file.getAbsolutePath(), e);
            markSectionFailed(typeName);
        }
    }

    private void readAndValidateHeader(DataInputStream in, String typeName) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Invalid magic for " + typeName);
        }
        int version = in.readInt();
        if (version > CURRENT_VERSION) {
            throw new IOException("Version " + version + " > current " + CURRENT_VERSION + " for " + typeName);
        }
        in.readInt(); // flags, reserved
    }

    private <D extends Descriptor> D readDescriptor(DataInputStream in, DescriptorCodec<D> codec) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        try (DataInputStream descIn = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return codec.read(descIn);
        }
    }

    private BigInteger readCount(DataInputStream in) throws IOException {
        in.readByte(); // sign (BigInteger.toByteArray() embeds sign, so we skip the explicit sign here)
        int magLen = in.readInt();
        byte[] mag = new byte[magLen];
        in.readFully(mag);
        return new BigInteger(mag);
    }

    // ---- Save ----

    public boolean save() {
        // 实例级锁：flush 线程与 close 线程不得并发执行保存（写同一 .tmp）
        synchronized (saveLock) {
            boolean itemOk = true, fluidOk = true, energyOk = true, gasOk = true, essentiaOk = true, manaOk = true, starlightOk = true;
            // 加载失败的分区直接跳过：绝不用部分加载的 Map 覆写未能完整解析的文件，保留 dirty 标记
            if (itemDirty) {
                if (failedSections.contains(StorageSection.ITEM)) {
                    itemOk = false;
                } else {
                    itemOk = saveSection(itemFile, itemCodec, storageRef, "item");
                    if (itemOk) itemDirty = false;
                }
            }
            if (fluidDirty) {
                if (failedSections.contains(StorageSection.FLUID)) {
                    fluidOk = false;
                } else {
                    fluidOk = saveSection(fluidFile, fluidCodec, fluidStorageRef, "fluid");
                    if (fluidOk) fluidDirty = false;
                }
            }
            if (energyDirty) {
                if (failedSections.contains(StorageSection.ENERGY)) {
                    energyOk = false;
                } else {
                    energyOk = saveSection(energyFile, energyCodec, energyStorageRef, "energy");
                    if (energyOk) energyDirty = false;
                }
            }
            if (gasDirty && gasFile != null && gasCodec != null && gasStorageRef != null) {
                if (failedSections.contains(StorageSection.GAS)) {
                    gasOk = false;
                } else {
                    gasOk = saveSectionReflective(gasFile, gasCodec, gasStorageRef, "gas");
                    if (gasOk) gasDirty = false;
                }
            }
            if (essentiaDirty && essentiaFile != null && essentiaCodec != null && essentiaStorageRef != null) {
                if (failedSections.contains(StorageSection.ESSENTIA)) {
                    essentiaOk = false;
                } else {
                    essentiaOk = saveSectionReflective(essentiaFile, essentiaCodec, essentiaStorageRef, "essentia");
                    if (essentiaOk) essentiaDirty = false;
                }
            }
            if (manaDirty && manaFile != null && manaCodec != null && manaStorageRef != null) {
                if (failedSections.contains(StorageSection.MANA)) {
                    manaOk = false;
                } else {
                    manaOk = saveSectionReflective(manaFile, manaCodec, manaStorageRef, "mana");
                    if (manaOk) manaDirty = false;
                }
            }
            if (starlightDirty && starlightFile != null && starlightCodec != null && starlightStorageRef != null) {
                if (failedSections.contains(StorageSection.STARLIGHT)) {
                    starlightOk = false;
                } else {
                    starlightOk = saveSectionReflective(starlightFile, starlightCodec, starlightStorageRef, "starlight");
                    if (starlightOk) starlightDirty = false;
                }
            }
            dirty = itemDirty || fluidDirty || gasDirty || essentiaDirty || energyDirty || manaDirty || starlightDirty;
            return itemOk && fluidOk && energyOk && gasOk && essentiaOk && manaOk && starlightOk;
        }
    }

    private <D extends Descriptor> boolean saveSection(File file, DescriptorCodec<D> codec, Map<D, BigInteger> source, String typeName) {
        if (file == null || codec == null) return true;
        File tmpFile = new File(file.getAbsolutePath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
            writeHeader(out, source != null ? source.size() : 0);
            if (source != null) {
                for (Map.Entry<D, BigInteger> entry : source.entrySet()) {
                    writeEntry(out, codec, entry.getKey(), entry.getValue());
                }
            }
            out.flush();
        } catch (IOException e) {
            // 保存失败不置安全模式：仅记录日志并保留 dirty，下次 flush 重试
            AE2Enhanced.LOGGER.error("[AE2E] Failed to write {} temp file: {}", typeName, tmpFile.getAbsolutePath(), e);
            return false;
        }
        return atomicMove(tmpFile, file, typeName);
    }

    @SuppressWarnings("unchecked")
    private boolean saveSectionReflective(File file, Object codec, Map<?, BigInteger> source, String typeName) {
        if (file == null || codec == null) {
            return true;
        }
        try {
            java.lang.reflect.Method writeMethod = codec.getClass().getMethod("write", DataOutput.class, Descriptor.class);
            File tmpFile = new File(file.getAbsolutePath() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
                writeHeader(out, source != null ? source.size() : 0);
                if (source != null) {
                    int written = 0;
                    for (Map.Entry<?, BigInteger> entry : source.entrySet()) {
                        writeEntryReflective(out, codec, writeMethod, (Descriptor) entry.getKey(), entry.getValue());
                        written++;
                    }
                }
                out.flush();
            } catch (IOException e) {
                // 保存失败不置安全模式：仅记录日志并保留 dirty，下次 flush 重试
                AE2Enhanced.LOGGER.error("[AE2E] Failed to write {} temp file: {}", typeName, tmpFile.getAbsolutePath(), e);
                return false;
            }
            return atomicMove(tmpFile, file, typeName);
        } catch (NoSuchMethodException e) {
            AE2Enhanced.LOGGER.error("[AE2E] Codec missing write method for {}", typeName, e);
            return false;
        }
    }

    private void writeHeader(DataOutputStream out, int entryCount) throws IOException {
        out.write(MAGIC);
        out.writeInt(CURRENT_VERSION);
        out.writeInt(0); // flags
        out.writeInt(entryCount);
    }

    private <D extends Descriptor> void writeEntry(DataOutputStream out, DescriptorCodec<D> codec, D descriptor, BigInteger count) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream descOut = new DataOutputStream(baos);
        codec.write(descOut, descriptor);
        descOut.flush();
        byte[] descBytes = baos.toByteArray();

        out.writeInt(descBytes.length);
        out.write(descBytes);
        writeCount(out, count);
    }

    private void writeEntryReflective(DataOutputStream out, Object codec, java.lang.reflect.Method writeMethod,
                                       Descriptor descriptor, BigInteger count) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream descOut = new DataOutputStream(baos);
            writeMethod.invoke(codec, descOut, descriptor);
            descOut.flush();
            byte[] descBytes = baos.toByteArray();

            out.writeInt(descBytes.length);
            out.write(descBytes);
            writeCount(out, count);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Reflective codec write failed", e);
        }
    }

    private void writeCount(DataOutputStream out, BigInteger count) throws IOException {
        out.writeByte(count.signum());
        byte[] mag = count.toByteArray();
        out.writeInt(mag.length);
        out.write(mag);
    }

    private boolean atomicMove(File tmpFile, File targetFile, String typeName) {
        try {
            Files.move(tmpFile.toPath(), targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            // 原子移动失败不置安全模式：仅记录日志并保留 dirty，下次 flush 重试
            AE2Enhanced.LOGGER.error("[AE2E] Failed to save {} storage file: {}", typeName, targetFile.getAbsolutePath(), e);
            return false;
        }
    }

    // ---- Migration ----

    private void migrateFromOldFormat() {
        AE2Enhanced.LOGGER.info("[AE2E] Migrating old NBT format storage for nexus {} to new binary format", nexusId);
        try {
            NBTTagCompound root = CompressedStreamTools.read(oldFile);
            if (root == null) return;

            // Items
            migrateNbtListToBinary(root, "items", itemFile, (tag, out) -> {
                ItemDescriptor d = ItemDescriptor.fromNBT(tag);
                if (d == null) return false;
                itemCodec.write(out, d);
                return true;
            });

            // Fluids
            migrateNbtListToBinary(root, "fluids", fluidFile, (tag, out) -> {
                FluidDescriptor d = FluidDescriptor.fromNBT(tag);
                if (d == null) return false;
                fluidCodec.write(out, d);
                return true;
            });

            // Energy
            migrateNbtListToBinary(root, "energy", energyFile, (tag, out) -> {
                EnergyDescriptor d = EnergyDescriptor.fromNBT(tag);
                if (d == null) return false;
                energyCodec.write(out, d);
                return true;
            });

            // Gases
            if (gasFile != null && gasCodec != null) {
                try {
                    java.lang.reflect.Method writeMethod = gasCodec.getClass().getMethod("write", DataOutput.class, Descriptor.class);
                    migrateNbtListToBinary(root, "gases", gasFile, (tag, out) -> {
                        Object d = GasDescriptor.fromNBT(tag);
                        if (d == null) return false;
                        writeMethod.invoke(gasCodec, out, d);
                        return true;
                    });
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to migrate gas section", e);
                }
            }

            // Essentias
            if (essentiaFile != null && essentiaCodec != null) {
                try {
                    java.lang.reflect.Method writeMethod = essentiaCodec.getClass().getMethod("write", DataOutput.class, Descriptor.class);
                    migrateNbtListToBinary(root, "essentias", essentiaFile, (tag, out) -> {
                        Object d = EssentiaDescriptor.fromNBT(tag);
                        if (d == null) return false;
                        writeMethod.invoke(essentiaCodec, out, d);
                        return true;
                    });
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to migrate essentia section", e);
                }
            }

            // Backup old file
            File backup = new File(oldFile.getAbsolutePath() + ".backup");
            Files.move(oldFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            AE2Enhanced.LOGGER.info("[AE2E] Migration complete. Old file backed up to {}", backup.getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to migrate old storage format for nexus {}", nexusId, e);
            safeMode = true;
        }
    }

    @FunctionalInterface
    private interface NbtEntryWriter {
        boolean write(NBTTagCompound tag, DataOutput out) throws Exception;
    }

    private void migrateNbtListToBinary(NBTTagCompound root, String nbtKey, File targetFile, NbtEntryWriter writer) throws Exception {
        if (!root.hasKey(nbtKey, 9) || targetFile == null) return;
        NBTTagList list = root.getTagList(nbtKey, 10);
        // 先把有效条目序列化到临时缓冲并统计实际条数，再按实际条数写头部；
        // 直接按 list.tagCount() 写头部再跳过无效条目会导致计数不符，下次加载 EOF
        ByteArrayOutputStream entriesBaos = new ByteArrayOutputStream();
        DataOutputStream entriesOut = new DataOutputStream(entriesBaos);
        int written = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            BigInteger count;
            try {
                count = new BigInteger(tag.getString("Count"));
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
        File tmpFile = new File(targetFile.getAbsolutePath() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)))) {
            writeHeader(out, written);
            out.write(entriesBaos.toByteArray());
            out.flush();
        }
        Files.move(tmpFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ---- Lifecycle ----

    public void markDirty() {
        if (!this.dirty) {
            this.dirty = true;
        }
    }

    public void markDirty(StorageSection section) {
        switch (section) {
            case ITEM:
                if (!this.itemDirty) this.itemDirty = true;
                break;
            case FLUID:
                if (!this.fluidDirty) this.fluidDirty = true;
                break;
            case GAS:
                if (!this.gasDirty) this.gasDirty = true;
                break;
            case ESSENTIA:
                if (!this.essentiaDirty) this.essentiaDirty = true;
                break;
            case ENERGY:
                if (!this.energyDirty) this.energyDirty = true;
                break;
            case MANA:
                if (!this.manaDirty) this.manaDirty = true;
                break;
            case STARLIGHT:
                if (!this.starlightDirty) this.starlightDirty = true;
                break;
        }
        markDirty();
    }

    public void setStorageRef(Map<ItemDescriptor, BigInteger> ref) {
        this.storageRef = ref;
    }

    public void setFluidStorageRef(Map<FluidDescriptor, BigInteger> ref) {
        this.fluidStorageRef = ref;
    }

    public void setGasStorageRef(Map<?, BigInteger> ref) {
        this.gasStorageRef = ref;
    }

    public void setEssentiaStorageRef(Map<?, BigInteger> ref) {
        this.essentiaStorageRef = ref;
    }

    public void setEnergyStorageRef(Map<EnergyDescriptor, BigInteger> ref) {
        this.energyStorageRef = ref;
    }

    public void setManaStorageRef(Map<?, BigInteger> ref) {
        this.manaStorageRef = ref;
    }

    public void setStarlightStorageRef(Map<?, BigInteger> ref) {
        this.starlightStorageRef = ref;
    }

    private void flush() {
        if (!dirty || closed) return;
        try {
            save(); // save() 内部已按 section 重置 dirty 并更新全局 dirty
        } catch (Throwable t) {
            // scheduleWithFixedDelay 任务体抛异常会永久压制后续周期执行，必须兜底并记录日志
            AE2Enhanced.LOGGER.error("[AE2E] Periodic storage flush failed for nexus {}", nexusId, t);
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (flushTask != null) {
            flushTask.cancel(false);
        }
        boolean saved = save();
    }

    public boolean isClosed() {
        return closed;
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
}
