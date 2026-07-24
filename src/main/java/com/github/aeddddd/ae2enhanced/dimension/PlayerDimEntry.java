package com.github.aeddddd.ae2enhanced.dimension;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;

/**
 * 玩家个人维度的持久化条目.
 *
 * <p>与 1.12 版本的差异：维度 ID 由 int 改为从玩家 UUID 确定性推导的
 * {@code ResourceKey<Level>},因此只需记录"是否已创建"标记.</p>
 */
public class PlayerDimEntry {

    public final UUID playerId;
    /**
     * 是否已为该玩家创建个人维度.
     */
    public boolean created = false;
    public PersonalDimensionRules rules = new PersonalDimensionRules();
    public BlockPos entryPoint = new BlockPos(0, 65, 0);
    /**
     * 返回点所在维度的 ResourceLocation 字符串（如 "minecraft:overworld"）.
     */
    public String returnDim = "minecraft:overworld";
    public double returnX, returnY, returnZ;
    public float returnYaw, returnPitch;
    public boolean hasReturnPoint = false;

    /**
     * 地板颜色方案(创建维度时在创建向导中确定,之后可在管理器中更改).
     */
    public FloorColorScheme colorScheme = FloorColorScheme.createDefault();

    /**
     * 被允许进入该维度的其他玩家 UUID.
     */
    public final Set<UUID> allowedPlayers = new HashSet<>();

    /**
     * 其他玩家在该维度内的权限.未在 map 中的玩家默认没有任何权限.
     */
    public final Map<UUID, Set<PersonalDimPermission>> permissions = new HashMap<>();

    public PlayerDimEntry(UUID playerId) {
        this.playerId = playerId;
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("playerUUID", playerId.toString());
        tag.putBoolean("created", created);
        tag.put("rules", rules.writeToNBT());
        tag.putLong("entryPoint", entryPoint.asLong());
        tag.putString("returnDim", returnDim);
        tag.putDouble("returnX", returnX);
        tag.putDouble("returnY", returnY);
        tag.putDouble("returnZ", returnZ);
        tag.putFloat("returnYaw", returnYaw);
        tag.putFloat("returnPitch", returnPitch);
        tag.putBoolean("hasReturnPoint", hasReturnPoint);
        tag.put("colorScheme", colorScheme.writeToNBT());

        ListTag allowed = new ListTag();
        for (UUID id : allowedPlayers) {
            CompoundTag t = new CompoundTag();
            t.putString("uuid", id.toString());
            allowed.add(t);
        }
        tag.put("allowedPlayers", allowed);

        ListTag perms = new ListTag();
        for (Map.Entry<UUID, Set<PersonalDimPermission>> e : permissions.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("uuid", e.getKey().toString());
            StringBuilder sb = new StringBuilder();
            for (PersonalDimPermission p : e.getValue()) {
                if (sb.length() > 0)
                    sb.append(',');
                sb.append(p.name());
            }
            t.putString("permissions", sb.toString());
            perms.add(t);
        }
        tag.put("permissions", perms);

        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        created = tag.getBoolean("created");
        rules.readFromNBT(tag.getCompound("rules"));
        entryPoint = BlockPos.of(tag.getLong("entryPoint"));
        returnDim = tag.getString("returnDim");
        if (returnDim.isEmpty()) {
            returnDim = "minecraft:overworld";
        }
        returnX = tag.getDouble("returnX");
        returnY = tag.getDouble("returnY");
        returnZ = tag.getDouble("returnZ");
        returnYaw = tag.getFloat("returnYaw");
        returnPitch = tag.getFloat("returnPitch");
        hasReturnPoint = tag.getBoolean("hasReturnPoint");
        if (tag.contains("colorScheme", Tag.TAG_COMPOUND)) {
            colorScheme.readFromNBT(tag.getCompound("colorScheme"));
        }

        allowedPlayers.clear();
        if (tag.contains("allowedPlayers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("allowedPlayers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try {
                    allowedPlayers.add(UUID.fromString(list.getCompound(i).getString("uuid")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        permissions.clear();
        if (tag.contains("permissions", Tag.TAG_LIST)) {
            ListTag list = tag.getList("permissions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                try {
                    UUID id = UUID.fromString(t.getString("uuid"));
                    Set<PersonalDimPermission> set = EnumSet.noneOf(PersonalDimPermission.class);
                    for (String s : t.getString("permissions").split(",")) {
                        if (s.isEmpty())
                            continue;
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
     * 检查指定玩家是否拥有某项权限.
     */
    public boolean hasPermission(UUID playerId, PersonalDimPermission permission) {
        Set<PersonalDimPermission> set = permissions.get(playerId);
        return set != null && set.contains(permission);
    }

    /**
     * 授予指定玩家权限.若玩家不在白名单中,会自动加入白名单.
     */
    public void grantPermission(UUID playerId, PersonalDimPermission permission) {
        allowedPlayers.add(playerId);
        permissions.computeIfAbsent(playerId, k -> EnumSet.noneOf(PersonalDimPermission.class)).add(permission);
    }

    /**
     * 移除指定玩家的某项权限.
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
     * 将指定玩家完全从白名单与权限表中移除.
     */
    public void removePlayer(UUID playerId) {
        allowedPlayers.remove(playerId);
        permissions.remove(playerId);
    }

    /**
     * 获取指定玩家的权限集合（只读）.
     */
    public Set<PersonalDimPermission> getPermissions(UUID playerId) {
        Set<PersonalDimPermission> set = permissions.get(playerId);
        return set != null ? Collections.unmodifiableSet(EnumSet.copyOf(set)) : Collections.emptySet();
    }
}
