package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;

/**
 * 超因果合成接口 TileEntity。
 * 本身不创建独立的 AE 网络节点，而是作为 TileComputationCore 的物理网络接入点。
 * ME 线缆连接到本方块时，实际上接入的是控制器的网格节点。
 */
public class TileSuperCraftingInterface extends TileEntity implements IGridProxyable {

    private BlockPos controllerPos = null;

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(this.pos, world.getBlockState(this.pos), world.getBlockState(this.pos), 2);
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public TileComputationCore getController() {
        if (controllerPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return te instanceof TileComputationCore ? (TileComputationCore) te : null;
    }

    // ---- IGridProxyable ----

    @Override
    public AENetworkProxy getProxy() {
        TileComputationCore controller = getController();
        if (controller != null) {
            return controller.getProxy();
        }
        return null;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        TileComputationCore controller = getController();
        if (controller != null && controller.isFormed()) {
            AENetworkProxy proxy = controller.getProxy();
            return proxy != null ? proxy.getNode() : null;
        }
        return null;
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        TileComputationCore controller = getController();
        return (controller != null && controller.isFormed()) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        if (controllerPos != null && world != null) {
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileComputationCore) {
                ((TileComputationCore) te).disassemble();
            }
        }
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("controllerX")) {
            controllerPos = new BlockPos(
                compound.getInteger("controllerX"),
                compound.getInteger("controllerY"),
                compound.getInteger("controllerZ")
            );
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (controllerPos != null) {
            compound.setInteger("controllerX", controllerPos.getX());
            compound.setInteger("controllerY", controllerPos.getY());
            compound.setInteger("controllerZ", controllerPos.getZ());
        }
        return compound;
    }
}
