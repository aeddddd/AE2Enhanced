package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;

public class TileAssemblyMeInterface extends TileEntity implements IGridProxyable, ICraftingProvider, ITickable {

    private boolean needsReady = false;

    private BlockPos controllerPos;
    private AENetworkProxy proxy;

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        markDirty();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    private AENetworkProxy createProxy() {
        AENetworkProxy p = new AENetworkProxy(this, "me_interface",
            new net.minecraft.item.ItemStack(ModBlocks.ASSEMBLY_ME_INTERFACE), true);
        p.setValidSides(EnumSet.allOf(EnumFacing.class));
        return p;
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = createProxy();
        }
        return proxy;
    }

    @Override
    public void validate() {
        super.validate();
        needsReady = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (proxy != null) {
            proxy.invalidate();
        }
    }

    @Override
    public void update() {
        if (!needsReady || world == null || world.isRemote || controllerPos == null) {
            return;
        }
        if (!world.isBlockLoaded(controllerPos)) {
            return; // 控制器所在 chunk 未加载，延迟到下次 tick
        }
        needsReady = false;
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof TileAssemblyController && ((TileAssemblyController) te).isFormed()) {
            getProxy().onReady();
        }
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
    }

    // IGridHost
    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        if (controllerPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof TileAssemblyController && ((TileAssemblyController) te).isFormed()) {
            return getProxy().getNode();
        }
        return null;
    }

    @Nonnull
    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        if (controllerPos == null || world == null) return AECableType.NONE;
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof TileAssemblyController) {
            TileAssemblyController controller = (TileAssemblyController) te;
            if (controller.isFormed()) {
                return AECableType.SMART;
            }
        }
        return AECableType.NONE;
    }

    @Override
    public void securityBreak() {
        if (controllerPos != null && world != null) {
            TileEntity te = world.getTileEntity(controllerPos);
            if (te instanceof TileAssemblyController) {
                ((TileAssemblyController) te).disassemble();
            }
        }
    }

    // ICraftingMedium
    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.pushPattern(patternDetails, table);
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.isBusy();
        }
        return false;
    }

    // ICraftingProvider
    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        TileAssemblyController controller = getController();
        if (controller != null && controller.isMeInterfaceActive(pos)) {
            controller.provideCrafting(craftingTracker);
        }
    }

    public TileAssemblyController getController() {
        if (controllerPos == null || world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return te instanceof TileAssemblyController ? (TileAssemblyController) te : null;
    }

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
        if (compound.hasKey("proxy")) {
            getProxy().readFromNBT(compound.getCompoundTag("proxy"));
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
        if (proxy != null) {
            NBTTagCompound proxyTag = new NBTTagCompound();
            proxy.writeToNBT(proxyTag);
            compound.setTag("proxy", proxyTag);
        }
        return compound;
    }
}
