package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;

/**
 * 委托控制器代理的网络接口 TileEntity 基类.
 *
 * ME 接口方块本身不创建独立 AE 节点,而是将网络接入委托给对应的控制器.
 * 统一封装 controllerPos 读写、getProxy/getGridNode 委托、securityBreak 转发,
 * 消除 TileAssemblyMeInterface / TileHyperdimensionalMeInterface / TileSuperCraftingInterface
 * 中各自重复的 ~30 行样板代码.
 *
 * @param <C> 控制器类型,必须实现 IGridProxyable
 */
public abstract class TileDelegatedProxyBase<C extends TileEntity & IGridProxyable> extends TileEntity implements IGridProxyable {

    private BlockPos controllerPos;

    // ---- 子类必须实现 ----

    protected abstract Class<C> getControllerClass();

    protected abstract boolean isControllerFormed(C controller);

    // ---- Controller 访问 ----

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        markDirty();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public C getController() {
        if (controllerPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return getControllerClass().isInstance(te) ? getControllerClass().cast(te) : null;
    }

    // ---- IGridProxyable 实现 ----

    @Override
    public AENetworkProxy getProxy() {
        C controller = getController();
        return controller != null ? controller.getProxy() : null;
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
        C controller = getController();
        if (controller != null && isControllerFormed(controller)) {
            AENetworkProxy proxy = controller.getProxy();
            return proxy != null ? proxy.getNode() : null;
        }
        return null;
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        C controller = getController();
        return (controller != null && isControllerFormed(controller)) ? AECableType.SMART : AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        C controller = getController();
        if (controller instanceof TileAENetworkBase) {
            ((TileAENetworkBase) controller).disassemble();
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
