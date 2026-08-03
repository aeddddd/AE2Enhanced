package com.github.aeddddd.ae2enhanced.test.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.VanillaIngredientSerializer;

import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipeSerializer;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipe;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipeSerializer;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipe;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipeSerializer;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.item.ConformalInvariantChargeItem;
import com.github.aeddddd.ae2enhanced.item.MicroSingularityItem;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.testutil.AE2ItemTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.ForgeConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;

/**
 * crafting 包测试共享工装:把配方序列化器/配方类型/模组物品/方块的测试实例
 * 注入 {@link ModRecipes}、{@link ModItems}、{@link ModBlocks} 的 RegistryObject.
 * <p>同时完成两件环境补全:
 * <ol>
 * <li>把测试物品注册进物品注册表,否则 Forge 补丁后的 ItemStack 构造器
 * 会因缺少 registry delegate 抛异常;</li>
 * <li>向 {@link CraftingHelper} 注册原版物品型 ingredient 序列化器,
 * 游戏内由 ForgeMod 完成,测试环境缺失时 Ingredient.fromJson 会报
 * {@code Unknown ingredient type: minecraft:item}.</li>
 * </ol></p>
 */
final class CraftingTestFixtures {

    /** 燃料配方类型(测试实例). */
    static final RecipeType<SingularityFuelRecipe> FUEL_TYPE = new RecipeType<>() {
    };
    /** 仪式配方类型(测试实例). */
    static final RecipeType<SingularityRitualRecipe> RITUAL_TYPE = new RecipeType<>() {
    };
    /** 先进 ME 全能工具(测试实例). */
    static final AdvancedMEOmniToolItem OMNI_TOOL = new AdvancedMEOmniToolItem(new Item.Properties().stacksTo(1));
    /** 共形不变荷(测试实例). */
    static final ConformalInvariantChargeItem CONFORMAL_CHARGE =
            new ConformalInvariantChargeItem(new Item.Properties());
    /** 被约束微型奇点物品(测试实例). */
    static final MicroSingularityItem MICRO_SINGULARITY_ITEM =
            new MicroSingularityItem(new Item.Properties().stacksTo(1));
    /** 微型奇点方块(测试实例). */
    static final Block MICRO_SINGULARITY_BLOCK = new Block(BlockBehaviour.Properties.of());

    static {
        MinecraftTestBootstrap.bootstrap();
        ForgeConfigTestBootstrap.bootstrap();

        RegistryObjectTestInjector.inject(ModRecipes.SINGULARITY_FUEL_SERIALIZER,
                new SingularityFuelRecipeSerializer());
        RegistryObjectTestInjector.inject(ModRecipes.SINGULARITY_FUEL_TYPE, FUEL_TYPE);
        RegistryObjectTestInjector.inject(ModRecipes.SINGULARITY_RITUAL_SERIALIZER,
                new SingularityRitualRecipeSerializer());
        RegistryObjectTestInjector.inject(ModRecipes.SINGULARITY_RITUAL_TYPE, RITUAL_TYPE);
        RegistryObjectTestInjector.inject(ModRecipes.OMNI_TOOL_UPGRADE_SERIALIZER,
                new OmniToolUpgradeRecipeSerializer());

        RegistryObjectTestInjector.inject(ModItems.ME_OMNI_TOOL, OMNI_TOOL);
        RegistryObjectTestInjector.inject(ModItems.CONFORMAL_INVARIANT_CHARGE, CONFORMAL_CHARGE);
        RegistryObjectTestInjector.inject(ModItems.MICRO_SINGULARITY, MICRO_SINGULARITY_ITEM);
        RegistryObjectTestInjector.inject(ModBlocks.MICRO_SINGULARITY, MICRO_SINGULARITY_BLOCK);

        // 注册测试物品实例,ItemStack 构造器需要 registry delegate
        AE2ItemTestBootstrap.registerTestItem(ModItems.ME_OMNI_TOOL, OMNI_TOOL);
        AE2ItemTestBootstrap.registerTestItem(ModItems.CONFORMAL_INVARIANT_CHARGE, CONFORMAL_CHARGE);
        AE2ItemTestBootstrap.registerTestItem(ModItems.MICRO_SINGULARITY, MICRO_SINGULARITY_ITEM);

        // 原版物品型 ingredient 序列化器,CraftingHelper 静态表由 ForgeMod 在游戏内填充
        if (CraftingHelper.getID(VanillaIngredientSerializer.INSTANCE) == null) {
            CraftingHelper.register(new ResourceLocation("minecraft", "item"),
                    VanillaIngredientSerializer.INSTANCE);
        }
    }

    private CraftingTestFixtures() {
    }

    /** 确保静态注入已执行. */
    static void init() {
        // 触发类加载即可
    }
}
