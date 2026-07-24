package com.github.aeddddd.ae2enhanced.dimension.rules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;

/**
 * 统一应用/重置个人维度的玩家能力（飞行、移动速度、飞行惯性）.
 *
 * <p>1.20 官方映射下可直接访问 {@link Abilities},不再需要 1.12 的 MCP/SRG 双名反射.</p>
 */
public final class PlayerAbilityApplier {

    private PlayerAbilityApplier() {
    }

    /**
     * 根据个人维度规则应用飞行与移动速度.
     * 应在玩家进入维度、登录或规则变更时调用,不要在每 tick 调用.
     *
     * @param player 目标玩家
     * @param rules  维度规则
     * @return 若能力发生变化返回 true
     */
    public static boolean applyCapabilities(ServerPlayer player, PersonalDimensionRules rules) {
        Abilities abilities = player.getAbilities();
        boolean changed = false;

        boolean shouldFly = player.isCreative() || rules.flightEnabled;
        if (abilities.mayfly != shouldFly) {
            abilities.mayfly = shouldFly;
            if (!shouldFly) {
                abilities.flying = false;
            }
            changed = true;
        }

        float speed = clampMovementSpeed(rules.movementSpeed);
        if (Math.abs(abilities.getFlyingSpeed() - speed) > 1e-4f
                || Math.abs(abilities.getWalkingSpeed() - speed) > 1e-4f) {
            abilities.setFlyingSpeed(speed);
            abilities.setWalkingSpeed(speed);
            changed = true;
        }

        if (changed) {
            player.onUpdateAbilities();
        }

        return changed;
    }

    /**
     * 处理无飞行惯性规则：玩家停止移动输入时清零水平速度.
     * 这需要在玩家 tick 中持续调用,因为移动输入每 tick 都会变化.
     */
    public static void tickNoFlightInertia(ServerPlayer player, PersonalDimensionRules rules) {
        if (!rules.noFlightInertia) {
            return;
        }
        Abilities abilities = player.getAbilities();
        if (abilities.flying && player.zza == 0.0f && player.xxa == 0.0f) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.0, 1.0, 0.0));
        }
    }

    /**
     * 将玩家能力恢复为默认值.
     *
     * <p>仅在玩家离开个人维度或重生时调用.创造模式玩家的飞行能力不会被清除.</p>
     */
    public static void resetAbilities(ServerPlayer player) {
        if (player.isCreative()) {
            return;
        }
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (abilities.mayfly) {
            abilities.mayfly = false;
            changed = true;
        }
        if (abilities.flying) {
            abilities.flying = false;
            changed = true;
        }
        if (Math.abs(abilities.getWalkingSpeed() - 0.1f) > 1e-4f) {
            abilities.setWalkingSpeed(0.1f);
            changed = true;
        }
        if (Math.abs(abilities.getFlyingSpeed() - 0.05f) > 1e-4f) {
            abilities.setFlyingSpeed(0.05f);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    /**
     * 校验并限制移动速度在合理范围内,防止客户端伪造极大/极小值.
     */
    public static float clampMovementSpeed(float speed) {
        if (Float.isNaN(speed) || speed < 0.05f) {
            return 0.05f;
        }
        if (speed > 2.0f) {
            return 2.0f;
        }
        return speed;
    }
}
