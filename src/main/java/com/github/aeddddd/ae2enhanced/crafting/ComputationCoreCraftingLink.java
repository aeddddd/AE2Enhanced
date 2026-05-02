package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingLink;
import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Placeholder ICraftingLink for ComputationCoreCPU.
 * P1 skeleton — full state tracking will be implemented in P1-S5.
 */
public class ComputationCoreCraftingLink implements ICraftingLink {

    private final String craftingId = UUID.randomUUID().toString();
    private boolean canceled = false;
    private boolean done = false;

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public void cancel() {
        this.canceled = true;
    }

    @Override
    public boolean isStandalone() {
        return true;
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        compound.setString("craftingId", craftingId);
        compound.setBoolean("canceled", canceled);
        compound.setBoolean("done", done);
    }

    @Override
    public String getCraftingID() {
        return craftingId;
    }

    public void markDone() {
        this.done = true;
    }
}
