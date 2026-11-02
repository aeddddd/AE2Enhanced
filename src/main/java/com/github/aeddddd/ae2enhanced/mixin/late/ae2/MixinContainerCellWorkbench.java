package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.container.slot.SlotFake;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 允许流体容器或气体容器作为假物品放入 Cell Workbench 的 view cell 槽位.
 */
@Mixin(value = AEBaseContainer.class, remap = false)
public class MixinContainerCellWorkbench {

    // Mekanism IGasItem 类的静态缓存, 加载一次, Mekanism 缺席时为 null 并禁用气体检测
    private static final Class<?> GAS_ITEM_CLASS;
    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("mekanism.api.gas.IGasItem");
        } catch (Throwable ignored) {
            // Mekanism 未安装
        }
        GAS_ITEM_CLASS = clazz;
    }

    @Inject(method = "func_184996_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2enhanced$onSlotClick(int slotId, int dragType, ClickType clickType, EntityPlayer player,
                                         CallbackInfoReturnable<ItemStack> cir) {
        if (!((Object) this instanceof ContainerCellWorkbench) || slotId < 0) return;

        ContainerCellWorkbench container = (ContainerCellWorkbench) (Object) this;
        if (slotId >= container.inventorySlots.size()) return;

        Slot slot = container.inventorySlots.get(slotId);
        if (!(slot instanceof SlotFake)) return;

        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return;

        // 流体容器
        if (!com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat.AE2FC_LOADED
                && held.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            IFluidHandlerItem fh = held.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (fh == null) return;

            FluidStack drained = fh.drain(Integer.MAX_VALUE, false);
            if (drained == null || drained.amount == 0) return;

            if (clickType == ClickType.PICKUP && dragType == 0) {
                slot.putStack(ItemFluidDrop.createStack(drained));
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (clickType == ClickType.PICKUP && dragType == 1) {
                FluidStack single = drained.copy();
                single.amount = Math.min(single.amount, 1000);
                ItemStack existing = slot.getStack();
                if (!existing.isEmpty() && ItemFluidDrop.isFluidDrop(existing)) {
                    FluidStack existingFluid = ItemFluidDrop.getFluidStack(existing);
                    if (existingFluid != null && existingFluid.isFluidEqual(single)) {
                        single.amount += existingFluid.amount;
                    }
                }
                slot.putStack(ItemFluidDrop.createStack(single));
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }

        // 气体容器(简化处理：不实现放入逻辑,只阻止默认行为)
        if (GAS_ITEM_CLASS != null && GAS_ITEM_CLASS.isInstance(held.getItem())) {
            // 暂不实现气体容器放入 view cell，阻止默认行为避免气体容器被当作普通物品处理
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
