package com.github.aeddddd.ae2enhanced.util.compat;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * ae2fc (AE2 Fluid Crafting) 反射辅助类.
 *
 * <p>由于 ae2fc 是 compileOnly 依赖,所有硬引用必须通过反射,
 * 以避免运行时未安装 ae2fc 时触发 {@link NoClassDefFoundError}.</p>
 */
public class Ae2fcFluidHelper {

    private static final String MODID = "ae2fc";
    private static boolean checked = false;
    private static boolean loaded = false;

    // FakeFluids.packFluid2Drops(FluidStack) -> ItemStack
    private static Method packFluid2DropsMethod;
    // FakeFluids.packFluid2AEDrops(FluidStack) -> IAEItemStack
    private static Method packFluid2AEDropsMethod;

    public static boolean isLoaded() {
        if (!checked) {
            loaded = Loader.isModLoaded(MODID);
            checked = true;
        }
        return loaded;
    }

    private static void initReflection() {
        if (!isLoaded() || packFluid2DropsMethod != null) return;
        try {
            Class<?> fakeFluidsClass = Class.forName("com.glodblock.github.common.item.fake.FakeFluids");
            packFluid2DropsMethod = fakeFluidsClass.getMethod("packFluid2Drops", net.minecraftforge.fluids.FluidStack.class);
            packFluid2AEDropsMethod = fakeFluidsClass.getMethod("packFluid2AEDrops", net.minecraftforge.fluids.FluidStack.class);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to initialize ae2fc reflection.", e);
        }
    }

    @Nullable
    public static ItemStack packFluid2Drops(net.minecraftforge.fluids.FluidStack fluid) {
        initReflection();
        if (packFluid2DropsMethod == null || fluid == null) return null;
        try {
            return (ItemStack) packFluid2DropsMethod.invoke(null, fluid);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to pack fluid to drops.", e);
            return null;
        }
    }

    @Nullable
    public static IAEItemStack packFluid2AEDrops(net.minecraftforge.fluids.FluidStack fluid) {
        initReflection();
        if (packFluid2AEDropsMethod == null || fluid == null) return null;
        try {
            return (IAEItemStack) packFluid2AEDropsMethod.invoke(null, fluid);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to pack fluid to AE drops.", e);
            return null;
        }
    }
}
