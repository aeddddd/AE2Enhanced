package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 流体描述符,用于在内存中作为存储 Map 的 Key.
 * 基于 Fluid registryName + NBT 内容做 equals/hashCode.
 */
public class FluidDescriptor implements Descriptor {

    private final Fluid fluid;
    private final NBTTagCompound nbt;
    private final int hash;
    // 缓存 AE2 的 IAEFluidStack 模板,避免终端刷新时重复创建
    private transient volatile IAEFluidStack aeTemplate;

    public FluidDescriptor(FluidStack stack) {
        this.fluid = stack.getFluid();
        this.nbt = stack.tag != null ? stack.tag.copy() : null;
        this.hash = computeHash();
    }

    private FluidDescriptor(Fluid fluid, NBTTagCompound nbt) {
        this.fluid = fluid;
        this.nbt = nbt != null ? nbt.copy() : null;
        this.hash = computeHash();
    }

    private int computeHash() {
        // 只以 fluid 计算 hash,避免 NBT toString 与 Objects.hash 数组分配.
        // 不同 NBT 的流体产生碰撞,equals 会进一步区分,符合 hashCode 契约.
        return fluid != null ? fluid.hashCode() : 0;
    }

    /**
     * 供自定义二进制 Codec 使用的工厂方法.
     */
    public static FluidDescriptor fromRaw(Fluid fluid, NBTTagCompound nbt) {
        return new FluidDescriptor(fluid, nbt);
    }

    public static FluidDescriptor fromNBT(NBTTagCompound tag) {
        String id = tag.getString("id");
        if (id.isEmpty()) return null;
        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid == null) return null;
        NBTTagCompound nbt = tag.hasKey("tag", 10) ? tag.getCompoundTag("tag") : null;
        return new FluidDescriptor(fluid, nbt);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", fluid != null ? fluid.getName() : "water");
        if (nbt != null) {
            tag.setTag("tag", nbt.copy());
        }
        return tag;
    }

    public FluidStack toFluidStack() {
        if (fluid == null) return null;
        FluidStack stack = new FluidStack(fluid, 1);
        if (nbt != null) {
            stack.tag = nbt.copy();
        }
        return stack;
    }

    /**
     * 获取缓存的 IAEFluidStack 模板(stackSize=1).
     * 首次调用时通过 channel 创建,后续直接复用.
     */
    public IAEFluidStack getAETemplate(IFluidStorageChannel channel) {
        IAEFluidStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    FluidStack stack = toFluidStack();
                    if (stack == null) return null;
                    result = aeTemplate = channel.createStack(stack);
                }
            }
        }
        return result;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public NBTTagCompound getNbt() {
        return nbt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidDescriptor)) return false;
        FluidDescriptor other = (FluidDescriptor) o;
        if (fluid != other.fluid) return false;
        if (nbt == null && other.nbt == null) return true;
        if (nbt == null || other.nbt == null) return false;
        return nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
