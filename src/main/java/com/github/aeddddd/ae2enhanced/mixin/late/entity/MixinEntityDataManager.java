package com.github.aeddddd.ae2enhanced.mixin.late.entity;

import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 EntityDataManager.set() 层面拦截对 HEALTH 参数的治疗性修改。
 * 某些实体（如 dechaosislandlegacy 的 DraconicGuardianEntity）会绕过 setHealth()
 * 直接通过 dataManager.set(HEALTH, maxHealth) 来复活，此 mixin 专门堵这个口子。
 */
@Mixin(value = EntityDataManager.class, remap = true)
public class MixinEntityDataManager {

    private static final java.lang.reflect.Field EDM_ENTITY;
    private static final Object HEALTH_PARAM;
    private static final java.lang.reflect.Method EDM_GET;

    static {
        java.lang.reflect.Field entityField = null;
        Object param = null;
        java.lang.reflect.Method getMethod = null;
        try {
            for (java.lang.reflect.Field f : EntityDataManager.class.getDeclaredFields()) {
                if (f.getType() == Entity.class) {
                    entityField = f;
                    entityField.setAccessible(true);
                    break;
                }
            }
            for (java.lang.reflect.Field f : EntityLivingBase.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) &&
                    f.getType().getSimpleName().equals("DataParameter")) {
                    java.lang.reflect.Type genericType = f.getGenericType();
                    if (genericType instanceof java.lang.reflect.ParameterizedType) {
                        java.lang.reflect.Type[] args = ((java.lang.reflect.ParameterizedType) genericType).getActualTypeArguments();
                        if (args.length > 0 && "java.lang.Float".equals(args[0].getTypeName())) {
                            f.setAccessible(true);
                            param = f.get(null);
                            break;
                        }
                    }
                }
            }
            for (java.lang.reflect.Method m : EntityDataManager.class.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 &&
                    m.getParameterTypes()[0].getSimpleName().equals("DataParameter") &&
                    m.getReturnType() == Object.class) {
                    getMethod = m;
                    getMethod.setAccessible(true);
                    break;
                }
            }
        } catch (Exception ignored) {}
        EDM_ENTITY = entityField;
        HEALTH_PARAM = param;
        EDM_GET = getMethod;
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void ae2e$onSet(Object key, Object value, CallbackInfo ci) {
        if (HEALTH_PARAM == null || EDM_ENTITY == null || EDM_GET == null) return;
        if (key != HEALTH_PARAM) return;

        try {
            Entity entity = (Entity) EDM_ENTITY.get(this);
            if (entity == null || !(entity instanceof EntityLivingBase)) return;

            EntityLivingBase living = (EntityLivingBase) entity;
            if (!OmniToolUpgrades.hasAntiHeal(living)) return;

            float newHealth = value instanceof Float ? (Float) value : 0.0f;
            Object currentValue = EDM_GET.invoke(this, key);
            float currentHealth = currentValue instanceof Float ? (Float) currentValue : 0.0f;

            if (newHealth > currentHealth) {
                ci.cancel();
            }
        } catch (Exception ignored) {}
    }
}
