package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.lang.reflect.Method;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 通用内存卡测试共享支撑:原版引导 + 物品注册表解冻 + 内存卡物品构造.
 * 与 omnitool 测试同一模式(见 OmniToolTestSupport).
 */
final class UMCTestSupport {

    private static final ResourceLocation TEST_ITEM_ID = new ResourceLocation("ae2enhanced", "test_umc");

    private static volatile boolean bootstrapped;
    private static UniversalMemoryCardItem cardItem;

    private UMCTestSupport() {
    }

    /**
     * 完成全部测试环境引导.幂等,可安全重复调用.
     */
    static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        synchronized (UMCTestSupport.class) {
            if (bootstrapped) {
                return;
            }
            MinecraftTestBootstrap.bootstrap();
            unfreezeItemRegistry();
            cardItem = new UniversalMemoryCardItem(new Item.Properties());
            ForgeRegistries.ITEMS.register(TEST_ITEM_ID, cardItem);
            bootstrapped = true;
        }
    }

    /**
     * 构造一个全新的通用内存卡物品堆.
     */
    static ItemStack newCardStack() {
        return new ItemStack(cardItem);
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
