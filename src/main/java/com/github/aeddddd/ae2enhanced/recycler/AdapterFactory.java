package com.github.aeddddd.ae2enhanced.recycler;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * 根据目标 TileEntity 创建最合适的 TargetAdapter.
 */
public final class AdapterFactory {

    private AdapterFactory() {}

    @Nullable
    public static TargetAdapter create(TileEntity tile, EnumFacing face) {
        if (tile == null || tile.isInvalid()) return null;

        // Tech Reborn：其 Inventory 对所有面禁止提取，必须直接访问输出槽
        if (TechRebornMachineAdapter.isApplicable(tile)) {
            return new TechRebornMachineAdapter(tile, face);
        }

        // TODO: 根据 tile 类型选择专用适配器
        // if (isThermalMachine(tile)) return new ThermalMachineAdapter(tile, face);
        // if (isMekanismMachine(tile)) return new MekanismMachineAdapter(tile, face);
        // if (isStorageDrawer(tile)) return new StorageDrawerAdapter(tile, face);

        // 默认通用适配器
        if (tile.hasCapability(net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face)) {
            return new ForgeItemHandlerAdapter(tile, face);
        }

        return null;
    }
}
