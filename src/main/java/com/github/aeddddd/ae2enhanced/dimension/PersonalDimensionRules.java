package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 单个玩家的个人维度规则。
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

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("disableMobSpawning", disableMobSpawning);
        tag.setBoolean("lockWeather", lockWeather);
        tag.setBoolean("lockTime", lockTime);
        tag.setBoolean("daylightCycle", daylightCycle);
        tag.setLong("timeValue", timeValue);
        tag.setBoolean("flightEnabled", flightEnabled);
        tag.setFloat("movementSpeed", movementSpeed);
        tag.setBoolean("noFlightInertia", noFlightInertia);
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
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
