package com.github.aeddddd.ae2enhanced.util.compat.botania;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Botania 魔力交互反射助手.
 *
 * <p>所有 Botania 类均通过字符串类名反射访问,避免在 Botania 未安装时触发
 * {@link NoClassDefFoundError}.本类仅由 {@code TileChunkManaNode} 在 Botania
 * 已加载的条件下使用.</p>
 */
public final class BotaniaManaHelper {

    private static final String MOD_ID = "botania";

    private static final String IMANA_RECEIVER = "vazkii.botania.api.mana.IManaReceiver";
    private static final String TILE_SPECIAL_FLOWER = "vazkii.botania.common.block.tile.TileSpecialFlower";
    private static final String SUB_TILE_GENERATING = "vazkii.botania.api.subtile.SubTileGenerating";
    private static final String SUB_TILE_ENTITY = "vazkii.botania.api.subtile.SubTileEntity";
    private static final String BLOCK_MANA_VOID = "vazkii.botania.common.block.mana.BlockManaVoid";
    private static final String BOTANIA_STATE_PROPS = "vazkii.botania.api.state.BotaniaStateProps";
    private static final String POOL_VARIANT_ENUM = "vazkii.botania.api.state.enums.PoolVariant";

    private static Class<?> imanaReceiverClass;
    private static Class<?> tileSpecialFlowerClass;
    private static Class<?> subTileGeneratingClass;
    private static Class<?> blockManaVoidClass;

    private static Method isFullMethod;
    private static Method receiveManaMethod;

    private static Field subTileField;
    private static Field poolVariantPropertyField;

    private static Class<?> poolVariantClass;
    private static Object poolVariantCreative;
    private static Object poolVariantFabulous;

    private static boolean initialized = false;
    private static boolean available = false;

    private BotaniaManaHelper() {}

    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        if (!net.minecraftforge.fml.common.Loader.isModLoaded(MOD_ID)) {
            return;
        }

