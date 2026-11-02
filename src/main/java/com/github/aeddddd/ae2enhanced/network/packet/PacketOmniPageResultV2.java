package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** V2 分页响应；每个物品独立编码，单条大 NBT 不会破坏后续条目。 */
public final class PacketOmniPageResultV2 implements IMessage {
    public static final int MAX_ENTRY_BYTES = 256 * 1024;
    private long containerSessionId;
    private long queryGeneration;
    private long requestId;
    private int totalCount;
    private int offset;
    private List<Entry> entries = Collections.emptyList();
    public PacketOmniPageResultV2() {
    }

    public PacketOmniPageResultV2(long containerSessionId, long queryGeneration, long requestId,
            int totalCount, int offset, List<Entry> entries) {
        this.containerSessionId = containerSessionId;
        this.queryGeneration = queryGeneration;
        this.requestId = requestId;
        this.totalCount = totalCount;
        this.offset = offset;
        this.entries = entries == null ? Collections.emptyList() : entries;
    }

    private static byte[] encodeEntry(IAEItemStack stack) {
        if (stack == null) return new byte[0];
        byte[] encoded = tryEncode(stack);
        if (encoded != null && encoded.length <= MAX_ENTRY_BYTES) return encoded;
        IAEItemStack degraded = withoutNbt(stack);
        encoded = degraded == null ? null : tryEncode(degraded);
        return encoded != null && encoded.length <= MAX_ENTRY_BYTES ? encoded : new byte[0];
    }

    private static byte[] tryEncode(IAEItemStack stack) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            stack.writeToPacket(buffer);
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } catch (Exception | LinkageError ex) {
            return null;
        } finally {
            buffer.release();
        }
    }

    private static IAEItemStack withoutNbt(IAEItemStack original) {
        try {
            ItemStack definition = original.getDefinition().copy();
            definition.setCount(1);
            definition.setTagCompound(null);
            IAEItemStack result = AEItemStack.fromItemStack(definition);
            if (result != null) {
                result.setStackSize(original.getStackSize());
                result.setCraftable(original.isCraftable());
            }
            return result;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        containerSessionId = buf.readLong();
        queryGeneration = buf.readLong();
        requestId = buf.readLong();
        totalCount = buf.readInt();
        offset = buf.readInt();
        int count = buf.readUnsignedShort();
        if (count == 0) {
            entries = Collections.emptyList();
            return;
        }
        entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = ByteBufUtils.readVarInt(buf, 4);
            int length = ByteBufUtils.readVarInt(buf, 5);
            IAEItemStack stack = null;
            if (length > 0 && length <= MAX_ENTRY_BYTES && length <= buf.readableBytes()) {
                ByteBuf payload = buf.readSlice(length);
                try {
                    stack = AEItemStack.fromPacket(payload);
                } catch (RuntimeException ex) {
                    com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                            "[AE2E] Ignoring malformed V2 terminal entry {}", id, ex);
                }
            } else if (length > 0 && length <= buf.readableBytes()) {
                buf.skipBytes(length);
            }
            long amount = buf.readLong();
            entries.add(new Entry(id, stack, amount));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(containerSessionId);
        buf.writeLong(queryGeneration);
        buf.writeLong(requestId);
        buf.writeInt(totalCount);
        buf.writeInt(offset);
        buf.writeShort(entries.size());
        for (Entry entry : entries) {
            ByteBufUtils.writeVarInt(buf, entry.id, 4);
            byte[] payload = encodeEntry(entry.stack);
            ByteBufUtils.writeVarInt(buf, payload.length, 5);
            buf.writeBytes(payload);
            buf.writeLong(entry.count);
        }
    }

    public long getContainerSessionId() { return containerSessionId; }

    public long getQueryGeneration() { return queryGeneration; }

    public long getRequestId() { return requestId; }

    public int getTotalCount() { return totalCount; }

    public int getOffset() { return offset; }

    public List<Entry> getEntries() { return entries; }

    public static final class Entry {
        public final int id;
        public final IAEItemStack stack;
        public final long count;

        public Entry(int id, IAEItemStack stack, long count) {
            this.id = id;
            this.stack = stack;
            this.count = count;
        }
    }

    public static final class Handler implements IMessageHandler<PacketOmniPageResultV2, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniPageResultV2 message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                    com.github.aeddddd.ae2enhanced.client.me.TerminalQueryClient.dispatch(message));
            return null;
        }
    }
}
