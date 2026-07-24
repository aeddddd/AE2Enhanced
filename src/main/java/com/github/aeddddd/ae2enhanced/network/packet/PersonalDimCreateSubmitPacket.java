package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;

/**
 * C→S：创建向导确认提交,按所选颜色方案创建个人维度并进入.
 */
public class PersonalDimCreateSubmitPacket {

    private final int roadColor;
    private final int lineColor;
    private final int platformColor;

    public PersonalDimCreateSubmitPacket(int roadColor, int lineColor, int platformColor) {
        this.roadColor = roadColor;
        this.lineColor = lineColor;
        this.platformColor = platformColor;
    }

    public static void encode(PersonalDimCreateSubmitPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.roadColor);
        buffer.writeVarInt(packet.lineColor);
        buffer.writeVarInt(packet.platformColor);
    }

    public static PersonalDimCreateSubmitPacket decode(FriendlyByteBuf buffer) {
        return new PersonalDimCreateSubmitPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(PersonalDimCreateSubmitPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 已创建过维度的玩家不允许重复创建
            PlayerDimEntry entry = PersonalDimensionManager.getEntry(player.server, player.getUUID());
            if (entry != null && entry.created) {
                return;
            }
            if (!isValidColor(packet.roadColor) || !isValidColor(packet.lineColor)
                    || !isValidColor(packet.platformColor)) {
                return;
            }
            PersonalDimensionManager.setColorScheme(player.server, player.getUUID(),
                    FloorColorScheme.ofConcrete(DyeColor.byId(packet.roadColor),
                            DyeColor.byId(packet.lineColor), DyeColor.byId(packet.platformColor)));
            // 记录返回点后再创建并进入
            PersonalDimensionManager.setReturnPoint(player);
            ServerLevel level = PersonalDimensionManager.getOrCreateDimension(player);
            if (level == null) {
                return;
            }
            PersonalDimensionManager.teleportToDimension(player,
                    PersonalDimensionManager.dimensionKeyFor(player.getUUID()));
            player.closeContainer();
        });
        context.setPacketHandled(true);
    }

    private static boolean isValidColor(int id) {
        return id >= 0 && id < 16;
    }
}
