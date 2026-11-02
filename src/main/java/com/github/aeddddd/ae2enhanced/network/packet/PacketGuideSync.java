package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.clientapi.ClientPacketHandlers;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.io.IOException;
import java.util.Arrays;

/** S→C 脚本指南全量快照的一个有界分块。 */
public class PacketGuideSync implements IMessage {
    public static final int MAX_CHUNK_BYTES = 12 * 1024;
    private static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CHUNKS = (MAX_TOTAL_BYTES + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;

    private long revision;
    private int totalSize;
    private int chunkIndex;
    private int chunkCount;
    private byte[] payload = new byte[0];

    public PacketGuideSync() {
    }

    public PacketGuideSync(long revision, int totalSize, int chunkIndex, int chunkCount, byte[] payload) {
        this.revision = revision;
        this.totalSize = totalSize;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.payload = payload.clone();
    }

    public long getRevision() {
        return revision;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        revision = buf.readLong();
        totalSize = buf.readInt();
        chunkIndex = buf.readUnsignedShort();
        chunkCount = buf.readUnsignedShort();
        int length = buf.readUnsignedShort();
        if (totalSize < 0 || totalSize > MAX_TOTAL_BYTES
                || chunkCount <= 0 || chunkCount > MAX_CHUNKS
                || chunkIndex < 0 || chunkIndex >= chunkCount || length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Invalid script guide sync bounds");
        }
        payload = new byte[length];
        buf.readBytes(payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(revision);
        buf.writeInt(totalSize);
        buf.writeShort(chunkIndex);
        buf.writeShort(chunkCount);
        buf.writeShort(payload.length);
        buf.writeBytes(payload);
    }

    public static class Handler implements IMessageHandler<PacketGuideSync, IMessage> {
        @Override
        public IMessage onMessage(PacketGuideSync message, MessageContext ctx) {
            ClientPacketHandlers.accept(ClientPacketHandlers.GUIDE_SYNC, message);
            return null;
        }
    }
}
