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

    /**
     * 从 NBT 读取规则。缺失的键保留当前字段值，以兼容旧版本存档
     * （对象在构造时已带默认值）。
     */
    public void readFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("disableMobSpawning")) disableMobSpawning = tag.getBoolean("disableMobSpawning");
        if (tag.hasKey("lockWeather")) lockWeather = tag.getBoolean("lockWeather");
        if (tag.hasKey("lockTime")) lockTime = tag.getBoolean("lockTime");
        if (tag.hasKey("daylightCycle")) daylightCycle = tag.getBoolean("daylightCycle");
        if (tag.hasKey("timeValue")) timeValue = tag.getLong("timeValue");
        if (tag.hasKey("flightEnabled")) flightEnabled = tag.getBoolean("flightEnabled");
        if (tag.hasKey("movementSpeed")) movementSpeed = tag.getFloat("movementSpeed");
        if (tag.hasKey("noFlightInertia")) noFlightInertia = tag.getBoolean("noFlightInertia");
    }

    /**
     * 判断当前规则是否全部为默认值（未被玩家修改过）。
     */
    public boolean isDefault() {
        PersonalDimensionRules d = new PersonalDimensionRules();
        return disableMobSpawning == d.disableMobSpawning
                && lockWeather == d.lockWeather
                && lockTime == d.lockTime
                && daylightCycle == d.daylightCycle
                && timeValue == d.timeValue
                && flightEnabled == d.flightEnabled
                && movementSpeed == d.movementSpeed
                && noFlightInertia == d.noFlightInertia;
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
