package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/**
 * 气体描述符,作为 Map 的 Key.基于 Mekanism Gas 名称.
 */
public class GasDescriptor implements Descriptor {
    private final String gasName;
    private final int hash;
    private transient volatile com.mekeng.github.common.me.data.IAEGasStack aeTemplate = null;

    public GasDescriptor(com.mekeng.github.common.me.data.IAEGasStack stack) {
        mekanism.api.gas.Gas gas = stack.getGas();
        this.gasName = gas != null ? gas.getName() : "unknown";
        this.hash = gasName.hashCode();
    }

    public GasDescriptor(String gasName) {
        this.gasName = gasName;
        this.hash = gasName.hashCode();
    }

    /**
     * 根据存储的 gasName 重建 IAEGasStack 模板(stackSize=1).
     */
    public com.mekeng.github.common.me.data.IAEGasStack getAETemplate() {
        com.mekeng.github.common.me.data.IAEGasStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    mekanism.api.gas.Gas gas = mekanism.api.gas.GasRegistry.getGas(gasName);
                    if (gas == null) return null;
                    result = aeTemplate = com.mekeng.github.common.me.data.impl.AEGasStack.of(new mekanism.api.gas.GasStack(gas, 1));
                }
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GasDescriptor)) return false;
        return Objects.equals(gasName, ((GasDescriptor) obj).gasName);
    }

    public static GasDescriptor fromNBT(NBTTagCompound tag) {
        String gasName = tag.getString("gasName");
        if (gasName.isEmpty()) return null;
        return new GasDescriptor(gasName);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("gasName", gasName);
        return tag;
    }

    public String getGasName() {
        return gasName;
    }
}
