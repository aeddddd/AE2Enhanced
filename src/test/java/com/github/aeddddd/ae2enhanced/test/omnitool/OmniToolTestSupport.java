package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.lang.reflect.Method;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * omnitool 测试共享支撑:原版引导 + 配置默认值 + 物品注册表解冻 + 工具物品构造.
 * <p>原版引导结束后内置注册表处于冻结状态,此时 {@code new Item(...)} 会在
 * Forge 的注册表包装层抛出 {@code IllegalStateException: Registry is already frozen}.
 * 该包装类为包私有,这里通过反射调用其公开的 {@code unfreeze()} 解冻物品注册表,
 * 使测试可以自由构造 {@link AdvancedMEOmniToolItem} 实例.</p>
 */
final class OmniToolTestSupport {

    private static final ResourceLocation TEST_ITEM_ID = new ResourceLocation("ae2enhanced", "test_omni_tool");

    private static volatile boolean bootstrapped;
    private static AdvancedMEOmniToolItem toolItem;

    private OmniToolTestSupport() {
    }

    /**
     * 完成全部测试环境引导.幂等,可安全重复调用.
     * <p>除原版/配置引导外,还会解冻物品注册表并把测试用全能工具实例注册进去:
     * Forge 补丁后的 {@code ItemStack} 构造器要求物品在 {@code ForgeRegistries.ITEMS}
     * 中存在 registry delegate,未注册的物品会直接抛异常.</p>
     */
    static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (OmniToolTestSupport.class) {
            if (bootstrapped) {
                return;
            }
            MinecraftTestBootstrap.bootstrap();
            ConfigTestBootstrap.loadDefaults();
            unfreezeItemRegistry();
            toolItem = new AdvancedMEOmniToolItem(new Item.Properties());
            ForgeRegistries.ITEMS.register(TEST_ITEM_ID, toolItem);
            bootstrapped = true;
        }
    }

    /**
     * 返回测试用全能工具实例(已注册,共享同一实例).
     */
    static AdvancedMEOmniToolItem newToolItem() {
        return toolItem;
    }

    /**
     * 构造一个全新的全能工具物品堆.
     */
    static ItemStack newToolStack() {
        return new ItemStack(toolItem);
    }

    private static void unfreezeItemRegistry() {
        try {
            Method unfreeze = BuiltInRegistries.ITEM.getClass().getMethod("unfreeze");
            unfreeze.setAccessible(true);
            unfreeze.invoke(BuiltInRegistries.ITEM);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法在测试环境中解冻物品注册表", e);
        }
    }
}
