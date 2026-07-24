package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;

/**
 * C→S：个人维度权限操作（邀请/踢出/设置权限）.
 */
public class PersonalDimPermissionPacket {

    public static final int ACTION_INVITE = 0;
    public static final int ACTION_KICK = 1;
    public static final int ACTION_SET_PERM = 2;

    private final BlockPos pos;
    private final UUID owner;
    private final int action;
    /**
     * ACTION_INVITE 时使用玩家名,其余使用 target UUID.
     */
    private final String targetName;
    private final UUID target;
    private final int permissionOrdinal;
    private final boolean value;

    private PersonalDimPermissionPacket(BlockPos pos, UUID owner, int action, String targetName, UUID target,
            int permissionOrdinal, boolean value) {
        this.pos = pos;
        this.owner = owner;
        this.action = action;
        this.targetName = targetName;
        this.target = target;
        this.permissionOrdinal = permissionOrdinal;
        this.value = value;
    }

    public static PersonalDimPermissionPacket invite(BlockPos pos, UUID owner, String targetName) {
        return new PersonalDimPermissionPacket(pos, owner, ACTION_INVITE, targetName, new UUID(0L, 0L), 0, false);
    }

    public static PersonalDimPermissionPacket kick(BlockPos pos, UUID owner, UUID target) {
        return new PersonalDimPermissionPacket(pos, owner, ACTION_KICK, "", target, 0, false);
    }

    public static PersonalDimPermissionPacket setPerm(BlockPos pos, UUID owner, UUID target,
            PersonalDimPermission permission, boolean value) {
        return new PersonalDimPermissionPacket(pos, owner, ACTION_SET_PERM, "", target,
                permission.ordinal(), value);
    }

    public static void encode(PersonalDimPermissionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUUID(packet.owner);
        buffer.writeByte(packet.action);
        buffer.writeUtf(packet.targetName);
        buffer.writeUUID(packet.target);
        buffer.writeByte(packet.permissionOrdinal);
        buffer.writeBoolean(packet.value);
    }

    public static PersonalDimPermissionPacket decode(FriendlyByteBuf buffer) {
        return new PersonalDimPermissionPacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readByte(),
                buffer.readUtf(), buffer.readUUID(), buffer.readByte(), buffer.readBoolean());
    }

    public static void handle(PersonalDimPermissionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PersonalDimensionManager.canManage(player, packet.owner)) {
                return;
            }
            switch (packet.action) {
                case ACTION_INVITE -> {
                    UUID targetId = resolvePlayer(player, packet.targetName);
                    if (targetId == null) {
                        player.sendSystemMessage(Component.translatable(
                                "chat.ae2enhanced.personal_dimension.player_not_found", packet.targetName));
                        return;
                    }
                    if (targetId.equals(packet.owner)) {
                        return;
                    }
                    PersonalDimensionManager.invitePlayer(player.server, packet.owner, targetId);
                }
                case ACTION_KICK -> PersonalDimensionManager.kickPlayer(player.server, packet.owner, packet.target);
                case ACTION_SET_PERM -> {
                    PersonalDimPermission[] values = PersonalDimPermission.values();
                    if (packet.permissionOrdinal >= 0 && packet.permissionOrdinal < values.length) {
                        PersonalDimensionManager.setPermission(player.server, packet.owner, packet.target,
                                values[packet.permissionOrdinal], packet.value);
                    }
                }
                default -> {
                }
            }
            // 回发最新状态
            ModNetwork.CHANNEL.sendTo(
                    PersonalDimManagerStatePacket.create(player.server, packet.pos, packet.owner),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        });
        context.setPacketHandled(true);
    }

    /**
     * 按名称解析玩家 UUID：优先在线玩家,其次名称缓存.
     */
    private static UUID resolvePlayer(ServerPlayer player, String name) {
        var online = player.server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        var cache = player.server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(name);
            if (profile.isPresent()) {
                return profile.map(GameProfile::getId).orElse(null);
            }
        }
        return null;
    }
}
