package com.github.aeddddd.ae2enhanced.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 特殊 Boss 掉落辅助类（1.20.1 移植版）.
 * <p>
 * 某些模组 Boss（如额外植物学盖亚 III）使用自定义掉落逻辑,不经过 Forge 的 LivingDropsEvent.
 * 本类在强制击杀后通过反射尝试调用这些实体的掉落方法,确保掉落物正常生成.
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
            "generateDrops",
            "dropCustomDeathLoot",
            "dropFromLootTable"
    );

    private static final List<String> BOSS_HINTS = Arrays.asList(
            "gaia", "boss", "guardian", "chaosguardian", "wither", "dragon"
    );

    /**
     * 尝试为被杀死的实体生成掉落物.
     *
     * @param entity  已死亡的实体
     * @param player  击杀者
     * @param source  伤害源
     * @param looting 时运/抢夺等级
     */
    public static void trySpawnBossDrops(LivingEntity entity, @Nullable Player player, DamageSource source,
            int looting) {
        if (entity.level().isClientSide() || entity.isAlive()) return;
        if (!isBossLike(entity)) return;

        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        List<ItemStack> generated = new ArrayList<>();

        // 尝试调用常见掉落方法
        for (Method m : entity.getClass().getMethods()) {
            String name = m.getName();
            Class<?>[] params = m.getParameterTypes();

            // dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer) —— 1.20 标准签名
            if ("dropCustomDeathLoot".equals(name) && params.length == 3
                    && params[0] == DamageSource.class && params[1] == int.class && params[2] == boolean.class) {
                try {
                    m.setAccessible(true);
                    m.invoke(entity, source, looting, true);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] dropCustomDeathLoot reflection failed for {}",
                            entity.getClass().getName());
                }
                continue;
            }

            // 通用启发式：方法名包含 drop/loot/reward/gaia,参数数量 0~3
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
            ItemEntity drop = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            drop.setPickUpDelay(10);
            level.addFreshEntity(drop);
        }
    }

    private static boolean isBossLike(LivingEntity entity) {
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

    private static Object[] buildArgs(Class<?>[] params, Player player, DamageSource source, int looting) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> p = params[i];
            if (p == boolean.class || p == Boolean.class) args[i] = true;
            else if (p == int.class || p == Integer.class) args[i] = looting;
            else if (p == float.class || p == Float.class) args[i] = 1.0f;
            else if (DamageSource.class.isAssignableFrom(p)) args[i] = source;
            else if (Player.class.isAssignableFrom(p)) args[i] = player;
            else if (Entity.class.isAssignableFrom(p)) args[i] = player;
            else args[i] = null;
        }
        return args;
    }
}
