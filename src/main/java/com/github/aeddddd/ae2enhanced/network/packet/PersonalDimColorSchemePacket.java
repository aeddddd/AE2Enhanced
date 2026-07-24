package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;

/**
 * C→S：提交地板颜色方案修改(管理器预设页),可选同时重铺已生成地板.
 */
public class PersonalDimColorSchemePacket {

    private final BlockPos pos;
    private final UUID owner;
    private final int roadColor;
    private final int lineColor;
    private final int platformColor;
    private final boolean recarpet;

    public PersonalDimColorSchemePacket(BlockPos pos, UUID owner, int roadColor, int lineColor, int platformColor,
            boolean recarpet) {
        this.pos = pos;
        this.owner = owner;
        this.roadColor = roadColor;
        this.lineColor = lineColor;
        this.platformColor = platformColor;
        this.recarpet = recarpet;
    }

    public static void encode(PersonalDimColorSchemePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUUID(packet.owner);
        buffer.writeVarInt(packet.roadColor);
        buffer.writeVarInt(packet.lineColor);
        buffer.writeVarInt(packet.platformColor);
        buffer.writeBoolean(packet.recarpet);
    }

    public static PersonalDimColorSchemePacket decode(FriendlyByteBuf buffer) {
        return new PersonalDimColorSchemePacket(buffer.readBlockPos(), buffer.readUUID(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(PersonalDimColorSchemePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PersonalDimensionManager.canManage(player, packet.owner)) {
                return;
            }
            // 服务端校验:染料色 id 必须在合法范围内
            if (!isValidColor(packet.roadColor) || !isValidColor(packet.lineColor)
                    || !isValidColor(packet.platformColor)) {
                return;
            }
            FloorColorScheme scheme = FloorColorScheme.ofConcrete(
                    DyeColor.byId(packet.roadColor), DyeColor.byId(packet.lineColor),
                    DyeColor.byId(packet.platformColor));
            PersonalDimensionManager.setColorScheme(player.server, packet.owner, scheme);
            if (packet.recarpet) {
                PersonalDimensionManager.recarpetFloor(player.server, packet.owner);
            }
            // 回发最新状态
            ModNetwork.CHANNEL.sendTo(
                    PersonalDimManagerStatePacket.create(player.server, packet.pos, packet.owner),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        });
        context.setPacketHandled(true);
    }

    private static boolean isValidColor(int id) {
        return id >= 0 && id < 16;
    }
}
