package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家个人维度数据的 WorldSavedData，保存在主世界。
 */
public class PersonalDimensionData extends WorldSavedData {

    /**
     * 当前使用的数据名。使用下划线而非冒号，避免 Windows 文件系统
     * 将 {@code modid:data} 解析为 NTFS 备用数据流 (ADS) 而导致数据无法保存。
     */
    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_personal_dimensions";
    /**
     * 旧版本使用的数据名。保留它用于一次性迁移已有数据。
     */
    private static final String LEGACY_DATA_NAME = AE2Enhanced.MOD_ID + ":personal_dimensions";
    private static final int CURRENT_VERSION = 1;

    private final Map<UUID, PlayerDimEntry> entries = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> dimToPlayer = new ConcurrentHashMap<>();

    public PersonalDimensionData() {
        super(DATA_NAME);
    }

    public PersonalDimensionData(String name) {
        super(name);
    }

    /**
     * 获取条目，不存在时创建。仅在真正需要写入（分配维度/邀请/设权限等）时使用。
     */
    public PlayerDimEntry getEntry(UUID playerId) {
        return entries.computeIfAbsent(playerId, PlayerDimEntry::new);
    }

    /**
     * 只读查询条目，不存在时返回 null，不会创建空条目。
     * 只读路径（如 pd info、能力应用）必须使用本方法，避免为任意 UUID
     * 创建空条目并随 NBT 永久保存。
     */
    @Nullable
    public PlayerDimEntry peekEntry(UUID playerId) {
        return entries.get(playerId);
    }

    public java.util.Collection<PlayerDimEntry> getAllEntries() {
        return entries.values();
    }

    @Nullable
    public PlayerDimEntry getEntryByDimensionId(int dimId) {
        UUID id = dimToPlayer.get(dimId);
        return id != null ? entries.get(id) : null;
    }

    @Nullable
    public UUID getPlayerForDimension(int dimId) {
        return dimToPlayer.get(dimId);
    }

    public void updateDimensionMapping(UUID playerId, int dimId) {
        PlayerDimEntry entry = getEntry(playerId);
        if (entry.dimensionId != Integer.MIN_VALUE && entry.dimensionId != dimId) {
            dimToPlayer.remove(entry.dimensionId);
        }
        entry.dimensionId = dimId;
        dimToPlayer.put(dimId, playerId);
        markDirty();
    }

    public void setRules(UUID playerId, PersonalDimensionRules rules) {
        getEntry(playerId).rules = rules.copy();
        markDirty();
    }

    public void setEntryPoint(UUID playerId, net.minecraft.util.math.BlockPos pos) {
        getEntry(playerId).entryPoint = pos;
        markDirty();
    }

    public void setReturnPoint(UUID playerId, int dim, double x, double y, double z, float yaw, float pitch) {
        PlayerDimEntry entry = getEntry(playerId);
        entry.returnDim = dim;
        entry.returnX = x;
        entry.returnY = y;
        entry.returnZ = z;
        entry.returnYaw = yaw;
        entry.returnPitch = pitch;
        entry.hasReturnPoint = true;
        markDirty();
    }

    /**
     * 无条件清除指定玩家的返回点。
     */
    public void clearReturnPoint(UUID playerId) {
        PlayerDimEntry entry = peekEntry(playerId);
        if (entry != null && entry.hasReturnPoint) {
            entry.hasReturnPoint = false;
            markDirty();
        }
    }

    /**
     * 若指定玩家的返回点位于指定维度，则清除该返回点。
     */
    public void clearReturnPointIfInDimension(UUID playerId, int dimId) {
        PlayerDimEntry entry = peekEntry(playerId);
        if (entry != null && entry.hasReturnPoint && entry.returnDim == dimId) {
            entry.hasReturnPoint = false;
            markDirty();
        }
    }

    /**
     * 删除指定玩家的维度数据。用于管理员命令删除/重建维度。
     */
    public void removeEntry(UUID playerId) {
        PlayerDimEntry entry = entries.remove(playerId);
        if (entry != null && entry.dimensionId != Integer.MIN_VALUE) {
            dimToPlayer.remove(entry.dimensionId);
        }
        markDirty();
    }

    /**
     * 从另一个数据实例复制所有条目。用于旧数据名迁移。
     */
    public void copyFrom(PersonalDimensionData other) {
        this.entries.clear();
        this.entries.putAll(other.entries);
        this.dimToPlayer.clear();
        this.dimToPlayer.putAll(other.dimToPlayer);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        dimToPlayer.clear();
        int version = nbt.hasKey("version", 99) ? nbt.getInteger("version") : 0;
        NBTTagList list = nbt.getTagList("entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            UUID id;
            try {
                id = UUID.fromString(tag.getString("playerUUID"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            PlayerDimEntry entry = new PlayerDimEntry(id);
            entry.readFromNBT(tag, version);
            entries.put(id, entry);
            if (entry.dimensionId != Integer.MIN_VALUE) {
                dimToPlayer.put(entry.dimensionId, id);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("version", CURRENT_VERSION);
        NBTTagList list = new NBTTagList();
        for (PlayerDimEntry entry : entries.values()) {
            // 跳过既无维度又无白名单/规则改动的空条目，
            // 避免只读查询意外创建的空条目随 NBT 永久保存
            if (entry.isEmpty()) {
                continue;
            }
            list.appendTag(entry.writeToNBT());
        }
        compound.setTag("entries", list);
        return compound;
    }

    public static PersonalDimensionData get(World world) {
        if (world.isRemote) {
            throw new IllegalStateException("PersonalDimensionData cannot be loaded on the client");
        }
        World overworld = world.getMinecraftServer() != null
                ? world.getMinecraftServer().getWorld(0)
                : world;
        if (overworld == null) overworld = world;
        MapStorage storage = overworld.getMapStorage();
        PersonalDimensionData data = (PersonalDimensionData) storage.getOrLoadData(PersonalDimensionData.class, DATA_NAME);
        if (data == null) {
            // 尝试从旧数据名迁移（旧名使用冒号，在 Windows 上可能保存为 ADS 或无法读取）
            PersonalDimensionData legacy = (PersonalDimensionData) storage.getOrLoadData(PersonalDimensionData.class, LEGACY_DATA_NAME);
            if (legacy != null) {
                data = new PersonalDimensionData();
                data.copyFrom(legacy);
                storage.setData(DATA_NAME, data);
                data.markDirty();
                AE2Enhanced.LOGGER.info("[AE2E] Migrated personal dimension data from legacy name '{}' to '{}'", LEGACY_DATA_NAME, DATA_NAME);
            } else {
                data = new PersonalDimensionData();
                storage.setData(DATA_NAME, data);
            }
        }
        return data;
    }
}
