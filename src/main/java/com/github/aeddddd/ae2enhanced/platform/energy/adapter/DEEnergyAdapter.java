package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Draconic Evolution 专用能量适配器.
 *
 * <p>核心突破：对于所有实现 {@code IExtendedRFStorage} 的龙研设备(包括
 * {@code TileEnergyStorageCore}、{@code TileCraftingInjector} 等),通过反射
 * 直接操作其内部 {@code ManagedLong energy.value} 字段,实现 {@code long} 级别的
 * 瞬间注入,完全 bypass 标准 Forge {@link IEnergyStorage#receiveEnergy(int, boolean)}
 * 的 {@code int} 上限(2.1B)以及设备自身的 tick 级限流(如 CraftingInjector 的
 * {@code maxRFPerTick = cost / chargeSpeedModifier}).</p>
 *
 * <p>对于 {@code TileCraftingInjector},会额外检查 {@code currentCraftingInventory}
 * 字段：只有在注入器处于活跃合成状态时(字段非 null)才注入能量；空闲时返回 0,
 * 避免能量凭空消失.</p>
 *
 * <p>对于不实现 {@code IExtendedRFStorage} 的龙研设备,回退到标准 FE 多调用策略.</p>
 */
public class DEEnergyAdapter implements IEnergyAdapter {

    private static final String[] BLOCK_PATTERNS = {
        "draconicevolution:"
    };

    // IExtendedRFStorage 接口反射缓存
    private static Class<?> extendedRFStorageClass;
    private static Method getExtendedStorageMethod;
    private static Method getExtendedCapacityMethod;
    private static Field managedLongValueField;
    private static boolean interfaceReflectionReady = false;

    // IFusionCraftingInventory 接口反射缓存(用于 TileCraftingInjector)
    private static Method getIngredientEnergyCostMethod;
    private static boolean fusionInventoryReflectionReady = false;

    // 各具体 Tile 类的字段缓存
    private static final Map<Class<?>, Field> ENERGY_FIELD_CACHE = new HashMap<>();
    private static final Map<Class<?>, Field> CRAFTING_INV_FIELD_CACHE = new HashMap<>();

    public DEEnergyAdapter() {
        initInterfaceReflection();
        initFusionInventoryReflection();
    }

    private static synchronized void initInterfaceReflection() {
        if (interfaceReflectionReady) {
            return;
        }
        try {
            extendedRFStorageClass = Class.forName(
                    "com.brandon3055.draconicevolution.api.IExtendedRFStorage");
            getExtendedStorageMethod = extendedRFStorageClass.getMethod("getExtendedStorage");
            getExtendedCapacityMethod = extendedRFStorageClass.getMethod("getExtendedCapacity");

            Class<?> coreClass = Class.forName(
                    "com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyStorageCore");
            Field coreEnergyField = coreClass.getDeclaredField("energy");
            coreEnergyField.setAccessible(true);
            ENERGY_FIELD_CACHE.put(coreClass, coreEnergyField);

            managedLongValueField = findLongValueField(coreEnergyField.getType());

            interfaceReflectionReady = true;
        } catch (Exception e) {
            // 反射失败,将完全回退到标准 Forge 策略
        }
    }

    private static synchronized void initFusionInventoryReflection() {
        if (fusionInventoryReflectionReady) {
            return;
        }
        try {
            Class<?> fusionInvClass = Class.forName(
                    "com.brandon3055.draconicevolution.api.fusioncrafting.IFusionCraftingInventory");
            getIngredientEnergyCostMethod = fusionInvClass.getMethod("getIngredientEnergyCost");
            fusionInventoryReflectionReady = true;
        } catch (Exception e) {
            // IFusionCraftingInventory 不可用(极不可能,因为 draconic evolution 已加载)
        }
    }

    private static Field findLongValueField(Class<?> clazz) {
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField("value");
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException e) {
                // 继续查找父类
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static Field getEnergyField(TileEntity tile) {
        if (tile == null) return null;
        Class<?> clazz = tile.getClass();
        Field f = ENERGY_FIELD_CACHE.get(clazz);
        if (f != null) return f;
        if (ENERGY_FIELD_CACHE.containsKey(clazz)) return null;

        try {
            f = clazz.getDeclaredField("energy");
            if (f.getType().getSimpleName().equals("ManagedLong")) {
                f.setAccessible(true);
                ENERGY_FIELD_CACHE.put(clazz, f);
                return f;
            }
        } catch (NoSuchFieldException e) {
            // 该类没有 energy 字段
        }
        ENERGY_FIELD_CACHE.put(clazz, null);
        return null;
    }

    private static Field getCraftingInventoryField(Class<?> clazz) {
        Field f = CRAFTING_INV_FIELD_CACHE.get(clazz);
        if (f != null) return f;
        if (CRAFTING_INV_FIELD_CACHE.containsKey(clazz)) return null;

        try {
            f = clazz.getDeclaredField("currentCraftingInventory");
            f.setAccessible(true);
            CRAFTING_INV_FIELD_CACHE.put(clazz, f);
            return f;
        } catch (NoSuchFieldException e) {
            // 该类没有 currentCraftingInventory 字段
        }
        CRAFTING_INV_FIELD_CACHE.put(clazz, null);
        return null;
    }

    /**
     * 获取设备的有效容量.
     *
     * <p>正常设备使用 {@code getExtendedCapacity()}.对于 {@code TileCraftingInjector},
     * 其 {@code getExtendedCapacity()} 因实现 bug 返回 0,此时通过反射读取
     * {@code currentCraftingInventory} 字段,若活跃则调用
     * {@code IFusionCraftingInventory#getIngredientEnergyCost()} 获取真实容量；
     * 若空闲则返回 0,阻止注入.</p>
     */
    private static long getEffectiveCapacity(TileEntity tile, long stored) {
        try {
            long capacity = (Long) getExtendedCapacityMethod.invoke(tile);
            if (capacity > stored) {
                return capacity;
            }
            // capacity <= stored(包括 TileCraftingInjector 返回 0 的情况)
            Field craftingInvField = getCraftingInventoryField(tile.getClass());
            if (craftingInvField != null && fusionInventoryReflectionReady) {
                Object craftingInv = craftingInvField.get(tile);
                if (craftingInv != null) {
                    return (Long) getIngredientEnergyCostMethod.invoke(craftingInv);
                }
                // currentCraftingInventory == null：注入器空闲,禁止注入
                return 0L;
            }
            // 不是 CraftingInjector,且 capacity <= 0,视为无上限
            return Long.MAX_VALUE;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        for (String pattern : BLOCK_PATTERNS) {
            if (blockId.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getReceiveableEnergy(TileEntity tile, IEnergyStorage cap) {
        if (interfaceReflectionReady && isExtendedRFStorage(tile)) {
            Field energyField = getEnergyField(tile);
            if (energyField != null && managedLongValueField != null) {
                try {
                    long stored = (Long) getExtendedStorageMethod.invoke(tile);
                    long capacity = getEffectiveCapacity(tile, stored);
                    return Math.max(0L, capacity - stored);
                } catch (Exception e) {
                    // 反射失败,回退
                }
            }
        }
        return fallbackReceiveable(cap);
    }

    @Override
    public long injectEnergy(TileEntity tile, IEnergyStorage cap, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (interfaceReflectionReady && isExtendedRFStorage(tile)) {
            Field energyField = getEnergyField(tile);
            if (energyField != null && managedLongValueField != null) {
                try {
                    long stored = (Long) getExtendedStorageMethod.invoke(tile);
                    long capacity = getEffectiveCapacity(tile, stored);
                    long canAdd = Math.max(0L, capacity - stored);
                    long toAdd = Math.min(amount, canAdd);
                    if (toAdd > 0 && !simulate) {
                        Object managedLong = energyField.get(tile);
                        managedLongValueField.setLong(managedLong, stored + toAdd);
                        tile.markDirty();
                    }
                    return toAdd;
                } catch (Exception e) {
                    // 反射失败,回退
                }
            }
        }
        return fallbackInject(cap, amount, simulate);
    }

    private boolean isExtendedRFStorage(TileEntity tile) {
        return tile != null && extendedRFStorageClass.isInstance(tile);
    }

    private static long fallbackReceiveable(IEnergyStorage cap) {
        if (cap == null || !cap.canReceive()) {
            return 0;
        }
        return cap.receiveEnergy(Integer.MAX_VALUE, true);
    }

    private static long fallbackInject(IEnergyStorage cap, long amount, boolean simulate) {
        if (cap == null || !cap.canReceive() || amount <= 0) {
            return 0;
        }
        if (simulate) {
            return cap.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), true);
        }
        long total = 0;
        while (amount > 0) {
            int toReceive = (int) Math.min(amount, Integer.MAX_VALUE);
            int injected = cap.receiveEnergy(toReceive, false);
            if (injected <= 0) {
                break;
            }
            total += injected;
            amount -= injected;
        }
        return total;
    }
}
