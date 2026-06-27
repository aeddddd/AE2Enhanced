package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端提交个人维度规则修改。
 */
public class PacketPersonalDimensionRules implements IMessage {

    private boolean disableMobSpawning;
    private boolean lockWeather;
    private boolean lockTime;
    private boolean daylightCycle;
    private long timeValue;
    private boolean flightEnabled;
    private float movementSpeed;
    private boolean noFlightInertia;

    public PacketPersonalDimensionRules() {
    }

    public PacketPersonalDimensionRules(com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules rules) {
        this.disableMobSpawning = rules.disableMobSpawning;
        this.lockWeather = rules.lockWeather;
        this.lockTime = rules.lockTime;
        this.daylightCycle = rules.daylightCycle;
        this.timeValue = rules.timeValue;
        this.flightEnabled = rules.flightEnabled;
        this.movementSpeed = rules.movementSpeed;
        this.noFlightInertia = rules.noFlightInertia;
    }

    public boolean isDisableMobSpawning() { return disableMobSpawning; }
    public boolean isLockWeather() { return lockWeather; }
    public boolean isLockTime() { return lockTime; }
    public boolean isDaylightCycle() { return daylightCycle; }
    public long getTimeValue() { return timeValue; }
    public boolean isFlightEnabled() { return flightEnabled; }
    public float getMovementSpeed() { return movementSpeed; }
    public boolean isNoFlightInertia() { return noFlightInertia; }

    @Override
    public void fromBytes(ByteBuf buf) {
        int flags = buf.readUnsignedByte();
        disableMobSpawning = (flags & 1) != 0;
        lockWeather = (flags & 2) != 0;
        lockTime = (flags & 4) != 0;
        daylightCycle = (flags & 8) != 0;
        flightEnabled = (flags & 16) != 0;
        noFlightInertia = (flags & 32) != 0;
        timeValue = buf.readLong();
        movementSpeed = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int flags = 0;
        if (disableMobSpawning) flags |= 1;
        if (lockWeather) flags |= 2;
        if (lockTime) flags |= 4;
        if (daylightCycle) flags |= 8;
        if (flightEnabled) flags |= 16;
        if (noFlightInertia) flags |= 32;
        buf.writeByte(flags);
        buf.writeLong(timeValue);
        buf.writeFloat(movementSpeed);
    }
}
