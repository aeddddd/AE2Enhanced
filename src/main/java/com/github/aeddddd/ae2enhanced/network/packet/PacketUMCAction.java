package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 通用内存卡动作包：复制、粘贴、选取、GUI 按钮操作.
 */
public class PacketUMCAction implements IMessage {

    public enum ActionType {
        COPY, PASTE, SELECT,
        CLEAR_CONFIG, CLEAR_SELECTIONS, REMOVE_SELECTION,
        OPEN_GUI,
        BIND_SOURCE, BIND_TARGET, CLEAR_BINDINGS, BIND_RECYCLER, CLEAR_RECYCLER_BINDINGS
    }

    private ActionType type;
    private long pos;       // BlockPos.toLong()
    private byte face;      // EnumFacing.index
    private int index;      // for REMOVE_SELECTION

    public PacketUMCAction() {
    }

    public PacketUMCAction(ActionType type, BlockPos pos, EnumFacing face) {
        this.type = type;
        this.pos = pos != null ? pos.toLong() : 0;
        this.face = (byte) (face != null ? face.getIndex() : 0);
        this.index = -1;
    }

    public PacketUMCAction(ActionType type, int index) {
        this.type = type;
        this.pos = 0;
        this.face = 0;
        this.index = index;
    }

    public PacketUMCAction(ActionType type) {
        this.type = type;
        this.pos = 0;
        this.face = 0;
        this.index = -1;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int typeOrdinal = buf.readByte();
        // 越界动作类型置 null,由 handler 判空丢弃,防止恶意字节导致数组越界崩服
        this.type = typeOrdinal >= 0 && typeOrdinal < ActionType.values().length
                ? ActionType.values()[typeOrdinal] : null;
        this.pos = buf.readLong();
        this.face = buf.readByte();
        this.index = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte((byte) type.ordinal());
        buf.writeLong(pos);
        buf.writeByte(face);
        buf.writeInt(index);
    }

    public ActionType getType() {
        return type;
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public EnumFacing getFace() {
        int idx = face & 0xFF;
        // 越界面值返回 null,由调用方判空跳过,防止数组越界
        return idx < EnumFacing.values().length ? EnumFacing.values()[idx] : null;
    }

    public int getIndex() {
        return index;
    }

    public static class Handler implements IMessageHandler<PacketUMCAction, IMessage> {

        @Override
        public IMessage onMessage(PacketUMCAction message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard.handleServerAction(
                        ctx.getServerHandler().player, message
                );
            });
            return null;
        }
    }
}
