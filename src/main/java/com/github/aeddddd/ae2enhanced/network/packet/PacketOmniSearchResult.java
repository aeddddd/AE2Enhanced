package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端 → 客户端：Omni Terminal 搜索结果
 *
 * <p>包含匹配的 (id, stack, count) 列表，客户端直接替换 renderView 显示。
 */
public class PacketOmniSearchResult implements IMessage {

    public static class Entry {
        public int id;
        public IAEItemStack stack;
        public long count;

        public Entry() {
        }

        public Entry(int id, IAEItemStack stack, long count) {
            this.id = id;
            this.stack = stack;
            this.count = count;
        }
    }

    private List<Entry> entries = new ArrayList<>();
    private boolean isFullResult = true;

    public PacketOmniSearchResult() {
    }

    public PacketOmniSearchResult(List<Entry> entries) {
        this.entries = entries != null ? entries : Collections.emptyList();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readInt();
            long c = buf.readLong();
            boolean hasStack = buf.readBoolean();
            IAEItemStack stack = null;
            if (hasStack) {
                try {
                    stack = AEItemStack.fromPacket(buf);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to read stack from OmniSearchResult", e);
                }
            }
            this.entries.add(new Entry(id, stack, c));
        }
        this.isFullResult = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entries.size());
        for (Entry e : this.entries) {
            buf.writeInt(e.id);
            buf.writeLong(e.count);
            boolean hasStack = (e.stack != null);
            buf.writeBoolean(hasStack);
            if (hasStack) {
                try {
                    e.stack.writeToPacket(buf);
                } catch (Exception ex) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to write stack to OmniSearchResult", ex);
                }
            }
        }
        buf.writeBoolean(this.isFullResult);
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public boolean isFullResult() {
        return this.isFullResult;
    }

    public void setFullResult(boolean fullResult) {
        this.isFullResult = fullResult;
    }

    public static class Handler implements IMessageHandler<PacketOmniSearchResult, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniSearchResult message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen gui = Minecraft.getMinecraft().currentScreen;
                if (gui instanceof GuiOmniTerm) {
                    ((GuiOmniTerm) gui).handleOmniSearchResult(message.getEntries());
                }
            });
            return null;
        }
    }
}
