package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;

/**
 * S→C：同步玩家当前所在个人维度的规则；不在任何个人维度内时 {@code active=false}.
 *
 * <p>飞行移动为客户端权威,服务端的 deltaMovement 清零不会影响客户端,
 * 因此"无飞行惯性"规则必须同步到客户端执行.对齐 1.12 主分支的
 * {@code PacketPersonalDimensionRulesSync}.</p>
 */
public class PersonalDimRulesSyncPacket {

    private final boolean active;
    private final PersonalDimensionRules rules;

    public PersonalDimRulesSyncPacket(boolean active, PersonalDimensionRules rules) {
        this.active = active;
        this.rules = rules;
    }

    public static void encode(PersonalDimRulesSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeBoolean(packet.rules.disableMobSpawning);
        buffer.writeBoolean(packet.rules.lockWeather);
        buffer.writeBoolean(packet.rules.lockTime);
        buffer.writeBoolean(packet.rules.daylightCycle);
        buffer.writeLong(packet.rules.timeValue);
        buffer.writeBoolean(packet.rules.flightEnabled);
        buffer.writeFloat(packet.rules.movementSpeed);
        buffer.writeBoolean(packet.rules.noFlightInertia);
    }

    public static PersonalDimRulesSyncPacket decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = buffer.readBoolean();
        rules.lockWeather = buffer.readBoolean();
        rules.lockTime = buffer.readBoolean();
        rules.daylightCycle = buffer.readBoolean();
        rules.timeValue = buffer.readLong();
        rules.flightEnabled = buffer.readBoolean();
        rules.movementSpeed = buffer.readFloat();
        rules.noFlightInertia = buffer.readBoolean();
        return new PersonalDimRulesSyncPacket(active, rules);
    }

    public static void handle(PersonalDimRulesSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.github.aeddddd.ae2enhanced.client.ClientPersonalDimRules
                        .update(packet.active ? packet.rules : null)));
        context.setPacketHandled(true);
    }
}
