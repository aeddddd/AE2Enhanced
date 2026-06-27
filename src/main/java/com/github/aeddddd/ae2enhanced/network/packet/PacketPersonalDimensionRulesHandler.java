package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPersonalDimensionRulesHandler implements IMessageHandler<PacketPersonalDimensionRules, IMessage> {

    @Override
    public IMessage onMessage(PacketPersonalDimensionRules message, MessageContext ctx) {
        ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
            EntityPlayerMP player = ctx.getServerHandler().player;
            PersonalDimensionRules rules = new PersonalDimensionRules();
            rules.disableMobSpawning = message.isDisableMobSpawning();
            rules.lockWeather = message.isLockWeather();
            rules.lockTime = message.isLockTime();
            rules.daylightCycle = message.isDaylightCycle();
            rules.timeValue = normalizeTime(message.getTimeValue());
            rules.flightEnabled = message.isFlightEnabled();
            rules.movementSpeed = PlayerAbilityApplier.clampMovementSpeed(message.getMovementSpeed());
            rules.noFlightInertia = message.isNoFlightInertia();
            PersonalDimensionManager.setRules(player.getUniqueID(), rules);
        });
        return null;
    }

    private static long normalizeTime(long time) {
        long t = time % 24000L;
        if (t < 0) t += 24000L;
        return t;
    }
}
