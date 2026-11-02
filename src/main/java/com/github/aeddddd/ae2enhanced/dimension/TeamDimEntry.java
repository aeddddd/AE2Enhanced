package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** FTB 团队维度条目；团队 ID 与随机 bindingId 共同防止同名团队继承旧世界。 */
public final class TeamDimEntry {
    public final String teamId;
    public String bindingId;
    public int dimensionId = Integer.MIN_VALUE;
    public PersonalDimensionRules rules = new PersonalDimensionRules();
    public BlockPos entryPoint = new BlockPos(0, 65, 0);
    public FloorDefinition floor = new FloorDefinition();
    public boolean frozen;
    public NBTTagCompound floorTask = new NBTTagCompound();

    public TeamDimEntry(String teamId, String bindingId) {
        this.teamId = teamId;
        this.bindingId = bindingId;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("teamId", teamId);
        tag.setString("bindingId", bindingId);
        tag.setInteger("dimensionId", dimensionId);
        tag.setTag("rules", rules.writeToNBT());
        tag.setLong("entryPoint", entryPoint.toLong());
        tag.setTag("floor", floor.writeToNBT());
        tag.setBoolean("frozen", frozen);
        tag.setTag("floorTask", floorTask.copy());
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("bindingId", 8)) {
            bindingId = tag.getString("bindingId");
        }
        if (tag.hasKey("dimensionId", 99)) {
            dimensionId = tag.getInteger("dimensionId");
        }
        rules.readFromNBT(tag.getCompoundTag("rules"));
        if (tag.hasKey("entryPoint", 99)) {
            entryPoint = BlockPos.fromLong(tag.getLong("entryPoint"));
        }
        floor.readFromNBT(tag.getCompoundTag("floor"));
        frozen = tag.getBoolean("frozen");
        floorTask = tag.getCompoundTag("floorTask").copy();
    }
}
