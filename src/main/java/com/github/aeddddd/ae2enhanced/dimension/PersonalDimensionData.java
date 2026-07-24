package com.github.aeddddd.ae2enhanced.dimension;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;

/**
 * 玩家个人维度数据的 SavedData,保存在主世界.
 */
public class PersonalDimensionData extends SavedData {

    private static final String DATA_NAME = "ae2enhanced_personal_dimensions";
    private static final int CURRENT_VERSION = 1;

    private final Map<UUID, PlayerDimEntry> entries = new ConcurrentHashMap<>();

    public PlayerDimEntry getEntry(UUID playerId) {
        return entries.computeIfAbsent(playerId, PlayerDimEntry::new);
    }

    public Collection<PlayerDimEntry> getAllEntries() {
        return entries.values();
    }

    public void setRules(UUID playerId, PersonalDimensionRules rules) {
        getEntry(playerId).rules = rules.copy();
        setDirty();
    }

    public void setColorScheme(UUID playerId, FloorColorScheme scheme) {
        getEntry(playerId).colorScheme = scheme.copy();
        setDirty();
    }

    /**
     * 删除指定玩家的维度数据.用于管理员命令删除/重建维度.
     */
    public void removeEntry(UUID playerId) {
        entries.remove(playerId);
        setDirty();
    }

    public static PersonalDimensionData load(CompoundTag tag) {
        PersonalDimensionData data = new PersonalDimensionData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID id;
            try {
                id = UUID.fromString(entryTag.getString("playerUUID"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            PlayerDimEntry entry = new PlayerDimEntry(id);
            entry.readFromNBT(entryTag);
            data.entries.put(id, entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", CURRENT_VERSION);
        ListTag list = new ListTag();
        for (PlayerDimEntry entry : entries.values()) {
            list.add(entry.writeToNBT());
        }
        tag.put("entries", list);
        return tag;
    }

    public static PersonalDimensionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PersonalDimensionData::load, PersonalDimensionData::new, DATA_NAME);
    }
}
