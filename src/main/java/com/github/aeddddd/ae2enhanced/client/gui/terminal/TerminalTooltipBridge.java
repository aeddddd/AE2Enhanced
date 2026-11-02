package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.SlotME;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.item.fakeitem.EssentiaFakeItemChecks;
import com.github.aeddddd.ae2enhanced.item.fakeitem.FakeItemRegister;
import com.github.aeddddd.ae2enhanced.item.fakeitem.GasFakeItemChecks;
import com.github.aeddddd.ae2enhanced.item.fakeitem.FakeEssentiaSafe;
import com.github.aeddddd.ae2enhanced.terminal.UnifiedResourceTerminalBridge;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 终端 Tooltip 桥接类 —— 处理流体/气体/源质假物品的 tooltip 渲染.
 */
public final class TerminalTooltipBridge {

    private TerminalTooltipBridge() {}

    public static boolean onRenderSlotTooltip(GuiContainer gui, Slot slot, int mouseX, int mouseY) {
        if (slot instanceof SlotME) {
            if (Ae2fcCompat.AE2FC_LOADED && !(gui instanceof IOmniTerminalGui)) {
                return false;
            }
            return onRenderHoveredToolTip(gui, slot, mouseX, mouseY);
        }
        if (slot == null || !slot.getHasStack()) {
            return false;
        }
        ItemStack stack = slot.getStack();
        String tooltip = null;
        if (!Ae2fcCompat.AE2FC_LOADED && ItemFluidDrop.isFluidDrop(stack)) {
            FluidStack fluid = ItemFluidDrop.getFluidStack(stack);
            if (fluid != null) {
                tooltip = fluid.getLocalizedName();
            }
        } else if (!Ae2fcCompat.AE2FC_LOADED && GasFakeItemChecks.isGasFakeItem(stack)) {
            tooltip = GasFakeItemChecks.tryGetGasName(stack);
        } else if (FakeEssentiaSafe.isEssentiaFakeItem(stack)) {
            String aspectTag = FakeEssentiaSafe.tryGetAspectTag(stack);
            if (aspectTag != null) {
                tooltip = "Essentia: " + aspectTag;
            }
        }
        if (tooltip == null) {
            return false;
        }
        gui.drawHoveringText(java.util.Collections.singletonList(tooltip), mouseX, mouseY);
        return true;
    }

    /**
     * 在 AE2 终端 SlotME 上渲染完整的资源提示.
     *
     * @return true 表示已处理 tooltip,Mixin 应 cancel 原方法
     */
    public static boolean onRenderHoveredToolTip(GuiContainer gui, Slot slot, int mouseX, int mouseY) {
        if (!(slot instanceof SlotME) || !((SlotME) slot).isEnabled()) {
            return false;
        }

        ItemStack mouseItem = gui.mc.player.inventory.getItemStack();
        if (!mouseItem.isEmpty()) {
            if (UnifiedResourceTerminalBridge.onRenderHoveredToolTip(gui, slot, mouseX, mouseY)) {
                return true;
            }
            return renderContainerToolTip(gui, slot, mouseX, mouseY);
        }

        IAEItemStack aeStack = ((SlotME) slot).getAEStack();
        if (aeStack == null) {
            return false;
        }

        if (!Ae2fcCompat.AE2FC_LOADED) {
            if (aeStack.getItem() == ItemRegistry.FLUID_DROP) {
                return renderFluidToolTip(gui, aeStack, mouseX, mouseY);
            }
            if (ItemRegistry.GAS_DROP != null && aeStack.getItem() == ItemRegistry.GAS_DROP) {
                return renderGasToolTip(gui, aeStack, mouseX, mouseY);
            }
        }
        if (ItemRegistry.ESSENTIA_DROP != null && aeStack.getItem() == ItemRegistry.ESSENTIA_DROP) {
            return renderEssentiaToolTip(gui, aeStack, mouseX, mouseY);
        }

        if (UnifiedResourceTerminalBridge.onRenderHoveredToolTip(gui, slot, mouseX, mouseY)) {
            return true;
        }

        return false;
    }

    /* ========================= Fluid ========================= */

