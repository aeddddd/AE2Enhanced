package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.me.CraftingStatus;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：同步当前正在 Crafting CPU 中合成的物品列表及其进度
 */
public class PacketOmniCraftingUpdate implements IMessage {

    private List<CraftingStatus> activeCrafting = new ArrayList<>();

    public PacketOmniCraftingUpdate() {
    }

    public PacketOmniCraftingUpdate(List<CraftingStatus> activeCrafting) {
        this.activeCrafting = activeCrafting != null ? activeCrafting : new ArrayList<>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        this.activeCrafting = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            try {
                IAEItemStack stack = AEItemStack.fromPacket(buf);
                long remaining = buf.readLong();
                long start = buf.readLong();
                if (stack != null) {
                    this.activeCrafting.add(new CraftingStatus(stack, remaining, start));
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to read crafting update packet", e);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.activeCrafting.size());
        for (CraftingStatus status : this.activeCrafting) {
            try {
                status.output.writeToPacket(buf);
                buf.writeLong(status.remaining);
                buf.writeLong(status.start);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to write crafting update packet", e);
            }
        }
    }

    public List<CraftingStatus> getActiveCrafting() {
        return this.activeCrafting;
    }

    public static class Handler implements IMessageHandler<PacketOmniCraftingUpdate, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniCraftingUpdate message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof GuiContainer) {
                    GuiContainer gui = (GuiContainer) Minecraft.getMinecraft().currentScreen;
                    if (gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) {
                        com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm container =
                                (com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) gui.inventorySlots;
                        container.setClientActiveCrafting(message.getActiveCrafting());
                    }
                }
            });
            return null;
        }
    }
}
