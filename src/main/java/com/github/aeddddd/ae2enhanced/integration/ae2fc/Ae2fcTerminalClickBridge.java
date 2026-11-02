package com.github.aeddddd.ae2enhanced.integration.ae2fc;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidPatternHelper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class Ae2fcTerminalClickBridge {
    private static final String ACTION_CLASS = "com.glodblock.github.network.CpacketMEMonitorableAction";
    private static final String FAKE_REGISTER_CLASS = "com.glodblock.github.common.item.fake.FakeItemRegister";
    private static final String FLUID_CRAFT_CLASS = "com.glodblock.github.FluidCraft";

    private Ae2fcTerminalClickBridge() {
    }

    public static boolean onHandleMouseClick(AEBaseGui gui, SlotME slot, int mouseButton) {
        IAEItemStack aeStack = slot.getAEStack();
        if (mouseButton == 2 || !Ae2fcFluidPatternHelper.isAe2fcFluidDrop(aeStack)) {
            return false;
        }
        try {
            ItemStack held = gui.mc.player.inventory.getItemStack();
            if (!held.isEmpty() && mouseButton == 0
                    && held.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                FluidStack fluid = readFluid(aeStack);
                sendAction(0, fluid != null ? fluid.writeToNBT(new NBTTagCompound()) : new NBTTagCompound());
                return true;
            }
            if (aeStack.isCraftable() && mouseButton == 0
                    && (aeStack.getStackSize() == 0L || GuiScreen.isCtrlKeyDown())) {
                return false;
            }
            NBTTagCompound definition = aeStack.getDefinition().writeToNBT(new NBTTagCompound());
            definition.setBoolean("shift", GuiScreen.isShiftKeyDown());
            sendAction(2, definition);
            return true;
        } catch (Exception exception) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to forward terminal click to ae2fc", exception);
            return true;
        }
    }

    private static FluidStack readFluid(IAEItemStack stack) throws ReflectiveOperationException {
        return (FluidStack) RuntimeIntegrationRegistry.invokeStatic(FAKE_REGISTER_CLASS, "getStack",
                new Class<?>[]{ItemStack.class}, stack.createItemStack());
    }

    private static void sendAction(int type, NBTTagCompound data) throws ReflectiveOperationException {
        Class<?> actionType = RuntimeIntegrationRegistry.classForName(ACTION_CLASS);
        IMessage action = (IMessage) actionType.getConstructor(byte.class, NBTTagCompound.class)
                .newInstance((byte) type, data);
        Class<?> fluidCraft = RuntimeIntegrationRegistry.classForName(FLUID_CRAFT_CLASS);
        Object proxy = fluidCraft.getField("proxy").get(null);
        Object network = proxy.getClass().getField("netHandler").get(proxy);
        RuntimeIntegrationRegistry.method(network.getClass(), "sendToServer", IMessage.class)
                .invoke(network, action);
    }
}
