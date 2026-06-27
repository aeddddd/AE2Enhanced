package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方注册中心：黑洞合成配方、奇点仪式配方.
 * 工作台合成配方统一通过 assets/ae2enhanced/recipes/*.json 注册.
 */
public final class ModRecipes {

    private ModRecipes() {}

    public static void init() {
        registerSingularityRecipes();
    }

    private static void registerSingularityRecipes() {
        // 系统 A：微型奇点仪式
        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        Block ae2Controller = Block.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "controller"));
        if (ae2Material != null && ae2Controller != null) {
            List<ItemStack> ritualInputs = new ArrayList<>();
            ritualInputs.add(new ItemStack(ae2Material, 64, 47)); // AE2 奇点
            ritualInputs.add(new ItemStack(Items.NETHER_STAR, 4));
            SingularityRecipeRegistry.register(new SingularityRecipe(
                    "micro_singularity_ritual",
                    ritualInputs,
                    new ItemStack(Items.NETHER_STAR),
                    ae2Controller,
                    6000
            ));
        } else {
            AE2Enhanced.LOGGER.warn("无法获取 AE2 材料或控制器方块,微型奇点仪式配方未注册");
        }

        // 系统 B：黑洞合成配方
        registerBlackHoleRecipes();

        // 执行 CraftTweaker 延迟移除
        SingularityRecipeRegistry.applyPendingRemovals();
        BlackHoleRecipeRegistry.applyPendingRemovals();
    }

    private static void registerBlackHoleRecipes() {
        // 测试配方：8 石头 + 1 钻石 → 1 黑曜石
        Map<String, Integer> bhInputs = new HashMap<>();
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Blocks.STONE)), 8);
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.DIAMOND)), 1);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "test_obsidian", bhInputs, new ItemStack(Blocks.OBSIDIAN, 1)));

        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            // 稳态时空流形：16 空间组件 + 64 奇点
            Map<String, Integer> manifoldInputs = new HashMap<>();
            manifoldInputs.put("appliedenergistics2:material:34", 16);
            manifoldInputs.put("appliedenergistics2:material:47", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "stable_spacetime_manifold", manifoldInputs,
                    new ItemStack(ItemRegistry.STABLE_SPACETIME_MANIFOLD, 1)));

            // 微分形式稳定单元：128 奇点 + 16 下界之星
            Map<String, Integer> stabilizerInputs = new HashMap<>();
            stabilizerInputs.put("appliedenergistics2:material:47", 128);
            stabilizerInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.NETHER_STAR)), 16);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "differential_form_stabilizer", stabilizerInputs,
                    new ItemStack(ItemRegistry.DIFFERENTIAL_FORM_STABILIZER, 1)));
        }

        // 共形不变荷：16 稳态时空流形 + 16 微分形式稳定单元
        Map<String, Integer> chargeInputs = new HashMap<>();
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ItemRegistry.STABLE_SPACETIME_MANIFOLD)), 16);
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ItemRegistry.DIFFERENTIAL_FORM_STABILIZER)), 16);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "conformal_invariant_charge", chargeInputs,
                new ItemStack(ItemRegistry.CONFORMAL_CHARGE, 1)));

        // 智能空白样板：64 空白样板
        if (ae2Material != null) {
            Map<String, Integer> smartPatternInputs = new HashMap<>();
            smartPatternInputs.put("appliedenergistics2:material:52", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "smart_blank_pattern", smartPatternInputs,
                    new ItemStack(ItemRegistry.SMART_BLANK_PATTERN, 1)));
        }

        // 虚拟并行卡（Tier 1）：1024 加速卡 + 1024 容量卡 + 64 奇点
        if (ae2Material != null && ItemRegistry.VIRTUAL_PARALLEL_CARD != null) {
            Map<String, Integer> vpcInputs = new HashMap<>();
            vpcInputs.put("appliedenergistics2:material:30", 1024);
            vpcInputs.put("appliedenergistics2:material:27", 1024);
            vpcInputs.put("appliedenergistics2:material:47", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "virtual_parallel_card_tier1", vpcInputs,
                    ItemVirtualParallelCard.createStack(0)));

            // Tier 2~6：16 张低一级卡片合成一张高一级卡片
            for (int tier = 1; tier <= 5; tier++) {
                Map<String, Integer> upgradeInputs = new HashMap<>();
                upgradeInputs.put(BlackHoleRecipe.keyOf(ItemVirtualParallelCard.createStack(tier - 1)), 16);
                BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                        "virtual_parallel_card_tier" + (tier + 1), upgradeInputs,
                        ItemVirtualParallelCard.createStack(tier)));
            }
        }
    }
}
