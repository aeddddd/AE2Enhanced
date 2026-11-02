package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.clientapi.ClientPacketHandlers;
import com.github.aeddddd.ae2enhanced.dimension.DimensionOwner;
import com.github.aeddddd.ae2enhanced.dimension.DimensionPreference;
import com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionInfo;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** S→C 私域所有者、团队名、偏好与地板状态同步。 */
public class PacketPrivateDimensionInfo implements IMessage {
    private PrivateDimensionInfo info;

    public PacketPrivateDimensionInfo() {
    }

    public PacketPrivateDimensionInfo(PrivateDimensionInfo info) {
        this.info = info;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        DimensionOwner.Scope scope = DimensionOwner.Scope.values()[buf.readUnsignedByte()
                % DimensionOwner.Scope.values().length];
        DimensionPreference preference = DimensionPreference.values()[buf.readUnsignedByte()
                % DimensionPreference.values().length];
        boolean canManage = buf.readBoolean();
        long revision = buf.readLong();
        String owner = ByteBufUtils.readUTF8String(buf);
        String display = ByteBufUtils.readUTF8String(buf);
        String floor = ByteBufUtils.readUTF8String(buf);
        info = new PrivateDimensionInfo(scope, owner, display, preference, floor, revision, canManage);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(info.scope.ordinal());
        buf.writeByte(info.preference.ordinal());
        buf.writeBoolean(info.canManage);
        buf.writeLong(info.floorRevision);
        ByteBufUtils.writeUTF8String(buf, info.ownerId);
        ByteBufUtils.writeUTF8String(buf, info.displayName);
        ByteBufUtils.writeUTF8String(buf, info.floorStyleId);
    }

    public static class Handler implements IMessageHandler<PacketPrivateDimensionInfo, IMessage> {
        @Override
        public IMessage onMessage(PacketPrivateDimensionInfo message, MessageContext ctx) {
            ClientPacketHandlers.accept(ClientPacketHandlers.DIMENSION_INFO, message.info);
            return null;
        }
    }
}
