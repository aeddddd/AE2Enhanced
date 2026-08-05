package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipe;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * event 包测试共享工装:原版/配置引导 + 仪式配方类型注入 + 测试用全能工具注册.
 * <p>配方类型的 {@code RegistryObject} 只在游戏注册事件中填充,测试环境注入测试实例;
 * 测试物品需注册进物品注册表,否则 Forge 补丁后的 {@code ItemStack} 构造器
 * 会因缺少 registry delegate 抛异常.</p>
 */
final class EventTestFixtures {

    private static final ResourceLocation TEST_TOOL_ID = new ResourceLocation("ae2enhanced", "test_event_omni_tool");

    private static volatile boolean bootstrapped;
    private static AdvancedMEOmniToolItem toolItem;

    private EventTestFixtures() {
    }

    /** 完成全部测试环境引导.幂等,可安全重复调用. */
    static void init() {
        if (bootstrapped) {
            return;
        }
        synchronized (EventTestFixtures.class) {
            if (bootstrapped) {
                return;
            }
            MinecraftTestBootstrap.bootstrap();
            ConfigTestBootstrap.loadDefaults();
            RegistryObjectTestInjector.inject(ModRecipes.SINGULARITY_RITUAL_TYPE,
                    new RecipeType<SingularityRitualRecipe>() {
                    });
            toolItem = new AdvancedMEOmniToolItem(new Item.Properties());
            ForgeRegistries.ITEMS.register(TEST_TOOL_ID, toolItem);
            bootstrapped = true;
        }
    }

    /** 返回测试用全能工具实例(已注册,共享同一实例). */
    static AdvancedMEOmniToolItem toolItem() {
        return toolItem;
    }

    /** 构造一个全新的全能工具物品堆. */
    static ItemStack newToolStack() {
        return new ItemStack(toolItem);
    }
}
