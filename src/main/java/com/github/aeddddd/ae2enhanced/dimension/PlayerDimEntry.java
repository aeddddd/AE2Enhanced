package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
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
     * 组队白名单：被邀请进入该维度（拥有完整建造/交互能力）的其他玩家 UUID。
     */
    public final Set<UUID> allowedPlayers = new HashSet<>();

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
        // 缺键时保留构造默认值，避免落入 0（主世界 ID）/ (0,0,0) 虚空坐标等危险默认值
        if (tag.hasKey("dimensionId", 99)) {
            dimensionId = tag.getInteger("dimensionId");
        }
        rules.readFromNBT(tag.getCompoundTag("rules"));
        if (tag.hasKey("entryPoint", 99)) {
            entryPoint = BlockPos.fromLong(tag.getLong("entryPoint"));
        }
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
        // 旧存档中的 "permissions" 逐玩家权限表已废弃，读取时直接忽略
    }

    /**
     * 判断条目是否为"空"：既未分配维度，也无白名单/返回点，且规则未被修改。
     * 空条目多为只读查询意外创建，持久化时应跳过。
     */
    public boolean isEmpty() {
        return dimensionId == Integer.MIN_VALUE
                && allowedPlayers.isEmpty()
                && !hasReturnPoint
                && rules.isDefault();
    }

    /**
     * 将指定玩家从组队白名单中移除。
     */
    public void removePlayer(UUID playerId) {
        allowedPlayers.remove(playerId);
    }
}
