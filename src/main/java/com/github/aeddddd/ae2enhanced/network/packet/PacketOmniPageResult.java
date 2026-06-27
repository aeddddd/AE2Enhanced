package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端 -> 客户端：Omni Terminal 分页结果。
 *
 * <p>R3 架构核心包。服务端完成排序/搜索/过滤后，只返回客户端请求范围内的物品。
 * 使用 per-session ID 压缩物品标识。
 */
public class PacketOmniPageResult implements IMessage {

    public static class Entry {
        public int id;              // per-session ID（用于压缩）
        public IAEItemStack stack;  // 物品模板（stackSize 可能为 0）
        public long count;          // 实际数量

        public Entry(int id, IAEItemStack stack, long count) {
            this.id = id;
            this.stack = stack;
            this.count = count;
        }
    }

    private int totalCount;
    private int offset;
    private List<Entry> entries = Collections.emptyList();

    public PacketOmniPageResult() {
    }

    public PacketOmniPageResult(int totalCount, int offset, List<Entry> entries) {
        this.totalCount = totalCount;
        this.offset = offset;
        this.entries = entries != null ? entries : Collections.emptyList();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.totalCount = buf.readInt();
        this.offset = buf.readInt();
        int count = buf.readUnsignedShort();
        if (count > 0) {
            this.entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int id = ByteBufUtils.readVarInt(buf, 4);
                IAEItemStack stack = null;
                try {
                    stack = AEItemStack.fromPacket(buf);
                } catch (Exception ex) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error("[AE2E] Failed to read stack from OmniPageResult", ex);
                }
                long cnt = buf.readLong();
                this.entries.add(new Entry(id, stack, cnt));
            }
        } else {
            this.entries = Collections.emptyList();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.totalCount);
        buf.writeInt(this.offset);
        buf.writeShort(this.entries.size());
        for (Entry e : this.entries) {
            ByteBufUtils.writeVarInt(buf, e.id, 4);
            try {
                e.stack.writeToPacket(buf);
            } catch (Exception ex) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error("[AE2E] Failed to write stack to OmniPageResult", ex);
            }
            buf.writeLong(e.count);
        }
    }

    public int getTotalCount() { return totalCount; }
    public int getOffset() { return offset; }
    public List<Entry> getEntries() { return entries; }

    public static class Handler implements IMessageHandler<PacketOmniPageResult, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniPageResult message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.client.gui.GuiScreen gui = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
                if (gui instanceof com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm) {
                    ((com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm) gui).handlePageResult(message);
                }
            });
            return null;
        }
    }
}
