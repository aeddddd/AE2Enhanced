package com.github.aeddddd.ae2enhanced.chamber;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.api.features.IGrinderRecipe;
import appeng.api.features.IInscriberRecipe;
import appeng.api.features.InscriberProcessType;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 奇点处理仓配方索引：汇总黑洞配方、压印链（含多步合一）、水晶充能、
 * 福鲁伊克斯聚合、水晶种子生长与磨粉机配方.
 *
 * <p>构建时机：首次使用惰性构建（保证 cells 等 mod 在 init 注册的压印配方已就位）;
 * CraftTweaker 修改配方后通过 {@link #markDirty()} 触发重建.</p>
 *
 * <p>磨粉机注册表随矿辞动态增长,采用按 key 惰性查询 + 缓存,不做静态展开.</p>
 */
public final class ChamberRecipeIndex {

    /** 各配方类别的基准处理时间（沿用 AE2 原版基准） */
    public static final int TIME_BLACK_HOLE = 20;
    public static final int TIME_INSCRIBE = 100;
    public static final int TIME_PRESS = 100;
    public static final int TIME_CHAIN = 200;
    public static final int TIME_CHARGER = 100;
    public static final int TIME_FLUIX = 60;
    public static final int TIME_SEED = 600;
    public static final int TIME_GRINDER_PER_TURN = 10;

    private static final List<ChamberRecipe> generated = new ArrayList<>();
    private static final List<ChamberRecipe> custom = new ArrayList<>();
    private static final Map<String, List<ChamberRecipe>> byInputKey = new HashMap<>();
    private static final Map<String, ChamberRecipe> grinderCache = new HashMap<>();
    private static boolean built = false;

    private ChamberRecipeIndex() {
    }

    public static void markDirty() {
        built = false;
    }

    public static synchronized void ensureBuilt() {
        if (!built) {
            built = true;
            rebuild();
        }
    }

    /**
     * 按输入 key 查询候选配方（含磨粉机惰性查询）.
     * 返回顺序即处理优先级：黑洞 > 合并链 > 单步压印 > 硬编码（充能/聚合/种子）> 磨粉 > CT 自定义.
     */
    public static List<ChamberRecipe> recipesForInput(String key, ItemStack template) {
        ensureBuilt();
        List<ChamberRecipe> result = byInputKey.get(key);
        if (result == null && !grinderCache.containsKey(key)) {
            ChamberRecipe grinder = queryGrinder(key, template);
            grinderCache.put(key, grinder);
            if (grinder != null) {
                index(grinder);
                result = byInputKey.get(key);
            }
        }
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 插入过滤：只有能参与某条配方的物品才允许进入处理仓
     *（ExtendedAE 的 BaseFilter 思路：不让无关物品占用缓存槽）.
     */
    public static boolean isValidInput(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String key = LongItemStore.keyOf(stack);
        return !recipesForInput(key, stack).isEmpty();
    }

    public static List<ChamberRecipe> allRecipes() {
        ensureBuilt();
        List<ChamberRecipe> all = new ArrayList<>(generated.size() + custom.size());
        all.addAll(generated);
        all.addAll(custom);
        return Collections.unmodifiableList(all);
    }

    // ---- CraftTweaker 自定义配方 ----

    public static void addCustomRecipe(ChamberRecipe recipe) {
        ensureBuilt();
        custom.removeIf(r -> r.getId().equals(recipe.getId()));
        custom.add(recipe);
        index(recipe);
    }

    public static boolean removeCustomRecipe(String id) {
        ensureBuilt();
        boolean removed = custom.removeIf(r -> r.getId().equals(id));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    // ---- 构建 ----

    private static synchronized void rebuild() {
        generated.clear();
        byInputKey.clear();
        grinderCache.clear();

        buildBlackHole();
        buildInscriberChains();
        buildHardcoded();

        for (ChamberRecipe r : generated) {
            index(r);
        }
        for (ChamberRecipe r : custom) {
            index(r);
        }
    }

    private static void index(ChamberRecipe recipe) {
        for (ChamberRecipe.InputGroup group : recipe.getInputGroups()) {
            for (String key : group.getKeys()) {
                byInputKey.computeIfAbsent(key, k -> new ArrayList<>()).add(recipe);
            }
        }
    }

    // ---- 黑洞配方 ----

    private static void buildBlackHole() {
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            ChamberRecipe.Builder builder = ChamberRecipe.builder("blackhole:" + recipe.getId())
                    .output(recipe.getOutput())
                    .time(TIME_BLACK_HOLE);
            for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
                // 使用原始 key 直传,NBT 感知输入不会因模板重建而失配
                builder.inputRawKey(entry.getKey(), entry.getValue(), parseKeyTemplate(entry.getKey()));
            }
            generated.add(builder.build());
        }
    }

    /**
     * 从 key（"registry:meta[+NBT mojangson]"）还原显示用模板物品.
     */
    private static ItemStack parseKeyTemplate(String key) {
        int nbtStart = key.indexOf('{');
        String base = nbtStart >= 0 ? key.substring(0, nbtStart) : key;
        int lastColon = base.lastIndexOf(':');
        if (lastColon < 0) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation name = new ResourceLocation(base.substring(0, lastColon));
            int meta = Integer.parseInt(base.substring(lastColon + 1));
            Item item = Item.REGISTRY.getObject(name);
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, 1, meta);
            if (nbtStart >= 0) {
                try {
                    NBTTagCompound tag = JsonToNBT.getTagFromJson(key.substring(nbtStart));
                    stack.setTagCompound(tag);
                } catch (Exception ignored) {
                    // NBT 解析失败时退化为无 NBT 模板
                }
            }
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ---- 压印器（含多步合一） ----

    /**
     * 读取压印注册表并按"输出物品"聚合生成配方.
     *
     * <p>AE2 的 JSON 加载器会把矿辞展开成整条替代列表,且多个 mod 可能对同一输出
     * 重复注册配方（如凿子为赛特斯石英注册大量变体）.为避免 JEI 配方页被刷屏,
     * 这里按输出合并：同一输出的所有输入并入同一替代组,一个输出只产生一条配方
     * （JEI 槽位内循环展示替代品）.</p>
     */
    private static void buildInscriberChains() {
        List<IInscriberRecipe> all;
        try {
            all = new ArrayList<>(AEApi.instance().registries().inscriber().getRecipes());
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to read inscriber registry", t);
            return;
        }

        // 1) INSCRIBE 按输出聚合：输出 key -> 中间物替代列表
        Map<String, ItemStack> inscribeOut = new LinkedHashMap<>();
        Map<String, List<ItemStack>> inscribeMid = new LinkedHashMap<>();
        for (IInscriberRecipe recipe : all) {
            if (recipe.getProcessType() != InscriberProcessType.INSCRIBE || recipe.getOutput().isEmpty()) {
                continue;
            }
            String outKey = LongItemStore.keyOf(recipe.getOutput());
            inscribeOut.putIfAbsent(outKey, recipe.getOutput());
            mergeStacks(inscribeMid.computeIfAbsent(outKey, k -> new ArrayList<>()),
                    nonEmpty(recipe.getInputs()));
        }

        // 2) PRESS 按输出聚合,并尝试链式合并（上/下消耗物均有 INSCRIBE 来源时）
        Map<String, ItemStack> pressOut = new LinkedHashMap<>();
        Map<String, List<ItemStack>> pressTop = new LinkedHashMap<>();
        Map<String, List<ItemStack>> pressMid = new LinkedHashMap<>();
        Map<String, List<ItemStack>> pressBot = new LinkedHashMap<>();
        Map<String, ItemStack> chainOut = new LinkedHashMap<>();
        Map<String, List<ItemStack>> chainMid = new LinkedHashMap<>();
        Map<String, List<ItemStack>> chainTopSrc = new LinkedHashMap<>();
        Map<String, List<ItemStack>> chainBotSrc = new LinkedHashMap<>();
        for (IInscriberRecipe recipe : all) {
            if (recipe.getProcessType() != InscriberProcessType.PRESS || recipe.getOutput().isEmpty()) {
                continue;
            }
            String outKey = LongItemStore.keyOf(recipe.getOutput());
            pressOut.putIfAbsent(outKey, recipe.getOutput());
            mergeStacks(pressMid.computeIfAbsent(outKey, k -> new ArrayList<>()), nonEmpty(recipe.getInputs()));
            mergeStacks(pressTop.computeIfAbsent(outKey, k -> new ArrayList<>()), nonEmpty(recipe.getTopInputs()));
            mergeStacks(pressBot.computeIfAbsent(outKey, k -> new ArrayList<>()), nonEmpty(recipe.getBottomInputs()));

            // 链式合并：任一上消耗物 + 任一下消耗物都能找到 INSCRIBE 来源即可成链
            List<ItemStack> topSrcMids = null;
            for (ItemStack top : nonEmpty(recipe.getTopInputs())) {
                List<ItemStack> mids = inscribeMid.get(LongItemStore.keyOf(top));
                if (mids != null) {
                    topSrcMids = mids;
                    break;
                }
            }
            List<ItemStack> botSrcMids = null;
            for (ItemStack bottom : nonEmpty(recipe.getBottomInputs())) {
                List<ItemStack> mids = inscribeMid.get(LongItemStore.keyOf(bottom));
                if (mids != null) {
                    botSrcMids = mids;
                    break;
                }
            }
            if (topSrcMids != null && botSrcMids != null) {
                chainOut.putIfAbsent(outKey, recipe.getOutput());
                mergeStacks(chainMid.computeIfAbsent(outKey, k -> new ArrayList<>()), nonEmpty(recipe.getInputs()));
                mergeStacks(chainTopSrc.computeIfAbsent(outKey, k -> new ArrayList<>()), topSrcMids);
                mergeStacks(chainBotSrc.computeIfAbsent(outKey, k -> new ArrayList<>()), botSrcMids);
            }
        }

        // 3) 生成：优先级 合并链 > 单步 PRESS > 单步 INSCRIBE（同一原料进入最深加工链）
        int n = 0;
        for (Map.Entry<String, ItemStack> entry : chainOut.entrySet()) {
            String outKey = entry.getKey();
            generated.add(ChamberRecipe.builder("chain:" + (n++))
                    .inputAlternatives(chainMid.get(outKey), 1)
                    .inputAlternatives(chainTopSrc.get(outKey), 1)
                    .inputAlternatives(chainBotSrc.get(outKey), 1)
                    .output(entry.getValue())
                    .time(TIME_CHAIN)
                    .build());
        }
        for (Map.Entry<String, ItemStack> entry : pressOut.entrySet()) {
            String outKey = entry.getKey();
            generated.add(ChamberRecipe.builder("press:" + (n++))
                    .inputAlternatives(pressTop.get(outKey), 1)
                    .inputAlternatives(pressMid.get(outKey), 1)
                    .inputAlternatives(pressBot.get(outKey), 1)
                    .output(entry.getValue())
                    .time(TIME_PRESS)
                    .build());
        }
        for (Map.Entry<String, ItemStack> entry : inscribeOut.entrySet()) {
            generated.add(ChamberRecipe.builder("inscribe:" + (n++))
                    .inputAlternatives(inscribeMid.get(entry.getKey()), 1)
                    .output(entry.getValue())
                    .time(TIME_INSCRIBE)
                    .build());
        }
    }

    /** 将 stacks 并入替代列表,按 key 去重. */
    private static void mergeStacks(List<ItemStack> target, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            String key = LongItemStore.keyOf(stack);
            boolean exists = false;
            for (ItemStack existing : target) {
                if (LongItemStore.keyOf(existing).equals(key)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                target.add(stack);
            }
        }
    }

    private static List<ItemStack> nonEmpty(List<ItemStack> list) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack s : list) {
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    // ---- 硬编码配方：充能 / 聚合 / 种子 ----

    private static void buildHardcoded() {
        try {
            // 水晶充能：赛特斯石英 -> 充能赛特斯石英（TileCharger 硬编码转化）
            Optional<ItemStack> certus = stack(AEApi.instance().definitions().materials().certusQuartzCrystal());
            Optional<ItemStack> charged = stack(AEApi.instance().definitions().materials().certusQuartzCrystalCharged());
            if (certus.isPresent() && charged.isPresent()) {
                generated.add(ChamberRecipe.builder("charger:certus")
                        .input(certus.get(), 1)
                        .output(charged.get())
                        .time(TIME_CHARGER)
                        .build());
            }

            // 福鲁伊克斯聚合：充能赛特斯 + 红石 + 下界石英 -> 2 福鲁伊克斯（EntityChargedQuartz 硬编码）
            Optional<ItemStack> fluix = stack(AEApi.instance().definitions().materials().fluixCrystal());
            if (charged.isPresent() && fluix.isPresent()) {
                ItemStack out = fluix.get();
                out.setCount(2);
                generated.add(ChamberRecipe.builder("aggregate:fluix")
                        .input(charged.get(), 1)
                        .input(new ItemStack(Items.REDSTONE), 1)
                        .input(new ItemStack(Items.QUARTZ), 1)
                        .output(out)
                        .time(TIME_FLUIX)
                        .build());
            }

            // 水晶种子生长：三种种子 -> 高纯水晶（EntityGrowingCrystal 硬编码,机器内压缩为固定时长）
            Item seedItem = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "crystal_seed"));
            if (seedItem != null) {
                addSeedRecipe(seedItem, 0, stack(AEApi.instance().definitions().materials().purifiedCertusQuartzCrystal()), "certus");
                addSeedRecipe(seedItem, 600, stack(AEApi.instance().definitions().materials().purifiedNetherQuartzCrystal()), "nether");
                addSeedRecipe(seedItem, 1200, stack(AEApi.instance().definitions().materials().purifiedFluixCrystal()), "fluix");
            }
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to build hardcoded chamber recipes", t);
        }
    }

    private static void addSeedRecipe(Item seedItem, int meta, Optional<ItemStack> purified, String name) {
        if (!purified.isPresent()) {
            return;
        }
        generated.add(ChamberRecipe.builder("seed:" + name)
                .input(new ItemStack(seedItem, 1, meta), 1)
                .output(purified.get())
                .time(TIME_SEED)
                .build());
    }

    private static Optional<ItemStack> stack(IItemDefinition def) {
        return def.maybeStack(1);
    }

    // ---- 磨粉机（惰性查询 + 缓存） ----

    private static ChamberRecipe queryGrinder(String key, ItemStack template) {
        if (template.isEmpty()) {
            return null;
        }
        try {
            IGrinderRecipe recipe = AEApi.instance().registries().grinder().getRecipeForInput(template);
            if (recipe == null || recipe.getOutput().isEmpty()) {
                return null;
            }
            return ChamberRecipe.builder("grinder:" + key)
                    .input(template, 1)
                    .output(recipe.getOutput())
                    .time(recipe.getRequiredTurns() * TIME_GRINDER_PER_TURN)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }
}
