package com.github.aeddddd.ae2enhanced.util;
import com.github.aeddddd.ae2enhanced.util.essentia.EssentiaChannelAccessor;
import com.github.aeddddd.ae2enhanced.util.reflection.GasReflectionHelper;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

/**
 * 统一探测目标容器的四种 Capability(物品 / 流体 / 气体 / 源质).
 *
 * 替代 Part 类中重复编写的 capability 探测块：
 *   hasItemCap = target.hasCapability(...);
 *   hasFluidCap = target.hasCapability(...);
 *   hasGasCap = Loader.isModLoaded(...) && ...;
 *   hasEssentiaCap = ...Class.forName(...)...;
 */
public final class CapabilityProbe {

    public final boolean hasItem;
    public final boolean hasFluid;
    public final boolean hasGas;
    public final boolean hasEssentia;

    public CapabilityProbe(TileEntity target, EnumFacing opposite) {
        this.hasItem = target.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, opposite);
        this.hasFluid = target.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, opposite);
        this.hasGas = GasReflectionHelper.isAvailable()
                && GasReflectionHelper.getGasHandler(target, opposite) != null;
        this.hasEssentia = EssentiaChannelAccessor.isAvailable()
                && EssentiaChannelAccessor.isEssentiaTransport(target);
    }

    public boolean hasAny() {
        return hasItem || hasFluid || hasGas || hasEssentia;
    }
}
