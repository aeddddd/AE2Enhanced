package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.client.ClientPersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 服务端 -> 客户端的个人维度规则同步包。
 */
public class PacketPersonalDimensionRulesSync implements IMessage {

    private PersonalDimensionRules rules;

    public PacketPersonalDimensionRulesSync() {
    }

    public PacketPersonalDimensionRulesSync(PersonalDimensionRules rules) {
        this.rules = rules != null ? rules.copy() : new PersonalDimensionRules();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rules = new PersonalDimensionRules();
        int flags = buf.readUnsignedByte();
        rules.disableMobSpawning = (flags & 1) != 0;
        rules.lockWeather = (flags & 2) != 0;
        rules.lockTime = (flags & 4) != 0;
        rules.daylightCycle = (flags & 8) != 0;
        rules.flightEnabled = (flags & 16) != 0;
        rules.noFlightInertia = (flags & 32) != 0;
        rules.timeValue = buf.readLong();
        rules.movementSpeed = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int flags = 0;
        if (rules.disableMobSpawning) flags |= 1;
        if (rules.lockWeather) flags |= 2;
        if (rules.lockTime) flags |= 4;
        if (rules.daylightCycle) flags |= 8;
        if (rules.flightEnabled) flags |= 16;
        if (rules.noFlightInertia) flags |= 32;
        buf.writeByte(flags);
        buf.writeLong(rules.timeValue);
        buf.writeFloat(rules.movementSpeed);
    }

    public static class Handler implements IMessageHandler<PacketPersonalDimensionRulesSync, IMessage> {
        @Override
        public IMessage onMessage(PacketPersonalDimensionRulesSync message, MessageContext ctx) {
            scheduleUpdate(message.rules);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void scheduleUpdate(PersonalDimensionRules rules) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                    ClientPersonalDimensionRules.update(rules));
        }
    }
}