        try {
            imanaReceiverClass = Class.forName(IMANA_RECEIVER);
            tileSpecialFlowerClass = Class.forName(TILE_SPECIAL_FLOWER);
            subTileGeneratingClass = Class.forName(SUB_TILE_GENERATING);
            blockManaVoidClass = Class.forName(BLOCK_MANA_VOID);

            isFullMethod = imanaReceiverClass.getMethod("isFull");
            receiveManaMethod = imanaReceiverClass.getMethod("recieveMana", int.class);

            subTileField = tileSpecialFlowerClass.getDeclaredField("subTile");
            subTileField.setAccessible(true);

            Class<?> botaniaStatePropsClass = Class.forName(BOTANIA_STATE_PROPS);
            poolVariantPropertyField = botaniaStatePropsClass.getField("POOL_VARIANT");
            poolVariantClass = Class.forName(POOL_VARIANT_ENUM);
            poolVariantCreative = Enum.valueOf((Class<Enum>) poolVariantClass, "CREATIVE");
            poolVariantFabulous = Enum.valueOf((Class<Enum>) poolVariantClass, "FABULOUS");

            available = true;
        } catch (Throwable t) {
            imanaReceiverClass = null;
            tileSpecialFlowerClass = null;
            subTileGeneratingClass = null;
            blockManaVoidClass = null;
            isFullMethod = null;
            receiveManaMethod = null;
            subTileField = null;
            poolVariantPropertyField = null;
            poolVariantClass = null;
            poolVariantCreative = null;
            poolVariantFabulous = null;
            available = false;
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error(
                    "[AE2E] BotaniaManaHelper initialization failed: {}", t.toString(), t);
        }
    }

    /**
     * 判断指定方块是否是 Mana Void.
     */
    public static boolean isManaVoid(World world, BlockPos pos) {
        if (!available || blockManaVoidClass == null) return false;
        try {
            return blockManaVoidClass.isInstance(world.getBlockState(pos).getBlock());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 判断指定 TileEntity 是否是 Botania 产能花(generating flower).
     */
    public static boolean isGeneratingFlower(TileEntity te) {
        if (!available || tileSpecialFlowerClass == null || subTileGeneratingClass == null || subTileField == null) {
            return false;
        }
        try {
            if (!tileSpecialFlowerClass.isInstance(te)) return false;
            Object subTile = subTileField.get(te);
            return subTile != null && subTileGeneratingClass.isInstance(subTile);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 判断指定 TileEntity 是否是可接收魔力的 IManaReceiver.
     */
    public static boolean isManaReceiver(TileEntity te) {
        if (!available || imanaReceiverClass == null) return false;
        return imanaReceiverClass.isInstance(te);
    }

    /**
     * 调用 IManaReceiver#isFull().
     */
    public static boolean isFull(TileEntity te) {
        if (!available || isFullMethod == null) return true;
        try {
            Object result = isFullMethod.invoke(te);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 调用 IManaReceiver#recieveMana(int).
     *
     * @param te     IManaReceiver 实例
     * @param amount 注入魔力量,Botania 内部会自动 clamp 到容量上限
     */
    public static void receiveMana(TileEntity te, int amount) {
        if (!available || receiveManaMethod == null || amount <= 0) return;
        try {
            receiveManaMethod.invoke(te, amount);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 获取目标当前 Mana.
     *
     * <p>按以下优先级尝试：</p>
     * <ol>
     *   <li>目标类 public int getCurrentMana()</li>
     *   <li>目标类 int mana 字段(含非 public,通过 setAccessible)</li>
     *   <li>TileSpecialFlower 子 tile 的 mana 字段</li>
     * </ol>
     *
     * @return 当前 mana,无法获取时返回 0
     */
    public static int getCurrentMana(TileEntity te) {
        if (!available || !isManaReceiver(te)) return 0;
        Class<?> clazz = te.getClass();
        try {
            Method m = clazz.getMethod("getCurrentMana");
            Object result = m.invoke(te);
            if (result instanceof Number) return ((Number) result).intValue();
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable ignored) {
        }
        Integer teMana = getIntField(te, "mana");
        if (teMana != null) return teMana;
        if (tileSpecialFlowerClass != null && tileSpecialFlowerClass.isInstance(te) && subTileField != null) {
            try {
                Object subTile = subTileField.get(te);
                if (subTile != null) {
                    Integer subMana = getIntField(subTile, "mana");
                    if (subMana != null) return subMana;
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static Integer getIntField(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object result = f.get(obj);
                if (result instanceof Number) return ((Number) result).intValue();
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable ignored) {
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 获取目标 Mana 容量.
     *
     * <p>按以下优先级尝试：</p>
     * <ol>
     *   <li>目标类 int manaCap 字段(魔力池等)</li>
     *   <li>TileSpecialFlower 子 tile 的 getMaxMana()</li>
     *   <li>符文祭坛的 manaToGet 字段(目标配方 mana)</li>
     * </ol>
     *
     * @return 容量,无法获取时返回 0
     */
    public static int getManaCapacity(TileEntity te) {
        if (!available || !isManaReceiver(te)) return 0;
        Integer cap = getIntField(te, "manaCap");
        if (cap != null) return cap;
        if (tileSpecialFlowerClass != null && tileSpecialFlowerClass.isInstance(te) && subTileField != null) {
            try {
                Object subTile = subTileField.get(te);
                if (subTile != null) {
                    Method maxManaMethod = subTile.getClass().getMethod("getMaxMana");
                    Object result = maxManaMethod.invoke(subTile);
                    if (result instanceof Number) return ((Number) result).intValue();
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
            }
        }
        Integer toGet = getIntField(te, "manaToGet");
        if (toGet != null) return toGet;
        return 0;
    }

    /**
     * 判断指定位置是否是 Botania 永恒魔力池(Creative / Everlasting Mana Pool).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean isEverlastingManaPool(World world, BlockPos pos) {
        if (!available || poolVariantPropertyField == null || poolVariantCreative == null) return false;
        try {
            IBlockState state = world.getBlockState(pos);
            IProperty property = (IProperty) poolVariantPropertyField.get(null);
            Object value = state.getValue(property);
            return poolVariantCreative.equals(value);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 将指定位置的永恒魔力池变为神话魔力池(Fabulous Mana Pool).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void convertEverlastingToFabulous(World world, BlockPos pos) {
        if (!available || poolVariantPropertyField == null || poolVariantFabulous == null) return;
        try {
            IBlockState state = world.getBlockState(pos);
            IProperty property = (IProperty) poolVariantPropertyField.get(null);
            IBlockState newState = state.withProperty(property, (Comparable) poolVariantFabulous);
            world.setBlockState(pos, newState);
        } catch (Throwable ignored) {
        }
    }
}
