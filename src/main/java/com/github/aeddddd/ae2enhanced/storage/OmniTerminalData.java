package com.github.aeddddd.ae2enhanced.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局 Omni Terminal 持久化数据(WorldSavedData).
 * 每个终端通过 UUID 对应一份 OmniTerminalStorage,存储在 world 维度数据中.
 * 使用 per-world storage(getPerWorldStorage),每个维度独立.
 * 玩家通常只在主世界使用终端,数据保存在 overworld 即可满足需求.
 */
public class OmniTerminalData extends WorldSavedData {

    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_omni_terminal";

    /** 注意：WorldSavedData 反射构造需要 public Constructor(String). */
    public OmniTerminalData() {
        super(DATA_NAME);
    }

    public OmniTerminalData(String name) {
        super(name);
    }

    private final Map<UUID, OmniTerminalStorage> storages = new HashMap<>();

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        storages.clear();
        NBTTagList list = nbt.getTagList("storages", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            UUID id = entry.getUniqueId("id");
            OmniTerminalStorage storage = new OmniTerminalStorage();
            storage.readFromNBT(entry.getCompoundTag("data"));
            storages.put(id, storage);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, OmniTerminalStorage> e : storages.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setUniqueId("id", e.getKey());
            entry.setTag("data", e.getValue().writeToNBT(new NBTTagCompound()));
            list.appendTag(entry);
        }
        compound.setTag("storages", list);
        return compound;
    }

    /**
     * 获取或创建指定 UUID 的存储.
     */
    public OmniTerminalStorage getOrCreate(UUID id) {
        OmniTerminalStorage storage = storages.get(id);
        if (storage == null) {
            storage = new OmniTerminalStorage();
            storages.put(id, storage);
            markDirty();
        }
        return storage;
    }

    /**
     * 删除指定 UUID 的存储(例如终端被销毁时).
     */
    public void remove(UUID id) {
        if (storages.remove(id) != null) {
            markDirty();
        }
    }

    /**
     * 从世界获取或创建 OmniTerminalData 实例.
     * 优先使用主世界(overworld,dimension=0)的存储,确保跨维度数据一致.
     */
    public static OmniTerminalData get(World world) {
        World overworld = world.getMinecraftServer() != null
                ? world.getMinecraftServer().getWorld(0)
                : world;
        if (overworld == null) {
            overworld = world;
        }
        MapStorage storage = overworld.getPerWorldStorage();
        OmniTerminalData data = (OmniTerminalData) storage.getOrLoadData(OmniTerminalData.class, DATA_NAME);
        if (data == null) {
            data = new OmniTerminalData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }
}
