package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.reflection.ReflectionCache;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thaumcraft Handler 的反射辅助类.
 *
 * 缓存对 Thaumcraft API 全部使用到的类/方法/构造器的反射引用,避免重复反射带来的性能损耗.
 * 查找统一走 {@link ReflectionCache}:全部缓存由静态字段初始化一次性完成,失败时只记警告、
 * 不抛异常,未命中的缓存项保持 null.
 *
 * 对应 handler 由 HandlerRegistry 通过 Loader.isModLoaded("thaumcraft") 门控加载,
 * 正常路径下 Thaumcraft 必然存在;ThaumicEnergistics 为可选,缺失时源质消耗返回 null
 * (见 {@link #createEssentiaCosts}:返回 null 而非空列表,上层据此区分"无法计价").
 *
 * 本类自身不引用任何 thaumcraft / thaumicenergistics 类型,因此可以安全地被无条件加载.
 */
public class ThaumcraftReflectionHelper {

    // ---- Thaumcraft API 类 ----
    public static final Class<?> CLASS_ASPECT = ReflectionCache.clazz("thaumcraft.api.aspects.Aspect");
    public static final Class<?> CLASS_ASPECT_LIST = ReflectionCache.clazz("thaumcraft.api.aspects.AspectList");
    public static final Class<?> CLASS_INFUSION_RECIPE =
            ReflectionCache.clazz("thaumcraft.api.crafting.InfusionRecipe");
    public static final Class<?> CLASS_CRUCIBLE_RECIPE =
            ReflectionCache.clazz("thaumcraft.api.crafting.CrucibleRecipe");

    private static final Class<?> CLASS_THAUMCRAFT_API = ReflectionCache.clazz("thaumcraft.api.ThaumcraftApi");
    private static final Class<?> CLASS_CAPABILITIES =
            ReflectionCache.clazz("thaumcraft.api.capabilities.ThaumcraftCapabilities");
    private static final Class<?> CLASS_PLAYER_KNOWLEDGE =
            ReflectionCache.clazz("thaumcraft.api.capabilities.IPlayerKnowledge");

    // ---- ThaumcraftApi ----
    public static final Method METHOD_GET_CRAFTING_RECIPES =
            ReflectionCache.method(CLASS_THAUMCRAFT_API, "getCraftingRecipes");
    public static final Method METHOD_GET_INFUSION_RECIPE =
            ReflectionCache.method(CLASS_THAUMCRAFT_API, "getInfusionRecipe", ItemStack.class);
    public static final Method METHOD_GET_CRUCIBLE_RECIPE =
            ReflectionCache.method(CLASS_THAUMCRAFT_API, "getCrucibleRecipe", ItemStack.class);

    // ---- ThaumcraftCapabilities / IPlayerKnowledge ----
    public static final Method METHOD_GET_KNOWLEDGE =
            ReflectionCache.method(CLASS_CAPABILITIES, "getKnowledge", EntityPlayer.class);
    public static final Method METHOD_KNOWLEDGE_ADD_RESEARCH =
            ReflectionCache.method(CLASS_PLAYER_KNOWLEDGE, "addResearch", String.class);
    public static final Method METHOD_KNOWLEDGE_REMOVE_RESEARCH =
            ReflectionCache.method(CLASS_PLAYER_KNOWLEDGE, "removeResearch", String.class);
    public static final Method METHOD_KNOWLEDGE_SET_RESEARCH_STAGE =
            ReflectionCache.method(CLASS_PLAYER_KNOWLEDGE, "setResearchStage", String.class, int.class);
    public static final Method METHOD_KNOWLEDGE_SYNC =
            ReflectionCache.method(CLASS_PLAYER_KNOWLEDGE, "sync", EntityPlayerMP.class);

    // ---- InfusionRecipe ----
    public static final Method METHOD_INFUSION_GET_RESEARCH =
            ReflectionCache.method(CLASS_INFUSION_RECIPE, "getResearch");
    public static final Method METHOD_INFUSION_GET_RECIPE_INPUT =
            ReflectionCache.method(CLASS_INFUSION_RECIPE, "getRecipeInput");
    public static final Method METHOD_INFUSION_GET_COMPONENTS =
            ReflectionCache.method(CLASS_INFUSION_RECIPE, "getComponents");
    public static final Method METHOD_INFUSION_GET_RECIPE_OUTPUT =
            ReflectionCache.method(CLASS_INFUSION_RECIPE, "getRecipeOutput");
    public static final Method METHOD_INFUSION_GET_ASPECTS =
            ReflectionCache.method(CLASS_INFUSION_RECIPE, "getAspects");

    // ---- CrucibleRecipe ----
    public static final Method METHOD_CRUCIBLE_GET_RESEARCH =
            ReflectionCache.method(CLASS_CRUCIBLE_RECIPE, "getResearch");
    public static final Method METHOD_CRUCIBLE_GET_CATALYST =
            ReflectionCache.method(CLASS_CRUCIBLE_RECIPE, "getCatalyst");
    public static final Method METHOD_CRUCIBLE_GET_ASPECTS =
            ReflectionCache.method(CLASS_CRUCIBLE_RECIPE, "getAspects");

    // ---- AspectList ----
    public static final Method METHOD_ASPECT_LIST_SIZE = ReflectionCache.method(CLASS_ASPECT_LIST, "size");
    public static final Method METHOD_ASPECT_LIST_GET_ASPECTS =
            ReflectionCache.method(CLASS_ASPECT_LIST, "getAspects");
    public static final Method METHOD_ASPECT_LIST_GET_AMOUNT =
            ReflectionCache.method(CLASS_ASPECT_LIST, "getAmount", CLASS_ASPECT);

    // ---- ThaumicEnergistics (可选) ----
    private static final Class<?> CLASS_ESSENTIA_STACK =
            ReflectionCache.clazz("thaumicenergistics.api.EssentiaStack");
    private static final Class<?> CLASS_AE_ESSENTIA_STACK =
            ReflectionCache.clazz("thaumicenergistics.integration.appeng.AEEssentiaStack");
    public static final Constructor<?> CTOR_ESSENTIA_STACK =
            ReflectionCache.constructor(CLASS_ESSENTIA_STACK, CLASS_ASPECT, int.class);
    public static final Method METHOD_FROM_ESSENTIA_STACK =
            ReflectionCache.method(CLASS_AE_ESSENTIA_STACK, "fromEssentiaStack", CLASS_ESSENTIA_STACK);

    static {
        // 查找失败只 warn 不抛 —— 缺 Thaumcraft / 版本不匹配时各缓存项保持 null,
        // 由 handler 自行降级(拒绝计价);逐项未命中的原因由 ReflectionCache 按 debug 记录。
        if (CLASS_ASPECT == null || CLASS_ASPECT_LIST == null || METHOD_GET_INFUSION_RECIPE == null
                || METHOD_KNOWLEDGE_ADD_RESEARCH == null || METHOD_INFUSION_GET_ASPECTS == null
                || METHOD_CRUCIBLE_GET_CATALYST == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] ThaumcraftReflectionHelper: Thaumcraft 反射缓存不完整"
                    + "(未安装或版本不匹配),注魔/坩埚将无法计价");
        }
        if (CTOR_ESSENTIA_STACK == null || METHOD_FROM_ESSENTIA_STACK == null
                || METHOD_ASPECT_LIST_GET_ASPECTS == null || METHOD_ASPECT_LIST_GET_AMOUNT == null) {
            // ThaumicEnergistics 未安装或版本不兼容,源质消耗将返回 null(null = 无法计价)
            AE2Enhanced.LOGGER.warn("[AE2E] ThaumcraftReflectionHelper: ThaumicEnergistics 不可用,"
                    + "源质消耗将返回 null");
        }
    }

    // ---- 通用调用封装 ----

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            throw new IllegalStateException("[AE2E] ThaumcraftReflectionHelper: method not cached");
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[AE2E] ThaumcraftReflectionHelper invoke failed: " + method, e);
        }
    }

    // ---- ThaumcraftApi ----

    public static Object getInfusionRecipe(ItemStack stack) {
        return invoke(METHOD_GET_INFUSION_RECIPE, null, stack);
    }

    public static Object getCrucibleRecipe(ItemStack stack) {
        return invoke(METHOD_GET_CRUCIBLE_RECIPE, null, stack);
    }

    public static Map<?, ?> getCraftingRecipes() {
        return (Map<?, ?>) invoke(METHOD_GET_CRAFTING_RECIPES, null);
    }

    // ---- IPlayerKnowledge ----

    public static Object getKnowledge(EntityPlayer player) {
        return invoke(METHOD_GET_KNOWLEDGE, null, player);
    }

    public static boolean knowledgeAddResearch(Object knowledge, String research) {
        return (Boolean) invoke(METHOD_KNOWLEDGE_ADD_RESEARCH, knowledge, research);
    }

    public static boolean knowledgeRemoveResearch(Object knowledge, String research) {
        return (Boolean) invoke(METHOD_KNOWLEDGE_REMOVE_RESEARCH, knowledge, research);
    }

    public static void knowledgeSetResearchStage(Object knowledge, String research, int stage) {
        invoke(METHOD_KNOWLEDGE_SET_RESEARCH_STAGE, knowledge, research, stage);
    }

    public static void knowledgeSync(Object knowledge, EntityPlayer player) {
        invoke(METHOD_KNOWLEDGE_SYNC, knowledge, player);
    }

    // ---- InfusionRecipe ----

    public static String infusionGetResearch(Object recipe) {
        return (String) invoke(METHOD_INFUSION_GET_RESEARCH, recipe);
    }

    public static Ingredient infusionGetRecipeInput(Object recipe) {
        return (Ingredient) invoke(METHOD_INFUSION_GET_RECIPE_INPUT, recipe);
    }

    @SuppressWarnings("unchecked")
    public static NonNullList<Ingredient> infusionGetComponents(Object recipe) {
        return (NonNullList<Ingredient>) invoke(METHOD_INFUSION_GET_COMPONENTS, recipe);
    }

    public static Object infusionGetRecipeOutput(Object recipe) {
        return invoke(METHOD_INFUSION_GET_RECIPE_OUTPUT, recipe);
    }

    public static Object infusionGetAspects(Object recipe) {
        return invoke(METHOD_INFUSION_GET_ASPECTS, recipe);
    }

    // ---- CrucibleRecipe ----

    public static String crucibleGetResearch(Object recipe) {
        return (String) invoke(METHOD_CRUCIBLE_GET_RESEARCH, recipe);
    }

    public static Ingredient crucibleGetCatalyst(Object recipe) {
        return (Ingredient) invoke(METHOD_CRUCIBLE_GET_CATALYST, recipe);
    }

    public static Object crucibleGetAspects(Object recipe) {
        return invoke(METHOD_CRUCIBLE_GET_ASPECTS, recipe);
    }

    // ---- AspectList ----

    public static int aspectListSize(Object aspects) {
        return (Integer) invoke(METHOD_ASPECT_LIST_SIZE, aspects);
    }

    /**
     * 通过反射创建源质消耗栈，避免在 ThaumicEnergistics 未安装时类加载失败。
     *
     * @return 源质消耗列表；若 ThaumicEnergistics 缺失或任意源质栈无法创建则返回 null
     */
    public static List<IAEStack> createEssentiaCosts(Object aspects, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (CTOR_ESSENTIA_STACK == null || METHOD_FROM_ESSENTIA_STACK == null
                || METHOD_ASPECT_LIST_GET_ASPECTS == null || METHOD_ASPECT_LIST_GET_AMOUNT == null) {
            return null;
        }
        try {
            for (Object aspect : (Object[]) METHOD_ASPECT_LIST_GET_ASPECTS.invoke(aspects)) {
                if (aspect == null) continue;
                int amount = (Integer) METHOD_ASPECT_LIST_GET_AMOUNT.invoke(aspects, aspect);
                if (amount <= 0) continue;
                Object essStack = CTOR_ESSENTIA_STACK.newInstance(aspect, (int) ((long) amount * count));
                Object aeStack = METHOD_FROM_ESSENTIA_STACK.invoke(null, essStack);
                if (aeStack == null) {
                    return null;
                }
                costs.add((IAEStack) aeStack);
            }
        } catch (Exception e) {
            // ThaumicEnergistics 未安装或版本不兼容
            return null;
        }
        return costs;
    }
}
