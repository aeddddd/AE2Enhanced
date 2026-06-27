package com.github.aeddddd.ae2enhanced.util.fakeitem;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import mekanism.api.gas.GasStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;

/**
 * 气体假物品的 FakeItemHandler 实现与工具方法.
 *
 * 在模组初始化时调用 FakeGases.init() 注册 handler.
 */
public final class FakeGases {

    private static final String GAS_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemGasDrop";

    public static void init() {
        FakeItemRegister.registerHandler(ItemGasDrop.class, new FakeItemHandler<GasStack, IAEGasStack>() {

            @Override
            public GasStack getStack(ItemStack stack) {
                return ItemGasDrop.getGasStack(stack);
            }

            @Override
            public GasStack getStack(IAEItemStack stack) {
                return stack == null ? null : getStack(stack.createItemStack());
            }

            @Override
            public IAEGasStack getAEStack(ItemStack stack) {
                return getAEStack(AEItemStack.fromItemStack(stack));
            }

            @Override
            public IAEGasStack getAEStack(IAEItemStack stack) {
                if (stack == null) return null;
                GasStack gas = getStack(stack.createItemStack());
                if (gas == null || gas.getGas() == null) return null;
                AEGasStack result = AEGasStack.of(gas);
                if (result != null) {
                    result.setStackSize(stack.getStackSize());
                }
                return result;
            }

            @Override
            public ItemStack packStack(GasStack gas) {
                return ItemGasDrop.createStack(gas);
            }

            @Override
            public IAEItemStack packAEStack(GasStack gas) {
                if (gas == null || gas.amount <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(packStack(gas));
                if (stack == null) return null;
                stack.setStackSize(gas.amount);
                return stack;
            }

            @Override
            public IAEItemStack packAEStackLong(IAEGasStack gas) {
                if (gas == null || gas.getStackSize() <= 0) return null;
                IAEItemStack stack = AEItemStack.fromItemStack(
                        ItemGasDrop.createStack(new GasStack(gas.getGas(), 1)));
                if (stack == null) return null;
                stack.setStackSize(gas.getStackSize());
                return stack;
            }
        });
    }

    /**
     * 判断 ItemStack 是否是气体假物品.
     * 注意：本方法直接引用 ItemGasDrop,仅供条件配置(gas.json)中的代码使用.
     * 无条件配置中的代码应使用 {@link #isGasFakeItemSafe(ItemStack)}.
     */
    public static boolean isGasFakeItem(ItemStack stack) {
        return ItemGasDrop.isGasDrop(stack);
    }

    /**
     * 安全判断 ItemStack 是否是气体假物品.
     * 使用字符串比较而非直接引用 ItemGasDrop 类,避免 Mekanism 不存在时
     * 触发 NoClassDefFoundError.
     */
    public static boolean isGasFakeItemSafe(ItemStack stack) {
        return !stack.isEmpty() && GAS_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取气体假物品的气体注册名.
     * 直接从 NBT 读取,不加载 ItemGasDrop 类.
     */
    public static String tryGetGasName(ItemStack stack) {
        if (!isGasFakeItemSafe(stack)) return null;
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString("GasName") : null;
    }

    public static ItemStack packGas2Drops(GasStack stack) {
        return FakeItemRegister.packStack(stack, ItemRegistry.GAS_DROP);
    }

    public static IAEItemStack packGas2AEDrops(GasStack stack) {
        return FakeItemRegister.packAEStack(stack, ItemRegistry.GAS_DROP);
    }

    public static IAEItemStack packGas2AEDrops(IAEGasStack stack) {
        return FakeItemRegister.packAEStackLong(stack, ItemRegistry.GAS_DROP);
    }

    // 兼容方法：供 MixinNetworkMonitorGas / MixinNetworkInventoryHandlerGas 调用
    public static IAEItemStack packGas(IAEGasStack gasStack) {
        return packGas2AEDrops(gasStack);
    }

    public static IAEGasStack unpackGas(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack stack = itemStack.createItemStack();
        if (isGasFakeItemSafe(stack)) {
            return FakeItemRegister.getAEStack(itemStack);
        }
        // 兼容 ae2fc 的 ItemGasDrop / ItemGasPacket
        String itemClass = stack.getItem().getClass().getName();
        if ("com.glodblock.github.common.item.ItemGasDrop".equals(itemClass)
                || "com.glodblock.github.common.item.ItemGasPacket".equals(itemClass)) {
            try {
                GasStack gasStack = unpackAe2fcGas(stack);
                if (gasStack != null && gasStack.getGas() != null) {
                    AEGasStack result = AEGasStack.of(gasStack);
                    if (result != null) {
                        result.setStackSize(itemStack.getStackSize());
                    }
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static GasStack unpackAe2fcGas(ItemStack stack) throws Exception {
        if (!stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        // ItemGasPacket: GasStack 存储在 "GasStack" NBT 中
        if (tag.hasKey("GasStack", 10)) {
            return GasStack.readFromNBT(tag.getCompoundTag("GasStack"));
        }
        // ItemGasDrop: Gas 名称存储在 "Gas" 中,amount 为 stack count
        if (tag.hasKey("Gas", 8)) {
            String gasName = tag.getString("Gas");
            Class<?> gasRegistryClass = Class.forName("mekanism.api.gas.GasRegistry");
            Object gas = gasRegistryClass.getMethod("getGas", String.class).invoke(null, gasName);
            if (gas != null) {
                Class<?> gasClass = Class.forName("mekanism.api.gas.Gas");
                Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
                return (GasStack) gasStackClass.getConstructor(gasClass, int.class).newInstance(gas, stack.getCount());
            }
        }
        return null;
    }

    /**
     * 反射方法：从 JEI 拖动的 GasStack 对象打包为 AE 假物品.
     * 供 GhostIngredientTarget 调用(避免 GhostIngredientTarget 硬引用 Mekanism 类).
     */
    public static IAEItemStack tryPackJEIGas(Object ingredient) {
        if (!Loader.isModLoaded("mekanism") || ingredient == null) return null;
        try {
            Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
            if (!gasStackClass.isInstance(ingredient)) return null;
            Object gas = ingredient;
            Object gasType = gasStackClass.getMethod("getGas").invoke(gas);
            int amount = gasStackClass.getField("amount").getInt(gas);
            return packGas2AEDrops((GasStack) createGasStackReflection(gasStackClass, gasType, Math.min(amount, 1000)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射方法：从手持的气体物品(IGasItem)打包为 AE 假物品.
     * 供 GhostIngredientTarget / Container 调用.
     */
    public static IAEItemStack tryPackJEIGasFromItem(ItemStack is) {
        if (!Loader.isModLoaded("mekanism") || is == null || is.isEmpty()) return null;
        try {
            Class<?> gasItemClass = Class.forName("mekanism.api.gas.IGasItem");
            Class<?> gasStackClass = Class.forName("mekanism.api.gas.GasStack");
            java.lang.reflect.Field amountField = gasStackClass.getField("amount");
            // 优先尝试 IGasItem
            if (gasItemClass.isInstance(is.getItem())) {
                Object gasItem = is.getItem();
                Object gasStack = gasItemClass.getMethod("getGas", ItemStack.class).invoke(gasItem, is);
                if (gasStack != null) {
                    int amount = amountField.getInt(gasStack);
                    Object gasType = gasStackClass.getMethod("getGas").invoke(gasStack);
                    return packGas2AEDrops((GasStack) createGasStackReflection(gasStackClass, gasType, Math.min(amount, 1000)));
                }
            }
            // 回退：从 NBT "mekData" -> "stored" 读取 GasStack
            if (is.hasTagCompound() && is.getTagCompound().hasKey("mekData", 10)) {
                Object mekData = is.getTagCompound().getCompoundTag("mekData");
                if (mekData != null && ((net.minecraft.nbt.NBTTagCompound) mekData).hasKey("stored", 10)) {
                    Object stored = ((net.minecraft.nbt.NBTTagCompound) mekData).getCompoundTag("stored");
                    java.lang.reflect.Method readFromNBT = gasStackClass.getMethod("readFromNBT", net.minecraft.nbt.NBTTagCompound.class);
                    Object gasStack = readFromNBT.invoke(null, stored);
                    if (gasStack != null) {
                        int amount = amountField.getInt(gasStack);
                        if (amount > 0) {
                            Object gasType = gasStackClass.getMethod("getGas").invoke(gasStack);
                            return packGas2AEDrops((GasStack) createGasStackReflection(gasStackClass, gasType, Math.min(amount, 1000)));
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 反射创建 GasStack 的共享逻辑,消除 tryPackJEIGas / tryPackJEIGasFromItem 中的重复代码.
     */
    private static Object createGasStackReflection(Class<?> gasStackClass, Object gasType, int amount) throws Exception {
        Class<?> gasClass = Class.forName("mekanism.api.gas.Gas");
        return gasStackClass.getConstructor(gasClass, int.class).newInstance(gasType, amount);
    }
}
