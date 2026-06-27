package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * 流体假物品(Fluid Drop).
 * 用于在标准 AE2 物品终端中显示流体存储.
 *
 * 关键设计：使用 NBT 存储流体注册名,与 ae2fc 保持一致.
 * 不依赖 metadata,避免 AEItemStack.createItemStack() 在 stackSize=0 时丢失类型信息.
 */
public class ItemFluidDrop extends AbstractNbtDrop {

    private static final String FLUID_TAG = "FluidName";

    public ItemFluidDrop() {
        super("fluid_drop");
    }

    /**
     * 创建指定流体类型的假物品堆叠.
     */
    public static ItemStack createStack(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ItemRegistry.FLUID_DROP, fluid.amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(FLUID_TAG, fluid.getFluid().getName());
        if (fluid.tag != null) {
            tag.setTag("FluidTag", fluid.tag);
        }
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 从 ItemStack 中提取 FluidStack.
     */
    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemFluidDrop)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(FLUID_TAG, 8)) return null;
        Fluid fluid = FluidRegistry.getFluid(tag.getString(FLUID_TAG));
        if (fluid == null) return null;
        FluidStack result = new FluidStack(fluid, stack.getCount());
        if (tag.hasKey("FluidTag", 10)) {
            result.tag = tag.getCompoundTag("FluidTag");
        }
        return result;
    }

    /**
     * 判断 ItemStack 是否是流体假物品.
     * ItemFluidDrop 不依赖可选模组,instanceof 安全.
     */
    public static boolean isFluidDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFluidDrop;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        FluidStack fluid = getFluidStack(stack);
        return fluid != null ? fluid.getLocalizedName() : super.getItemStackDisplayName(stack);
    }
}
