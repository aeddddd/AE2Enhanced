package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.CompoundTag;

/**
 * 单个玩家的个人维度规则.
 */
public class PersonalDimensionRules {

    public boolean disableMobSpawning = false;
    public boolean lockWeather = false;
    public boolean lockTime = false;
    public boolean daylightCycle = true;
    public long timeValue = 6000L;

    public boolean flightEnabled = false;
    public float movementSpeed = 0.1f;
    public boolean noFlightInertia = false;

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("disableMobSpawning", disableMobSpawning);
        tag.putBoolean("lockWeather", lockWeather);
        tag.putBoolean("lockTime", lockTime);
        tag.putBoolean("daylightCycle", daylightCycle);
        tag.putLong("timeValue", timeValue);
        tag.putBoolean("flightEnabled", flightEnabled);
        tag.putFloat("movementSpeed", movementSpeed);
        tag.putBoolean("noFlightInertia", noFlightInertia);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        disableMobSpawning = tag.getBoolean("disableMobSpawning");
        lockWeather = tag.getBoolean("lockWeather");
        lockTime = tag.getBoolean("lockTime");
        daylightCycle = tag.getBoolean("daylightCycle");
        timeValue = tag.getLong("timeValue");
        flightEnabled = tag.getBoolean("flightEnabled");
        movementSpeed = tag.getFloat("movementSpeed");
        noFlightInertia = tag.getBoolean("noFlightInertia");
    }

    public PersonalDimensionRules copy() {
        PersonalDimensionRules copy = new PersonalDimensionRules();
        copy.disableMobSpawning = this.disableMobSpawning;
        copy.lockWeather = this.lockWeather;
        copy.lockTime = this.lockTime;
        copy.daylightCycle = this.daylightCycle;
        copy.timeValue = this.timeValue;
        copy.flightEnabled = this.flightEnabled;
        copy.movementSpeed = this.movementSpeed;
        copy.noFlightInertia = this.noFlightInertia;
        return copy;
    }
}