    private static boolean renderFluidToolTip(GuiContainer gui, IAEItemStack aeStack, int mouseX, int mouseY) {
        IAEFluidStack fluidStack = FakeItemRegister.getAEStack(aeStack.copy().setStackSize(1));
        if (fluidStack == null) return false;
        fluidStack.setStackSize(aeStack.getStackSize());

        List<String> tooltip = new ArrayList<>();
        FluidStack fs = fluidStack.getFluidStack();
        tooltip.add(fs != null ? fs.getLocalizedName() : "Unknown Fluid");

        String modName = getFluidModName(fs);
        if (!modName.isEmpty()) {
            tooltip.add(TextFormatting.BLUE.toString() + TextFormatting.ITALIC + modName);
        }

        boolean shift = GuiScreen.isShiftKeyDown();
        long amount = fluidStack.getStackSize();
        String formattedAmount = shift
                ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " mB"
                : NumberFormat.getNumberInstance(Locale.US).format((double) amount / 1000.0) + " B";
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("gui.appliedenergistics2.StoredFluids") + " : " + formattedAmount);

        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }

        gui.drawHoveringText(tooltip, mouseX, mouseY);
        return true;
    }

    /* ========================= Gas ========================= */

    private static boolean renderGasToolTip(GuiContainer gui, IAEItemStack aeStack, int mouseX, int mouseY) {
        if (ItemRegistry.GAS_DROP == null || aeStack.getItem() != ItemRegistry.GAS_DROP) return false;

        try {
            Object gasStack = FakeItemRegister.getAEStack(aeStack.copy().setStackSize(1));
            if (gasStack == null) return false;
            java.lang.reflect.Method setStackSize = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                    .method(gasStack.getClass(), "setStackSize", long.class);
            setStackSize.invoke(gasStack, aeStack.getStackSize());

            java.lang.reflect.Method getGas = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                    .method(gasStack.getClass(), "getGas");
            Object gas = getGas.invoke(gasStack);
            if (gas == null) return false;
            java.lang.reflect.Method getLocalizedName = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                    .method(gas.getClass(), "getLocalizedName");
            String gasName = (String) getLocalizedName.invoke(gas);

            List<String> tooltip = new ArrayList<>();
            tooltip.add(gasName);
            tooltip.add(TextFormatting.BLUE.toString() + TextFormatting.ITALIC + "Mekanism");

            boolean shift = GuiScreen.isShiftKeyDown();
            java.lang.reflect.Method getStackSize = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                    .method(gasStack.getClass(), "getStackSize");
            long amount = (long) getStackSize.invoke(gasStack);
            String formattedAmount = shift
                    ? NumberFormat.getNumberInstance(Locale.US).format(amount) + " mB"
                    : NumberFormat.getNumberInstance(Locale.US).format((double) amount / 1000.0) + " B";
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.stored") + " : " + formattedAmount);

            if (aeStack.isCraftable()) {
                tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
            }

            gui.drawHoveringText(tooltip, mouseX, mouseY);
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to render gas tooltip", e);
            return false;
        }
    }

    /* ========================= Essentia ========================= */

    private static boolean renderEssentiaToolTip(GuiContainer gui, IAEItemStack aeStack, int mouseX, int mouseY) {
        if (ItemRegistry.ESSENTIA_DROP == null || aeStack.getItem() != ItemRegistry.ESSENTIA_DROP) {
            return false;
        }
        String aspectTag = EssentiaFakeItemChecks.tryGetAspectTag(aeStack.createItemStack());
        if (aspectTag == null) return false;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(formatAspectName(aspectTag));
        tooltip.add(TextFormatting.BLUE.toString() + TextFormatting.ITALIC + "Thaumcraft");
        long amount = aeStack.getStackSize();
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.ae2enhanced.stored")
                + " : " + NumberFormat.getNumberInstance(Locale.US).format(amount));
        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }
        gui.drawHoveringText(tooltip, mouseX, mouseY);
        return true;
    }

    /**
     * 获取 aspect 的本地化名称(使用 Thaumcraft 自带的 tc.aspect.* 语言键).
     */
    private static String formatAspectName(String aspectTag) {
        String key = "tc.aspect." + aspectTag;
        String name = I18n.format(key);
        return key.equals(name) ? aspectTag : name;
    }

    /* ========================= Container (held item) ========================= */

    private static boolean renderContainerToolTip(GuiContainer gui, Slot slot, int mouseX, int mouseY) {
        ItemStack mouseItem = gui.mc.player.inventory.getItemStack();
        if (mouseItem.isEmpty()) return false;

        if (!Ae2fcCompat.AE2FC_LOADED) {
            FluidStack fluidInContainer = getFluidFromItem(mouseItem);
            if (fluidInContainer != null) {
                renderInjectTooltip(gui, mouseX, mouseY, fluidInContainer.getLocalizedName(), mouseItem.getDisplayName());
                return true;
            }

            try {
                Class<?> iGasItemClass = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                        .classForName("mekanism.api.gas.IGasItem");
                if (iGasItemClass.isInstance(mouseItem.getItem())) {
                    Object gas = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                            .method(iGasItemClass, "getGas", ItemStack.class).invoke(mouseItem.getItem(), mouseItem);
                    if (gas != null) {
                        int amount = (int) gas.getClass().getField("amount").get(gas);
                        if (amount > 0) {
                            Object gasObj = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                                    .method(gas.getClass(), "getGas").invoke(gas);
                            String gasName = (String) com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                                    .method(gasObj.getClass(), "getLocalizedName").invoke(gasObj);
                            renderInjectTooltip(gui, mouseX, mouseY, gasName, mouseItem.getDisplayName());
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to get gas from container in tooltip", e);
            }
        }

        // 源质容器(安瓿/源质罐)提示:ae2fc 不处理源质,不受 AE2FC_LOADED 影响
        if (ItemRegistry.ESSENTIA_DROP != null
                && EssentiaFakeItemChecks.getEssentiaContainerCapacity(mouseItem) > 0) {
            String contained = EssentiaFakeItemChecks.tryGetContainerAspectTag(mouseItem);
            if (contained != null) {
                // 容器内有源质 → 提示可倾倒回网络
                renderInjectTooltip(gui, mouseX, mouseY, formatAspectName(contained), mouseItem.getDisplayName());
                return true;
            }
            // 空容器悬停源质条目 → 提示可装填
            IAEItemStack aeStack = ((SlotME) slot).getAEStack();
            if (aeStack != null && aeStack.getItem() == ItemRegistry.ESSENTIA_DROP) {
                String aspectTag = EssentiaFakeItemChecks.tryGetAspectTag(aeStack.createItemStack());
                if (aspectTag != null) {
                    String s = " : " + I18n.format("tooltip.ae2enhanced.essentia.fill", formatAspectName(aspectTag))
                            + TextFormatting.RESET;
                    List<String> tooltip = new ArrayList<>();
                    tooltip.add(TextFormatting.DARK_GRAY
                            + net.minecraft.client.settings.GameSettings.getKeyDisplayString(-100) + s);
                    gui.drawHoveringText(tooltip, mouseX, mouseY);
                    return true;
                }
            }
        }

        return false;
    }

    private static void renderInjectTooltip(GuiContainer gui, int mouseX, int mouseY,
                                             String contentName, String itemName) {
        String injectKey = "gui.appliedenergistics2.security.inject.name";
        String s = " : " + I18n.format(injectKey) + " " + TextFormatting.RESET;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_GRAY + net.minecraft.client.settings.GameSettings.getKeyDisplayString(-100) + s + contentName);
        tooltip.add(TextFormatting.DARK_GRAY + net.minecraft.client.settings.GameSettings.getKeyDisplayString(-99) + s + itemName);
        gui.drawHoveringText(tooltip, mouseX, mouseY);
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

    private static String getFluidModName(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) return "";
        String defaultName = net.minecraftforge.fluids.FluidRegistry.getDefaultFluidName(fluidStack.getFluid());
        if (defaultName == null) return "";
        net.minecraft.util.ResourceLocation rl = new net.minecraft.util.ResourceLocation(defaultName);
        String modid = rl.getNamespace();
        net.minecraftforge.fml.common.ModContainer mod = net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(modid);
        return mod != null ? mod.getName() : modid;
    }
}
