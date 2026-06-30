package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import appeng.api.stacks.AEKey;

/**
 * 超维度仓储持久化文件管理。
 * <p>每个 Nexus 对应一个 NBT 文件：{@code <world>/ae2enhanced/storage/<uuid>.dat}。</p>
 */
public final class HyperdimensionalStorageFile {

    private static final String TAG_VERSION = "version";
    private static final String TAG_CONTENTS = "contents";
    private static final String TAG_KEY = "key";
    private static final String TAG_AMOUNT = "amount";
    private static final int CURRENT_VERSION = 1;

    private HyperdimensionalStorageFile() {
    }

    public static Path getStoragePath(MinecraftServer server, UUID nexusId) {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve("ae2enhanced")
                .resolve("storage")
                .resolve(nexusId + ".dat");
    }

    public static HyperdimensionalStorage loadOrCreate(MinecraftServer server, UUID nexusId,
            @Nullable Consumer<HyperdimensionalStorage> changeCallback) {
        HyperdimensionalStorage storage = new HyperdimensionalStorage(nexusId, changeCallback);
        Path path = getStoragePath(server, nexusId);
        if (!Files.exists(path)) {
            return storage;
        }
        try {
            CompoundTag root = NbtIo.read(path.toFile());
            if (root == null) {
                return storage;
            }
            ListTag list = root.getList(TAG_CONTENTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                CompoundTag keyTag = entry.getCompound(TAG_KEY);
                AEKey key = AEKey.fromTagGeneric(keyTag);
                if (key == null) {
                    continue;
                }
                BigInteger amount = new BigInteger(entry.getString(TAG_AMOUNT));
                if (amount.signum() > 0) {
                    storage.set(key, amount);
                }
            }
            storage.markClean();
        } catch (IOException e) {
            // 读取失败时返回空存储，避免崩溃
            e.printStackTrace();
        }
        return storage;
    }

    public static void save(MinecraftServer server, HyperdimensionalStorage storage) {
        if (!storage.isDirty()) {
            return;
        }
        Path path = getStoragePath(server, storage.getNexusId());
        try {
            Files.createDirectories(path.getParent());
            CompoundTag root = new CompoundTag();
            root.putInt(TAG_VERSION, CURRENT_VERSION);
            ListTag list = new ListTag();
            for (var entry : storage.getContents().entrySet()) {
                CompoundTag tag = new CompoundTag();
                tag.put(TAG_KEY, entry.getKey().toTagGeneric());
                tag.putString(TAG_AMOUNT, entry.getValue().toString());
                list.add(tag);
            }
            root.put(TAG_CONTENTS, list);
            File temp = new File(path.toFile().getAbsolutePath() + ".tmp");
            NbtIo.write(root, temp);
            Files.move(temp.toPath(), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            storage.markClean();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
