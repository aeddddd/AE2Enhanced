package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RF 能量在终端中的无 TII fallback 处理器.
 */
public class EnergyTerminalHandler implements IResourceTerminalHandler {

    private static final long DEFAULT_EXTRACT_AMOUNT = 1_000_000L;

    @Override
    public String getName() {
        return "energy";
    }

    @Override
    public boolean isResourceStack(IAEItemStack aeStack) {
        return aeStack != null && aeStack.getItem() == ItemRegistry.ENERGY_DROP;
    }

    @Override
    public boolean isPacketItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ItemRegistry.ENERGY_DROP;
    }

    @Override
    public boolean isContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.hasCapability(CapabilityEnergy.ENERGY, null);
    }

    @Override
    public boolean handleClick(ResourceClickContext ctx) {
        ItemStack held = ctx.player.inventory.getItemStack();

        // 手持能量容器：填充或排空网络能量
        if (isContainer(held)) {
            // 目标槽位是否为能量资源决定是填充还是排空
            boolean targetEnergy = ctx.slot.getAEStack() != null && isResourceStack(ctx.slot.getAEStack());
            NBTTagCompound extra = new NBTTagCompound();
            extra.setBoolean("TargetEnergy", targetEnergy);
            UnifiedResourceTerminalBridge.sendResourceAction(getName(), UnifiedResourceTerminalBridge.ACTION_WORK, 0, extra);
            return true;
        }

        // 手持能量数据包物品：存入网络
        if (isPacketItem(held)) {
            long amount = ItemEnergyDrop.getAmount(held);
            UnifiedResourceTerminalBridge.sendResourceAction(getName(), UnifiedResourceTerminalBridge.ACTION_DEPOSIT, amount, null);
            return true;
        }

        // 空手持点击能量槽位：从网络提取数据包物品
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
        tooltip.add(TextFormatting.GOLD + I18n.format("tooltip.ae2enhanced.energy") + TextFormatting.RESET);
        tooltip.add(formatAmount(aeStack.getStackSize()));
        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }
        return tooltip;
    }

    @Override
    public List<String> getContainerTooltip(ItemStack container) {
        IEnergyStorage cap = container.getCapability(CapabilityEnergy.ENERGY, null);
        if (cap == null) return null;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.GOLD + I18n.format("tooltip.ae2enhanced.energy.container",
                NumberFormat.getNumberInstance(Locale.US).format(cap.getEnergyStored()),
                NumberFormat.getNumberInstance(Locale.US).format(cap.getMaxEnergyStored())));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format("tooltip.ae2enhanced.left_click_network"));
        return tooltip;
    }

    static String formatAmount(long amount) {
        return TextFormatting.DARK_GRAY + I18n.format("tooltip.ae2enhanced.stored")
                + " : " + NumberFormat.getNumberInstance(Locale.US).format(amount) + " FE";
    }
}
