package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家个人维度的持久化条目。
 */
public class PlayerDimEntry {

    public final UUID playerId;
    public int dimensionId = Integer.MIN_VALUE;
    public PersonalDimensionRules rules = new PersonalDimensionRules();
    public BlockPos entryPoint = new BlockPos(0, 65, 0);
    public int returnDim = 0;
    public double returnX, returnY, returnZ;
    public float returnYaw, returnPitch;
    public boolean hasReturnPoint = false;

    /**
     * 被允许进入该维度的其他玩家 UUID。
     */
    public final Set<UUID> allowedPlayers = new HashSet<>();

    /**
     * 其他玩家在该维度内的权限。未在 map 中的玩家默认没有任何权限。
     */
    public final Map<UUID, Set<PersonalDimPermission>> permissions = new HashMap<>();

    public PlayerDimEntry(UUID playerId) {
        this.playerId = playerId;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("playerUUID", playerId.toString());
        tag.setInteger("dimensionId", dimensionId);
        tag.setTag("rules", rules.writeToNBT());
        tag.setLong("entryPoint", entryPoint.toLong());
        tag.setInteger("returnDim", returnDim);
        tag.setDouble("returnX", returnX);
        tag.setDouble("returnY", returnY);
        tag.setDouble("returnZ", returnZ);
        tag.setFloat("returnYaw", returnYaw);
        tag.setFloat("returnPitch", returnPitch);
        tag.setBoolean("hasReturnPoint", hasReturnPoint);

        NBTTagList allowed = new NBTTagList();
        for (UUID id : allowedPlayers) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("uuid", id.toString());
            allowed.appendTag(t);
        }
        tag.setTag("allowedPlayers", allowed);

        NBTTagList perms = new NBTTagList();
        for (Map.Entry<UUID, Set<PersonalDimPermission>> e : permissions.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("uuid", e.getKey().toString());
            StringBuilder sb = new StringBuilder();
            for (PersonalDimPermission p : e.getValue()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(p.name());
            }
            t.setString("permissions", sb.toString());
            perms.appendTag(t);
        }
        tag.setTag("permissions", perms);

        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        readFromNBT(tag, 0);
    }

    /**
     * 根据数据版本读取 NBT，便于未来扩展字段时做向后兼容。
     *
     * @param tag     NBT 数据
     * @param version PersonalDimensionData 的版本号
     */
    public void readFromNBT(NBTTagCompound tag, int version) {
        dimensionId = tag.getInteger("dimensionId");
        rules.readFromNBT(tag.getCompoundTag("rules"));
        entryPoint = BlockPos.fromLong(tag.getLong("entryPoint"));
        returnDim = tag.getInteger("returnDim");
        returnX = tag.getDouble("returnX");
        returnY = tag.getDouble("returnY");
        returnZ = tag.getDouble("returnZ");
        returnYaw = tag.getFloat("returnYaw");
        returnPitch = tag.getFloat("returnPitch");
        hasReturnPoint = tag.getBoolean("hasReturnPoint");

        allowedPlayers.clear();
        if (tag.hasKey("allowedPlayers", 9)) {
            NBTTagList list = tag.getTagList("allowedPlayers", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                try {
                    allowedPlayers.add(UUID.fromString(list.getCompoundTagAt(i).getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        permissions.clear();
        if (tag.hasKey("permissions", 9)) {
            NBTTagList list = tag.getTagList("permissions", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                try {
                    UUID id = UUID.fromString(t.getString("uuid"));
                    Set<PersonalDimPermission> set = EnumSet.noneOf(PersonalDimPermission.class);
                    for (String s : t.getString("permissions").split(",")) {
                        if (s.isEmpty()) continue;
                        try {
                            set.add(PersonalDimPermission.valueOf(s));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    permissions.put(id, set);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * 检查指定玩家是否拥有某项权限。
     */
    public boolean hasPermission(UUID playerId, PersonalDimPermission permission) {
        Set<PersonalDimPermission> set = permissions.get(playerId);
        return set != null && set.contains(permission);
    }

    /**
     * 授予指定玩家权限。若玩家不在白名单中，会自动加入白名单。
     */
    public void grantPermission(UUID playerId, PersonalDimPermission permission) {
        allowedPlayers.add(playerId);
        permissions.computeIfAbsent(playerId, k -> EnumSet.noneOf(PersonalDimPermission.class)).add(permission);
    }

    /**
     * 移除指定玩家的某项权限。
     */
    public void revokePermission(UUID playerId, PersonalDimPermission permission) {
        Set<PersonalDimPermission> set = permissions.get(playerId);
        if (set != null) {
            set.remove(permission);
            if (set.isEmpty()) {
                permissions.remove(playerId);
                allowedPlayers.remove(playerId);
            }
        }
    }

    /**
     * 将指定玩家完全从白名单与权限表中移除。
     */
    public void removePlayer(UUID playerId) {
        allowedPlayers.remove(playerId);
        permissions.remove(playerId);
    }

    /**
     * 获取指定玩家的权限集合（只读）。
     */
    public Set<PersonalDimPermission> getPermissions(UUID playerId) {
        Set<PersonalDimPermission> set = permissions.get(playerId);
        return set != null ? Collections.unmodifiableSet(EnumSet.copyOf(set)) : Collections.emptySet();
    }
}
