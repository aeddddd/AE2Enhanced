package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;

/**
 * C→S：提交个人维度规则修改.
 */
public class PersonalDimRulesSubmitPacket {

    private final BlockPos pos;
    private final UUID owner;
    private final PersonalDimensionRules rules;

    public PersonalDimRulesSubmitPacket(BlockPos pos, UUID owner, PersonalDimensionRules rules) {
        this.pos = pos;
        this.owner = owner;
        this.rules = rules;
    }

    public static void encode(PersonalDimRulesSubmitPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUUID(packet.owner);
        buffer.writeBoolean(packet.rules.disableMobSpawning);
        buffer.writeBoolean(packet.rules.lockWeather);
        buffer.writeBoolean(packet.rules.lockTime);
        buffer.writeBoolean(packet.rules.daylightCycle);
        buffer.writeLong(packet.rules.timeValue);
        buffer.writeBoolean(packet.rules.flightEnabled);
        buffer.writeFloat(packet.rules.movementSpeed);
        buffer.writeBoolean(packet.rules.noFlightInertia);
    }

    public static PersonalDimRulesSubmitPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID owner = buffer.readUUID();
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = buffer.readBoolean();
        rules.lockWeather = buffer.readBoolean();
        rules.lockTime = buffer.readBoolean();
        rules.daylightCycle = buffer.readBoolean();
        rules.timeValue = buffer.readLong();
        rules.flightEnabled = buffer.readBoolean();
        rules.movementSpeed = buffer.readFloat();
        rules.noFlightInertia = buffer.readBoolean();
        return new PersonalDimRulesSubmitPacket(pos, owner, rules);
    }

    public static void handle(PersonalDimRulesSubmitPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !PersonalDimensionManager.canManage(player, packet.owner)) {
                return;
            }
            // 服务端校验：时间归一化到 0~23999,速度钳制
            PersonalDimensionRules rules = packet.rules.copy();
            rules.timeValue = Math.floorMod(rules.timeValue, 24000L);
            rules.movementSpeed = PlayerAbilityApplier.clampMovementSpeed(rules.movementSpeed);
            PersonalDimensionManager.setRules(player.server, packet.owner, rules);
            // 回发最新状态
            ModNetwork.CHANNEL.sendTo(
                    PersonalDimManagerStatePacket.create(player.server, packet.pos, packet.owner),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        });
        context.setPacketHandled(true);
    }
}
