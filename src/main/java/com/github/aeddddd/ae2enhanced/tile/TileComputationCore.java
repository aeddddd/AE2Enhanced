package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
        syncToClient();
    }

    public int getParallelLimit() {
        return parallelLimit;
    }

    public void setParallelLimit(int parallelLimit) {
        this.parallelLimit = parallelLimit;
        markDirty();
        syncToClient();
    }

    public int getActiveOrderCount() {
        return activeOrderCount;
    }

    public void setActiveOrderCount(int activeOrderCount) {
        this.activeOrderCount = activeOrderCount;
        markDirty();
        syncToClient();
    }

    public void assemble(int parallelLimit) {
        this.formed = true;
        this.parallelLimit = parallelLimit;
        this.activeOrderCount = 0;
        markDirty();
        syncToClient();
    }

    public void disassemble() {
        this.formed = false;
        this.parallelLimit = 0;
        this.activeOrderCount = 0;
        markDirty();
        syncToClient();
    }

    private void syncToClient() {
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        }
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

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState, net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
