package com.github.aeddddd.ae2enhanced.dimension.rules;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一应用/重置个人维度的玩家能力（飞行、移动速度、飞行惯性）。
 *
 * <p>将原本散落在 {@code PersonalDimensionManager} 中的能力逻辑抽离，
 * 避免 WorldTick 与 PlayerTick 重复刷新，并提供一致的 reset 行为。</p>
 *
 * <p>移动速度通过反射设置，兼容部分服务端/插件环境（如 Mohist/CatServer/Arclight
 * 或特殊 mod 改造）中 {@code PlayerCapabilities.setFlySpeed} / {@code setPlayerWalkSpeed}
 * 方法名/签名不一致或被剥离的情况。</p>
 */
public final class PlayerAbilityApplier {

    private PlayerAbilityApplier() {}

    private static final Method SET_FLY_SPEED;
    private static final Method SET_WALK_SPEED;
    private static final Field FLY_SPEED_FIELD;
    private static final Field WALK_SPEED_FIELD;
    private static final boolean FLY_SPEED_FIELD_ACCESSIBLE;
    private static final boolean WALK_SPEED_FIELD_ACCESSIBLE;

    static {
        SET_FLY_SPEED = findMethod(PlayerCapabilities.class, "setFlySpeed", "func_75092_a", float.class);
        SET_WALK_SPEED = findMethod(PlayerCapabilities.class, "setPlayerWalkSpeed", "func_82877_b", float.class);

        Field flyField = null;
        Field walkField = null;
        try {
            flyField = PlayerCapabilities.class.getDeclaredField("flySpeed");
            flyField.setAccessible(true);
        } catch (Exception e) {
            try {
                flyField = PlayerCapabilities.class.getDeclaredField("field_75096_f");
                flyField.setAccessible(true);
            } catch (Exception ignored) {
            }
        }
        try {
            walkField = PlayerCapabilities.class.getDeclaredField("walkSpeed");
            walkField.setAccessible(true);
        } catch (Exception e) {
            try {
                walkField = PlayerCapabilities.class.getDeclaredField("field_75097_g");
                walkField.setAccessible(true);
            } catch (Exception ignored) {
            }
        }
        FLY_SPEED_FIELD = flyField;
        WALK_SPEED_FIELD = walkField;
        FLY_SPEED_FIELD_ACCESSIBLE = flyField != null;
        WALK_SPEED_FIELD_ACCESSIBLE = walkField != null;
    }

