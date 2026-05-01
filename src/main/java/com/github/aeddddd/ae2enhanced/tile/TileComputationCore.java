package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Supercausal Computation Core controller TileEntity.
 * Will later implement IGridProxyable + ICraftingProvider + ICraftingMedium.
 */
public class TileComputationCore extends TileEntity {

    private boolean formed = false;
    private int parallelLimit = 0;
    private int activeOrderCount = 0;

    public boolean isFormed() {
        return formed;
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
        markDirty();
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public void setParallelLimit(int parallelLimit) {
        this.parallelLimit = parallelLimit;
        markDirty();
    }

    public int getActiveOrderCount() {
        return activeOrderCount;
    }

    public void setActiveOrderCount(int activeOrderCount) {
        this.activeOrderCount = activeOrderCount;
        markDirty();
    }

    public void assemble(int parallelLimit) {
        this.formed = true;
        this.parallelLimit = parallelLimit;
        this.activeOrderCount = 0;
        markDirty();
    }

    public void disassemble() {
        this.formed = false;
        this.parallelLimit = 0;
        this.activeOrderCount = 0;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.formed = compound.getBoolean("formed");
        this.parallelLimit = compound.getInteger("parallelLimit");
        this.activeOrderCount = compound.getInteger("activeOrderCount");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        compound.setInteger("parallelLimit", parallelLimit);
        compound.setInteger("activeOrderCount", activeOrderCount);
        return compound;
    }
}
