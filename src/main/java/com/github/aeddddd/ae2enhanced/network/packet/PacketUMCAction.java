package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;

/**
 * 通用内存卡动作包:复制、粘贴、选取、GUI 按钮操作、无线访问点绑定.
 */
public class PacketUMCAction implements ServerboundPacket {

    public enum ActionType {
        COPY, PASTE, SELECT,
        CLEAR_CONFIG, CLEAR_SELECTIONS, REMOVE_SELECTION,
        OPEN_GUI,
        BIND_ACCESS_POINT, CLEAR_BINDING
    }

    private final ActionType type;
    private final long pos; // BlockPos.asLong()
    private final byte face; // Direction.get3DDataValue()
    private final int index; // for REMOVE_SELECTION

    public PacketUMCAction(ActionType type, BlockPos pos, Direction face) {
        this.type = type;
        this.pos = pos != null ? pos.asLong() : 0;
        this.face = (byte) (face != null ? face.get3DDataValue() : 0);
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

    public static PacketUMCAction decode(FriendlyByteBuf buffer) {
        ActionType type = ActionType.values()[buffer.readByte()];
        long pos = buffer.readLong();
        byte face = buffer.readByte();
        int index = buffer.readInt();
        return new PacketUMCAction(type, BlockPos.of(pos), Direction.from3DDataValue(face & 0xFF), index);
    }

    private PacketUMCAction(ActionType type, BlockPos pos, Direction face, int index) {
        this.type = type;
        this.pos = pos.asLong();
        this.face = (byte) face.get3DDataValue();
        this.index = index;
    }

    public static void encode(PacketUMCAction packet, FriendlyByteBuf buffer) {
        buffer.writeByte((byte) packet.type.ordinal());
        buffer.writeLong(packet.pos);
        buffer.writeByte(packet.face);
        buffer.writeInt(packet.index);
    }

    public static void handle(PacketUMCAction packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                packet.handleOnServer(player);
            }
        });
        context.setPacketHandled(true);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        UniversalMemoryCardItem.handleServerAction(player, this);
    }

    public ActionType getType() {
        return type;
    }

    public BlockPos getPos() {
        return BlockPos.of(pos);
    }

    public Direction getFace() {
        return Direction.from3DDataValue(face & 0xFF);
    }

    public int getIndex() {
        return index;
    }
}
