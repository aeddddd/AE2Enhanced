package com.github.aeddddd.ae2enhanced.dimension.rules;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 统一协调 base、个人维度与指环能力，避免来源各自快照并相互覆盖。 */
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

    public static void setFlySpeedSafe(PlayerCapabilities cap, float speed) {
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

    public static void setWalkSpeedSafe(PlayerCapabilities cap, float speed) {
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

    public static float getFlySpeedSafe(PlayerCapabilities cap) {
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

    public static float getWalkSpeedSafe(PlayerCapabilities cap) {
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

    private static final Map<UUID, Sources> STATES = new HashMap<>();

    public static void discardSnapshot(UUID playerId) {
        STATES.remove(playerId);
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static boolean applyCapabilities(EntityPlayerMP player, PersonalDimensionRules rules) {
        float speed = clampMovementSpeed(rules.movementSpeed);
        return updateDimension(player, new PlayerAbilityState.Modifier(rules.flightEnabled, speed, speed));
    }

    public static boolean resetAbilities(EntityPlayerMP player) {
        return updateDimension(player, null);
    }

    public static boolean applyRingCapabilities(EntityPlayerMP player, boolean allowFlying,
                                                 @Nullable Float flySpeed, @Nullable Float walkSpeed) {
        return updateRing(player, new PlayerAbilityState.Modifier(allowFlying, flySpeed, walkSpeed));
    }

    public static boolean clearRingCapabilities(EntityPlayerMP player) {
        return updateRing(player, null);
    }

    public static boolean refreshCapabilities(EntityPlayerMP player) {
        Sources sources = STATES.get(player.getUniqueID());
        return sources != null && apply(player, sources);
    }

    private static boolean updateDimension(EntityPlayerMP player, @Nullable PlayerAbilityState.Modifier modifier) {
        Sources sources = sources(player, modifier);
        if (sources == null) return false;
        sources.dimension = modifier;
        return apply(player, sources);
    }

    private static boolean updateRing(EntityPlayerMP player, @Nullable PlayerAbilityState.Modifier modifier) {
        Sources sources = sources(player, modifier);
        if (sources == null) return false;
        sources.ring = modifier;
        return apply(player, sources);
    }

    @Nullable
    private static Sources sources(EntityPlayerMP player, @Nullable PlayerAbilityState.Modifier modifier) {
        Sources sources = STATES.get(player.getUniqueID());
        if (sources == null && modifier != null) {
            sources = new Sources(player);
            STATES.put(player.getUniqueID(), sources);
        }
        return sources;
    }

    private static boolean apply(EntityPlayerMP player, Sources sources) {
        PlayerCapabilities cap = player.capabilities;
        PlayerAbilityState.Resolved resolved = PlayerAbilityState.resolve(sources.base, sources.dimension, sources.ring,
                player.isCreative() || player.isSpectator(), cap.isFlying);
        boolean changed = false;
        if (cap.allowFlying != resolved.allowFlying) {
            cap.allowFlying = resolved.allowFlying;
            changed = true;
        }
        if (cap.isFlying != resolved.isFlying) {
            cap.isFlying = resolved.isFlying;
            if (!resolved.isFlying) player.fallDistance = 0.0f;
            changed = true;
        }
        if (Math.abs(getFlySpeedSafe(cap) - resolved.flySpeed) > 1e-4f) {
            setFlySpeedSafe(cap, resolved.flySpeed);
            changed = true;
        }
        if (Math.abs(getWalkSpeedSafe(cap) - resolved.walkSpeed) > 1e-4f) {
            setWalkSpeedSafe(cap, resolved.walkSpeed);
            changed = true;
        }
        if (sources.unmanaged()) STATES.remove(player.getUniqueID());
        if (changed) player.sendPlayerAbilities();
        return changed;
    }

    private static final class Sources {
        final PlayerAbilityState.Base base;
        PlayerAbilityState.Modifier dimension;
        PlayerAbilityState.Modifier ring;

        Sources(EntityPlayerMP player) {
            PlayerCapabilities cap = player.capabilities;
            boolean privileged = player.isCreative() || player.isSpectator();
            base = new PlayerAbilityState.Base(!privileged && cap.allowFlying,
                    getFlySpeedSafe(cap), getWalkSpeedSafe(cap));
        }

        boolean unmanaged() {
            return dimension == null && ring == null;
        }
    }

    public static void tickNoFlightInertia(EntityPlayerMP player, PersonalDimensionRules rules) {
        if (!rules.noFlightInertia) return;
        PlayerCapabilities cap = player.capabilities;
        if (cap.isFlying && player.moveForward == 0.0f && player.moveStrafing == 0.0f) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
        }
    }

    public static float clampMovementSpeed(float speed) {
        if (Float.isNaN(speed) || speed < 0.05f) return 0.05f;
        if (speed > 2.0f) return 2.0f;
        return speed;
    }
}
