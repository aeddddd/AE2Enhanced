package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 特殊 Boss 掉落辅助类。
 * <p>
 * 某些模组 Boss（如额外植物学盖亚 III）使用自定义掉落逻辑，不经过 Forge 的 LivingDropsEvent。
 * 本类在强制击杀后通过反射尝试调用这些实体的掉落方法，确保掉落物正常生成。
 */
public final class BossDropHelper {

    private BossDropHelper() {}

    private static final List<String> DROP_METHOD_NAMES = Arrays.asList(
            "dropLoot",
            "dropFewItems",
            "dropEquipment",
            "dropItem",
            "dropRewards",
            "spawnDrops",
            "dropItems",
            "generateDrops"
    );

    private static final List<String> BOSS_HINTS = Arrays.asList(
            "gaia", "boss", "guardian", "chaosguardian", "wither", "dragon"
    );

    /**
     * 尝试为被杀死的实体生成掉落物。
     *
     * @param entity  已死亡的实体
     * @param player  击杀者
     * @param source  伤害源
     * @param looting 时运/抢夺等级
     */
    public static void trySpawnBossDrops(EntityLivingBase entity, @Nullable EntityPlayer player, DamageSource source, int looting) {
        if (entity.world.isRemote || entity.isEntityAlive()) return;
        if (!isBossLike(entity)) return;

        World world = entity.world;
        BlockPos pos = entity.getPosition();
        List<ItemStack> generated = new ArrayList<>();

        // 尝试调用常见掉落方法
        for (Method m : entity.getClass().getMethods()) {
            String name = m.getName();
            Class<?>[] params = m.getParameterTypes();
            Class<?> lastParam = params.length > 0 ? params[params.length - 1] : null;

            // dropLoot(boolean recentlyHit, int looting, DamageSource source)
            if ("dropLoot".equals(name) && params.length == 3
                    && params[0] == boolean.class && params[1] == int.class && params[2] == DamageSource.class) {
                try {
                    m.setAccessible(true);
                    m.invoke(entity, true, looting, source);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] dropLoot reflection failed for {}", entity.getClass().getName());
                }
                continue;
            }

            // 通用启发式：方法名包含 drop/loot/reward/gaia，参数数量 0~3，末参数可能是 DamageSource 或 EntityPlayer
            if (!isDropLikeName(name)) continue;
            if (params.length > 3) continue;

            try {
                m.setAccessible(true);
                Object[] args = buildArgs(params, player, source, looting);
                Object ret = m.invoke(entity, args);
                if (ret instanceof List) {
                    for (Object o : (List<?>) ret) {
                        if (o instanceof ItemStack) generated.add((ItemStack) o);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 将生成的掉落物生成到世界中
        for (ItemStack stack : generated) {
            if (stack.isEmpty()) continue;
            EntityItem drop = new EntityItem(world,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            drop.setPickupDelay(10);
            world.spawnEntity(drop);
        }
    }

    private static boolean isBossLike(EntityLivingBase entity) {
        String className = entity.getClass().getName().toLowerCase();
        for (String hint : BOSS_HINTS) {
            if (className.contains(hint)) return true;
        }
        return false;
    }

    private static boolean isDropLikeName(String name) {
        String lower = name.toLowerCase();
        for (String n : DROP_METHOD_NAMES) {
            if (lower.contains(n.toLowerCase())) return true;
        }
        return lower.contains("gaia") || lower.contains("reward");
    }

    private static Object[] buildArgs(Class<?>[] params, EntityPlayer player, DamageSource source, int looting) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            if (p == boolean.class || p == Boolean.class) args[i] = true;
            else if (p == int.class || p == Integer.class) args[i] = looting;
            else if (p == float.class || p == Float.class) args[i] = 1.0f;
            else if (DamageSource.class.isAssignableFrom(p)) args[i] = source;
            else if (EntityPlayer.class.isAssignableFrom(p)) args[i] = player;
            else if (Entity.class.isAssignableFrom(p)) args[i] = player;
            else args[i] = null;
        }
        return args;
    }
}
