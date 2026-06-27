package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemManaDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Botania Mana 在终端中的无 TII fallback 处理器.
 */
public class ManaTerminalHandler implements IResourceTerminalHandler {

    private static final long DEFAULT_EXTRACT_AMOUNT = 1_000_000L;

    private static final Class<?> IMANA_ITEM;
    private static final Method GET_MANA;
    private static final Method GET_MAX_MANA;

    static {
        Class<?> clazz = null;
        Method getMana = null;
        Method getMaxMana = null;
        if (Loader.isModLoaded("botania")) {
            try {
                clazz = Class.forName("vazkii.botania.api.mana.IManaItem");
                getMana = clazz.getMethod("getMana", ItemStack.class);
                getMaxMana = clazz.getMethod("getMaxMana", ItemStack.class);
            } catch (Throwable ignored) {
            }
        }
        IMANA_ITEM = clazz;
        GET_MANA = getMana;
        GET_MAX_MANA = getMaxMana;
    }

    @Override
    public String getName() {
        return "mana";
    }

    @Override
    public boolean isResourceStack(IAEItemStack aeStack) {
        return aeStack != null && aeStack.getItem() == ItemRegistry.MANA_DROP;
    }

    @Override
    public boolean isPacketItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ItemRegistry.MANA_DROP;
    }

    @Override
    public boolean isContainer(ItemStack stack) {
        return IMANA_ITEM != null && !stack.isEmpty() && IMANA_ITEM.isInstance(stack.getItem());
    }

    @Override
    public boolean handleClick(ResourceClickContext ctx) {
        ItemStack held = ctx.player.inventory.getItemStack();

        if (isContainer(held)) {
            boolean targetMana = ctx.slot.getAEStack() != null && isResourceStack(ctx.slot.getAEStack());
            NBTTagCompound extra = new NBTTagCompound();
            extra.setBoolean("TargetMana", targetMana);
            UnifiedResourceTerminalBridge.sendResourceAction(getName(), UnifiedResourceTerminalBridge.ACTION_WORK, 0, extra);
            return true;
        }

        if (isPacketItem(held)) {
            long amount = ItemManaDrop.getAmount(held);
            UnifiedResourceTerminalBridge.sendResourceAction(getName(), UnifiedResourceTerminalBridge.ACTION_DEPOSIT, amount, null);
            return true;
        }

        if (held.isEmpty() && ctx.mouseButton != 2 && ctx.slot.getAEStack() != null && isResourceStack(ctx.slot.getAEStack())) {
            long amount = ctx.clickType == ClickType.QUICK_MOVE
                    ? DEFAULT_EXTRACT_AMOUNT * 64
                    : DEFAULT_EXTRACT_AMOUNT;
            UnifiedResourceTerminalBridge.sendResourceAction(getName(), UnifiedResourceTerminalBridge.ACTION_EXTRACT, amount, null);
            return true;
        }

        return false;
    }

    @Override
    public List<String> getTooltip(IAEItemStack aeStack) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.ae2enhanced.mana") + TextFormatting.RESET);
        tooltip.add(formatAmount(aeStack.getStackSize()));
        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }
        return tooltip;
    }

    @Override
    public List<String> getContainerTooltip(ItemStack container) {
        if (GET_MANA == null || GET_MAX_MANA == null) return null;
        try {
            int stored = (int) GET_MANA.invoke(container.getItem(), container);
            int max = (int) GET_MAX_MANA.invoke(container.getItem(), container);
            List<String> tooltip = new ArrayList<>();
            tooltip.add(TextFormatting.AQUA + I18n.format("tooltip.ae2enhanced.mana.container",
                    NumberFormat.getNumberInstance(Locale.US).format(stored),
                    NumberFormat.getNumberInstance(Locale.US).format(max)));
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.ae2enhanced.left_click_network"));
            return tooltip;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String formatAmount(long amount) {
        return TextFormatting.DARK_GRAY + I18n.format("tooltip.ae2enhanced.stored")
                + " : " + NumberFormat.getNumberInstance(Locale.US).format(amount) + " Mana";
    }
}
