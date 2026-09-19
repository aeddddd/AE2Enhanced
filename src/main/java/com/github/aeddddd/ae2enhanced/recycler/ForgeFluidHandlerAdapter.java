package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEFluidStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 Forge IFluidHandler 适配器.
 */
public class ForgeFluidHandlerAdapter implements FluidTargetAdapter {

    private final TileEntity tile;
    private final EnumFacing face;
    private IFluidHandler handler;

    public ForgeFluidHandlerAdapter(TileEntity tile, EnumFacing face) {
        this.tile = tile;
        this.face = face;
    }

    private IFluidHandler getHandler() {
        if (handler == null && tile != null && !tile.isInvalid()) {
            handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        }
        return handler;
    }

    @Override
    @Nonnull
    public List<FluidStack> scan(boolean simulate) {
        List<FluidStack> result = new ArrayList<>();
        IFluidHandler h = getHandler();
        if (h == null) return result;

        for (IFluidTankProperties prop : h.getTankProperties()) {
            // 按机器声明的罐模式判断：只回收可抽取的罐，输入罐（canDrain == false）不参与
            if (prop == null || !prop.canDrain()) continue;
            FluidStack contents = prop.getContents();
            if (contents != null && contents.amount > 0) {
                result.add(contents.copy());
            }
        }
        return result;
    }

    @Override
    @Nullable
    public FluidStack extract(@Nonnull IAEFluidStack requested, boolean simulate) {
        IFluidHandler h = getHandler();
        if (h == null) return null;

        FluidStack wanted = requested.getFluidStack();
        if (wanted == null || wanted.amount <= 0) return null;
        if (!hasDrainableTank(h, wanted)) return null;

        return h.drain(wanted, !simulate);
    }

    /**
     * 判断目标是否声明了“装有该流体且可抽取”的罐。
     */
    private boolean hasDrainableTank(IFluidHandler handler, FluidStack wanted) {
        IFluidTankProperties[] properties = handler.getTankProperties();
        if (properties == null) return false;
        for (IFluidTankProperties property : properties) {
            if (property == null || !property.canDrain()) continue;
            FluidStack contents = property.getContents();
            if (contents != null && contents.amount > 0 && contents.isFluidEqual(wanted)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void invalidate() {
        handler = null;
    }
}