    private static Method findMethod(Class<?> clazz, String mcpName, String srgName, Class<?>... params) {
        try {
            return clazz.getDeclaredMethod(mcpName, params);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getDeclaredMethod(srgName, params);
            } catch (NoSuchMethodException e2) {
                AE2Enhanced.LOGGER.warn("[AE2E] Could not find method {} or {} in {}", mcpName, srgName, clazz.getName());
                return null;
            }
        }
    }

    private static void setFlySpeedSafe(PlayerCapabilities cap, float speed) {
        if (SET_FLY_SPEED != null) {
            try {
                SET_FLY_SPEED.invoke(cap, speed);
                return;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.trace("[AE2E] setFlySpeed reflection failed, falling back to field", e);
            }
        }
        if (FLY_SPEED_FIELD_ACCESSIBLE) {
            try {
                FLY_SPEED_FIELD.setFloat(cap, speed);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to set PlayerCapabilities.flySpeed", e);
            }
        }
    }

    private static void setWalkSpeedSafe(PlayerCapabilities cap, float speed) {
        if (SET_WALK_SPEED != null) {
            try {
                SET_WALK_SPEED.invoke(cap, speed);
                return;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.trace("[AE2E] setPlayerWalkSpeed reflection failed, falling back to field", e);
            }
        }
        if (WALK_SPEED_FIELD_ACCESSIBLE) {
            try {
                WALK_SPEED_FIELD.setFloat(cap, speed);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to set PlayerCapabilities.walkSpeed", e);
            }
        }
    }

    private static float getFlySpeedSafe(PlayerCapabilities cap) {
        try {
            return cap.getFlySpeed();
        } catch (Exception e) {
            if (FLY_SPEED_FIELD_ACCESSIBLE) {
                try {
                    return FLY_SPEED_FIELD.getFloat(cap);
                } catch (Exception ignored) {
                }
            }
            return 0.05f;
        }
    }

    private static float getWalkSpeedSafe(PlayerCapabilities cap) {
        try {
            return cap.getWalkSpeed();
        } catch (Exception e) {
            if (WALK_SPEED_FIELD_ACCESSIBLE) {
                try {
                    return WALK_SPEED_FIELD.getFloat(cap);
                } catch (Exception ignored) {
                }
            }
            return 0.1f;
        }
    }

    /**
     * 进入个人维度前的玩家能力快照（运行时状态，不持久化）。
     * 离开时优先恢复快照，避免破坏其他模组（如天使指环）提供的飞行/速度来源。
     */
    private static final Map<UUID, CapSnapshot> CAP_SNAPSHOTS = new HashMap<>();

    /**
     * 玩家能力快照。
     */
    private static final class CapSnapshot {
        final boolean allowFlying;
        final boolean isFlying;
        final float flySpeed;
        final float walkSpeed;

        CapSnapshot(boolean allowFlying, boolean isFlying, float flySpeed, float walkSpeed) {
            this.allowFlying = allowFlying;
            this.isFlying = isFlying;
            this.flySpeed = flySpeed;
            this.walkSpeed = walkSpeed;
        }
    }

    /**
     * 玩家退出登录时清理能力快照，防止离线玩家 UUID 在 map 中累积泄漏。
     */
    public static void discardSnapshot(UUID playerId) {
        CAP_SNAPSHOTS.remove(playerId);
    }

    /**
     * 根据个人维度规则应用飞行与移动速度。
     * 应在玩家进入维度、登录或规则变更时调用，不要在每 tick 调用。
     *
     * <p>首次进入时会快照玩家当前能力，离开维度时通过 {@link #resetAbilities}
     * 恢复快照而非硬编码默认值，以兼容其他模组的飞行/速度来源。</p>
     *
     * @param player 目标玩家
     * @param rules  维度规则
     * @return 若能力发生变化返回 true
     */
    public static boolean applyCapabilities(EntityPlayerMP player, PersonalDimensionRules rules) {
        PlayerCapabilities cap = player.capabilities;
        // 仅在无快照时记录，避免维度内重复应用把已被修改的能力当作"原始状态"
        CAP_SNAPSHOTS.computeIfAbsent(player.getUniqueID(), id -> new CapSnapshot(
                cap.allowFlying, cap.isFlying, getFlySpeedSafe(cap), getWalkSpeedSafe(cap)));
        boolean changed = false;

        boolean shouldFly = player.isCreative() || rules.flightEnabled;
        if (cap.allowFlying != shouldFly) {
            cap.allowFlying = shouldFly;
            if (!shouldFly) {
                cap.isFlying = false;
            }
            changed = true;
        }

        float speed = clampMovementSpeed(rules.movementSpeed);
        if (Math.abs(getFlySpeedSafe(cap) - speed) > 1e-4f || Math.abs(getWalkSpeedSafe(cap) - speed) > 1e-4f) {
            setFlySpeedSafe(cap, speed);
            setWalkSpeedSafe(cap, speed);
            changed = true;
        }

        if (changed) {
            player.sendPlayerAbilities();
        }

        return changed;
    }

    /**
     * 处理无飞行惯性规则：玩家停止移动输入时清零水平速度。
     * 这需要在玩家 tick 中持续调用，因为移动输入每 tick 都会变化。
     */
    public static void tickNoFlightInertia(EntityPlayerMP player, PersonalDimensionRules rules) {
        if (!rules.noFlightInertia) return;
        PlayerCapabilities cap = player.capabilities;
        if (cap.isFlying && player.moveForward == 0.0f && player.moveStrafing == 0.0f) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
        }
    }

    /**
     * 恢复玩家能力。
     *
     * <p>仅在玩家离开个人维度或重生时调用。优先恢复进入维度前的能力快照
     * （保留其他模组授予的飞行/速度来源）；无快照时（如直接出生在维度外等边界）
     * 才回退到原版默认值。创造模式玩家的能力不会被修改。</p>
     *
     * @param player 目标玩家
     */
    public static void resetAbilities(EntityPlayerMP player) {
        if (player.isCreative()) {
            CAP_SNAPSHOTS.remove(player.getUniqueID());
            return;
        }
        PlayerCapabilities cap = player.capabilities;
        CapSnapshot snapshot = CAP_SNAPSHOTS.remove(player.getUniqueID());
        boolean changed = false;
        if (snapshot != null) {
            // 恢复快照：回到进入维度前的状态，不破坏其他模组的飞行/速度来源
            boolean targetAllow = snapshot.allowFlying;
            if (cap.allowFlying != targetAllow) {
                cap.allowFlying = targetAllow;
                changed = true;
            }
            // 快照中的飞行状态仅在允许飞行时恢复
            boolean targetFlying = snapshot.isFlying && targetAllow;
            if (cap.isFlying != targetFlying) {
                cap.isFlying = targetFlying;
                changed = true;
            }
            if (Math.abs(getWalkSpeedSafe(cap) - snapshot.walkSpeed) > 1e-4f) {
                setWalkSpeedSafe(cap, snapshot.walkSpeed);
                changed = true;
            }
            if (Math.abs(getFlySpeedSafe(cap) - snapshot.flySpeed) > 1e-4f) {
                setFlySpeedSafe(cap, snapshot.flySpeed);
                changed = true;
            }
        } else {
            // 无快照时回退原版默认值
            if (cap.allowFlying) {
                cap.allowFlying = false;
                changed = true;
            }
            if (cap.isFlying) {
                cap.isFlying = false;
                changed = true;
            }
            if (Math.abs(getWalkSpeedSafe(cap) - 0.1f) > 1e-4f) {
                setWalkSpeedSafe(cap, 0.1f);
                changed = true;
            }
            if (Math.abs(getFlySpeedSafe(cap) - 0.05f) > 1e-4f) {
                setFlySpeedSafe(cap, 0.05f);
                changed = true;
            }
        }
        if (changed) {
            player.sendPlayerAbilities();
        }
    }

    /**
     * 校验并限制移动速度在合理范围内，防止客户端伪造极大/极小值。
     */
    public static float clampMovementSpeed(float speed) {
        if (Float.isNaN(speed) || speed < 0.05f) return 0.05f;
        if (speed > 2.0f) return 2.0f;
        return speed;
    }
}
