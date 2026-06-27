package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Astral Sorcery Starlight 在终端中的无 TII fallback 处理器.
 * <p>
 * Starlight 目前无容器交互，仅提供 tooltip 与数据包物品提取/存入。
 * </p>
 */
public class StarlightTerminalHandler implements IResourceTerminalHandler {

    private static final long DEFAULT_EXTRACT_AMOUNT = 1_000L;

    @Override
    public String getName() {
        return "starlight";
    }

    @Override
    public boolean isResourceStack(IAEItemStack aeStack) {
        return aeStack != null && aeStack.getItem() == ItemRegistry.STARLIGHT_DROP;
    }

    @Override
    public boolean isPacketItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ItemRegistry.STARLIGHT_DROP;
    }

    @Override
    public boolean isContainer(ItemStack stack) {
        return false;
    }

    @Override
    public boolean handleClick(ResourceClickContext ctx) {
        if (!Loader.isModLoaded("astralsorcery")) {
            return false;
        }
        ItemStack held = ctx.player.inventory.getItemStack();

        if (isPacketItem(held)) {
            long amount = ItemStarlightDrop.getAmount(held);
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
        tooltip.add(TextFormatting.BLUE + I18n.format("tooltip.ae2enhanced.starlight") + TextFormatting.RESET);
        tooltip.add(formatAmount(aeStack.getStackSize()));
        if (aeStack.isCraftable()) {
            tooltip.add(TextFormatting.GRAY + I18n.format("gui.tooltips.appliedenergistics2.ItemsCraftable"));
        }
        return tooltip;
    }

    @Override
    public List<String> getContainerTooltip(ItemStack container) {
        return null;
    }

    static String formatAmount(long amount) {
        return TextFormatting.DARK_GRAY + I18n.format("tooltip.ae2enhanced.stored")
                + " : " + NumberFormat.getNumberInstance(Locale.US).format(amount) + " Starlight";
    }
}
