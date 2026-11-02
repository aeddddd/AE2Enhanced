package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class ComputationCoreTaskSummary {
    public final String name;
    public final ItemStack output;
    public final long startItemCount;
    public final long remainingItemCount;
    public final long elapsedTime;

    public ComputationCoreTaskSummary(String name, ItemStack output, long startItemCount,
            long remainingItemCount, long elapsedTime) {
        this.name = name;
        this.output = output;
        this.startItemCount = startItemCount;
        this.remainingItemCount = remainingItemCount;
        this.elapsedTime = elapsedTime;
    }

    public static ComputationCoreTaskSummary readFromNBT(NBTTagCompound tag) {
        ItemStack output = tag.hasKey("output", 10) ? new ItemStack(tag.getCompoundTag("output")) : ItemStack.EMPTY;
        return new ComputationCoreTaskSummary(tag.getString("name"), output, tag.getLong("start"),
                tag.getLong("remaining"), tag.getLong("elapsed"));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);
        if (!output.isEmpty()) tag.setTag("output", output.serializeNBT());
        tag.setLong("start", startItemCount);
        tag.setLong("remaining", remainingItemCount);
        tag.setLong("elapsed", elapsedTime);
        return tag;
    }
}
