package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableAction;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

/**
 * 非物品资源终端交互的统一桥接入口.
 * <p>
 * 无 TII 时，所有 RF/Mana/Starlight 的终端点击、tooltip、容器交互都通过本类分发到
 * 对应的 {@link IResourceTerminalHandler}。
 * </p>
 */
public final class UnifiedResourceTerminalBridge {

    public static final String ACTION_EXTRACT = "extract";
    public static final String ACTION_DEPOSIT = "deposit";
    public static final String ACTION_WORK = "work";

    private UnifiedResourceTerminalBridge() {}

    /**
     * 客户端点击入口.
     *
     * @return true 表示已处理
     */
    public static boolean onHandleMouseClick(appeng.client.gui.AEBaseGui gui, Slot slot,
                                             int slotId, int mouseButton,
                                             net.minecraft.inventory.ClickType clickType) {
        if (!(slot instanceof appeng.client.me.SlotME)) {
            return false;
        }
        appeng.client.me.SlotME s = (appeng.client.me.SlotME) slot;
        ResourceClickContext ctx = new ResourceClickContext(gui, s, slotId, mouseButton, clickType);

        // 优先让槽位资源处理器处理（空手持资源槽点击、手持数据包物品等）
        IAEItemStack aeStack = s.getAEStack();
        if (aeStack != null) {
            IResourceTerminalHandler handler = ResourceTerminalHandlerRegistry.findForStack(aeStack);
            if (handler != null && handler.handleClick(ctx)) {
                return true;
            }
        }

        // 其次按手持物品处理（容器填充/排空、数据包物品存入）
        ItemStack held = gui.mc.player.inventory.getItemStack();
        if (!held.isEmpty()) {
            IResourceTerminalHandler handler = ResourceTerminalHandlerRegistry.findForHeldItem(held);
            if (handler != null && handler.handleClick(ctx)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 客户端 tooltip 入口.
     *
     * @return true 表示已处理
     */
    public static boolean onRenderHoveredToolTip(GuiContainer gui, Slot slot, int mouseX, int mouseY) {
        if (!(slot instanceof appeng.client.me.SlotME)) {
            return false;
        }
        appeng.client.me.SlotME s = (appeng.client.me.SlotME) slot;

        ItemStack held = gui.mc.player.inventory.getItemStack();
        if (!held.isEmpty()) {
            IResourceTerminalHandler handler = ResourceTerminalHandlerRegistry.findForHeldItem(held);
            if (handler != null) {
                List<String> tooltip = handler.getContainerTooltip(held);
                if (tooltip != null && !tooltip.isEmpty()) {
                    gui.drawHoveringText(tooltip, mouseX, mouseY);
                    return true;
                }
            }
        }

        IAEItemStack aeStack = s.getAEStack();
        if (aeStack != null) {
            IResourceTerminalHandler handler = ResourceTerminalHandlerRegistry.findForStack(aeStack);
            if (handler != null) {
                List<String> tooltip = handler.getTooltip(aeStack);
                if (tooltip != null && !tooltip.isEmpty()) {
                    gui.drawHoveringText(tooltip, mouseX, mouseY);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 发送统一资源动作包到服务端.
     */
    public static void sendResourceAction(String resource, String action, long amount, NBTTagCompound extra) {
        NBTTagCompound nbt = extra != null ? extra.copy() : new NBTTagCompound();
        nbt.setString("Resource", resource);
        nbt.setString("Action", action);
        nbt.setLong("Amount", amount);
        AE2Enhanced.network.sendToServer(new PacketMEMonitorableAction(PacketMEMonitorableAction.RESOURCE_ACTION, nbt));
    }
}
