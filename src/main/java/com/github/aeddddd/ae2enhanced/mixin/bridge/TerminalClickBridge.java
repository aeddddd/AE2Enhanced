package com.github.aeddddd.ae2enhanced.mixin.bridge;

import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPickerAction;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.terminal.UnifiedResourceTerminalBridge;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.fakeitem.EssentiaFakeItemChecks;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

/**
 * 终端点击桥接类 —— 处理流体/气体/源质假物品的点击拦截.
 * 由 MixinGuiMEMonitorableClick 在 @Inject 点调用.
 */
public final class TerminalClickBridge {

    private TerminalClickBridge() {}

    /**
     * 在 {@code AEBaseGui.func_184098_a} 的 HEAD 处调用.
     *
     * @return true 表示已处理点击,Mixin 应 cancel 原方法
     */
    public static boolean onHandleMouseClick(AEBaseGui gui, Slot slot, int slotId, int mouseButton, ClickType clickType) {
        if (!(slot instanceof SlotME)) {
            return false;
        }

        SlotME s = (SlotME) slot;
        EntityPlayer player = gui.mc.player;
        ItemStack mouseItem = player.inventory.getItemStack();

        // 中键提取(Picker upgrade)
        if (mouseButton == 2 && clickType == ClickType.CLONE && s.getAEStack() != null) {
            if (gui.inventorySlots instanceof ContainerOmniTerm) {
                ContainerOmniTerm container = (ContainerOmniTerm) gui.inventorySlots;
                if (container.hasPickerUpgrade()) {
                    if (s.getAEStack().getStackSize() > 0) {
                        AE2Enhanced.network.sendToServer(new PacketPickerAction(s.getAEStack()));
                        return true;
                    } else if (s.getAEStack().isCraftable()) {
                        // 缺货但可合成：复用原版 AUTO_CRAFT 打开合成数量界面
                        ((AEBaseContainer) gui.inventorySlots).setTargetStack(s.getAEStack());
                        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
                                InventoryAction.AUTO_CRAFT, gui.inventorySlots.inventorySlots.size(), 0));
                        return true;
                    }
                }
            }
        }

        // 统一资源终端桥接（RF/Mana/Starlight）
        if (UnifiedResourceTerminalBridge.onHandleMouseClick(gui, slot, slotId, mouseButton, clickType)) {
            return true;
        }

        // 手持物品左键点击
        if (!mouseItem.isEmpty() && mouseButton == 0) {
            if (!Ae2fcCompat.AE2FC_LOADED) {
                boolean isFluid = s.getAEStack() != null && s.getAEStack().getItem() == ItemRegistry.FLUID_DROP;
                boolean hasFluidCap = mouseItem.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                boolean hasFluidInHand = hasFluidCap && getFluidFromItem(mouseItem) != null;

                if (hasFluidCap && (isFluid || hasFluidInHand)) {
                    FluidStack fluid = isFluid ? FakeItemRegister.getStack(s.getAEStack()) : null;
                    NBTTagCompound nbt = fluid != null ? fluid.writeToNBT(new NBTTagCompound()) : new NBTTagCompound();
                    AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.FLUID_WORK, nbt));
                    return true;
                }

                if (ItemRegistry.GAS_DROP != null && handleGasMouseClick(s, mouseItem)) {
                    return true;
                }
            }

            if (ItemRegistry.ESSENTIA_DROP != null && EssentiaFakeItemChecks.isEssentiaFakeItem(mouseItem)) {
                return true;
            }
        }

        // 空手持假物品点击
        if (s.getAEStack() != null) {
            boolean isFluidOrGas = false;
            if (!Ae2fcCompat.AE2FC_LOADED) {
                isFluidOrGas = s.getAEStack().getItem() == ItemRegistry.FLUID_DROP
                        || (ItemRegistry.GAS_DROP != null && s.getAEStack().getItem() == ItemRegistry.GAS_DROP);
            }
            boolean isEssentia = ItemRegistry.ESSENTIA_DROP != null && s.getAEStack().getItem() == ItemRegistry.ESSENTIA_DROP;

            if ((isFluidOrGas || isEssentia)
                    && mouseButton != 2
                    && (!s.getAEStack().isCraftable()
                        || mouseButton != 0
                        || (s.getAEStack().getStackSize() != 0L && !GuiScreen.isCtrlKeyDown()))) {

                if (!Ae2fcCompat.AE2FC_LOADED && s.getAEStack().getItem() == ItemRegistry.FLUID_DROP) {
                    NBTTagCompound nbt = s.getAEStack().getDefinition().writeToNBT(new NBTTagCompound());
                    nbt.setBoolean("shift", GuiScreen.isShiftKeyDown());
                    AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.FLUID_OPERATE, nbt));
                } else if (!Ae2fcCompat.AE2FC_LOADED && ItemRegistry.GAS_DROP != null && s.getAEStack().getItem() == ItemRegistry.GAS_DROP) {
                    NBTTagCompound nbt = s.getAEStack().getDefinition().writeToNBT(new NBTTagCompound());
                    nbt.setBoolean("shift", GuiScreen.isShiftKeyDown());
                    AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.GAS_OPERATE, nbt));
                }
                return true;
            }
        }

        return false;
    }

    private static boolean handleGasMouseClick(SlotME s, ItemStack mouseItem) {
        boolean isGas = s.getAEStack() != null && s.getAEStack().getItem() == ItemRegistry.GAS_DROP;
        if (isGas || getGasFromItem(mouseItem) != null) {
            Object gas = isGas ? FakeItemRegister.getStack(s.getAEStack()) : null;
            NBTTagCompound nbt = new NBTTagCompound();
            if (gas != null) {
                try {
                    nbt = (NBTTagCompound) gas.getClass().getMethod("write", NBTTagCompound.class).invoke(gas, new NBTTagCompound());
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to serialize gas NBT in GUI click", e);
                }
            }
            AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.GAS_WORK, nbt));
            return true;
        }
        return false;
    }

    private static FluidStack getFluidFromItem(ItemStack stack) {
        if (!stack.isEmpty() && stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            net.minecraftforge.fluids.capability.IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler != null) {
                FluidStack drained = handler.drain(Integer.MAX_VALUE, false);
                if (drained != null && drained.amount > 0) {
                    return drained;
                }
            }
        }
        return null;
    }

    private static Object getGasFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        try {
            Class<?> iGasItemClass = Class.forName("mekanism.api.gas.IGasItem");
            if (iGasItemClass.isInstance(stack.getItem())) {
                return iGasItemClass.getMethod("getGas", ItemStack.class).invoke(stack.getItem(), stack);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get gas from item", e);
        }
        return null;
    }
}
