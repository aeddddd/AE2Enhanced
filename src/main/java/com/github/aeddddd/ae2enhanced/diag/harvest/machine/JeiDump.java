package com.github.aeddddd.ae2enhanced.diag.harvest.machine;

import java.util.List;
import java.util.concurrent.FutureTask;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.Internal;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.ingredients.Ingredients;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;

/**
 * 通用 JEI/HEI 配方注册表 dump（保底覆盖全部 JEI 可见机器配方）.
 * <p>逐 mod 手写适配器永远追不上整合包的 mod 数;所有带 JEI 集成的机器配方
 * 都以统一的 slot→候选 形式躺在 {@link IRecipeRegistry} 里,本适配器整表采集,
 * 包括流体成分(建模为合成物品键 {@code ae2e_harvest:fluid/<name>},count=mB)。
 * 只排除原版合成台/熔炉(已有专段)与被专用适配器完整覆盖的 mod
 * (ExtendedCrafting/Thaumcraft/ModularMachinery/DraconicEvolution——这些的
 * 概率/数量语义以专用适配器为准);NC/TE/EIO/TR/AA 等经本适配器再采一遍
 * (补流体与专用适配器跳过的配方),重复条目由下游 canonicalize 去重。</p>
 * <p>JEI 注册表只在客户端存在;本类引用客户端与 JEI 类,仅由 {@link MachineDumps}
 * 在 jei 已加载且为物理客户端时经 Class.forName 加载。采集必须切到客户端线程
 * (整合式服务器的命令执行在服务端线程,直接读注册表有并发风险)。</p>
 * <p>已知口径损失:JEI 的 {@link IIngredients} 不带概率信息,概率副产物一律按
 * 确定产出建模(高估可得性,优于丢配方);能量/耗时等无法从 wrapper 统一取得,不记。</p>
 */
public final class JeiDump {

    /** 原版已由 crafting/furnace 专段覆盖的 category uid. */
    private static final String[] SKIP_EXACT = {
            "minecraft.crafting", "minecraft.smelting"
    };
    /** 被专用适配器完整覆盖的 mod(category uid 前缀,兼容 . 与 : 两种分隔). */
    private static final String[] SKIP_PREFIX_MOD = {
            "extendedcrafting", "thaumcraft", "modularmachinery", "draconicevolution"
    };

    private JeiDump() {
    }

    public static void dump(HarvestSnapshot snapshot) {
        try {
            FutureTask<Integer> task = new FutureTask<>(() -> dumpOnClientThread(snapshot));
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.isCallingFromMinecraftThread()) {
                task.run();
            } else {
                mc.addScheduledTask(task);
            }
            int count = task.get();
            AE2Enhanced.LOGGER.info("[harvest] jei dump 完成: {} 条", count);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[harvest] jei dump 失败: {}", t.toString());
        }
    }

    private static int dumpOnClientThread(HarvestSnapshot snapshot) {
        if (Internal.getRuntime() == null) {
            AE2Enhanced.LOGGER.warn("[harvest] jei runtime 未就绪,跳过");
            return 0;
        }
        IRecipeRegistry registry = Internal.getRuntime().getRecipeRegistry();
        int total = 0;
        for (IRecipeCategory<?> category : registry.getRecipeCategories()) {
            try {
                total += dumpCategory(snapshot, registry, category);
            } catch (Throwable t) {
                // 单个 category 失败跳过
            }
        }
        return total;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int dumpCategory(HarvestSnapshot snapshot, IRecipeRegistry registry, IRecipeCategory category) {
        String uid = category.getUid();
        if (uid == null || skipped(uid)) {
            return 0;
        }
        List wrappers;
        try {
            wrappers = registry.getRecipeWrappers(category);
        } catch (Throwable t) {
            return 0;
        }
        int added = 0;
        for (Object wrapperObj : wrappers) {
            try {
                MachineEntry entry = convert(uid, (IRecipeWrapper) wrapperObj);
                if (entry != null) {
                    snapshot.machines.add(entry);
                    added++;
                }
            } catch (Throwable t) {
                // 单条失败跳过
            }
        }
        return added;
    }

    private static MachineEntry convert(String uid, IRecipeWrapper wrapper) {
        IIngredients ingredients = new Ingredients();
        try {
            wrapper.getIngredients(ingredients);
        } catch (Throwable t) {
            return null; // wrapper 不提供静态成分(动态配方)
        }
        MachineEntry entry = new MachineEntry();
        entry.mod = modOf(uid);
        entry.machine = uid;
        entry.type = "jei";
        entry.name = wrapper.getClass().getName();
        for (List<ItemStack> slot : ingredients.getInputs(VanillaTypes.ITEM)) {
            List<StackRef> refs = new java.util.ArrayList<>();
            for (ItemStack stack : slot) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    refs.add(ref);
                }
            }
            entry.inputs.add(refs);
        }
        for (List<FluidStack> slot : ingredients.getInputs(VanillaTypes.FLUID)) {
            List<StackRef> refs = new java.util.ArrayList<>();
            for (FluidStack fluid : slot) {
                StackRef ref = fluidRef(fluid);
                if (ref != null) {
                    refs.add(ref);
                }
            }
            entry.inputs.add(refs);
        }
        for (List<ItemStack> slot : ingredients.getOutputs(VanillaTypes.ITEM)) {
            for (ItemStack stack : slot) {
                StackRef ref = RecipeHarvester.stackRef(stack);
                if (ref != null) {
                    entry.outputs.add(ref);
                    break; // 输出槽只取首候选(JEI 输出槽基本单候选)
                }
            }
        }
        for (List<FluidStack> slot : ingredients.getOutputs(VanillaTypes.FLUID)) {
            for (FluidStack fluid : slot) {
                StackRef ref = fluidRef(fluid);
                if (ref != null) {
                    entry.outputs.add(ref);
                    break;
                }
            }
        }
        // 无输入 = 无中生有的伪源(信息页/掉落表),无产出 = 纯展示;均不收
        boolean anyInput = false;
        for (List<StackRef> slot : entry.inputs) {
            if (!slot.isEmpty()) {
                anyInput = true;
                break;
            }
        }
        if (!anyInput || entry.outputs.isEmpty()) {
            return null;
        }
        return entry;
    }

    /** FluidStack → 合成物品键 {@code ae2e_harvest:fluid/<name>}(count=mB;流体 NBT 忽略). */
    private static StackRef fluidRef(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null || fluid.getFluid().getName() == null) {
            return null;
        }
        StackRef ref = new StackRef();
        ref.id = "ae2e_harvest:fluid/" + sanitize(fluid.getFluid().getName());
        ref.meta = 0;
        ref.count = Math.max(1, fluid.amount);
        return ref;
    }

    /** 流体名 → ResourceLocation path 合法字符. */
    private static String sanitize(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                    || c == '_' || c == '-' || c == '.' || c == '/';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }

    private static boolean skipped(String uid) {
        for (String exact : SKIP_EXACT) {
            if (exact.equals(uid)) {
                return true;
            }
        }
        for (String mod : SKIP_PREFIX_MOD) {
            if (uid.startsWith(mod + ".") || uid.startsWith(mod + ":")) {
                return true;
            }
        }
        return false;
    }

    /** uid → 所属 mod 的分组名(取 . 或 : 前缀,无法判断时原样返回). */
    private static String modOf(String uid) {
        int dot = uid.indexOf('.');
        int colon = uid.indexOf(':');
        int cut = dot < 0 ? colon : colon < 0 ? dot : Math.min(dot, colon);
        return cut > 0 ? uid.substring(0, cut) : uid;
    }
}
