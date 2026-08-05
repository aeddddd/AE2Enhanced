package com.github.aeddddd.ae2enhanced.test.memorycard;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.github.aeddddd.ae2enhanced.memorycard.upgrade.MekanismUpgradeProvider;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link MekanismUpgradeProvider} 单元测试.
 *
 * <p>Mekanism 不在测试 classpath 上,{@code MekanismReflectionHelper} 反射点全部不可用,
 * 这里验证降级行为:零槽位、读取返回空、写入/清空静默忽略且不抛异常.</p>
 */
class MekanismUpgradeProviderTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testDegradedProviderBehavesAsEmpty() {
        MekanismUpgradeProvider provider = new MekanismUpgradeProvider(new Object(), new Object());

        assertThat(provider.getSlotCount()).isZero();
        assertThat(provider.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(provider.getStackInSlot(99).isEmpty()).isTrue();
    }

    @Test
    void testDegradedWritesAreSilentlyIgnored() {
        MekanismUpgradeProvider provider = new MekanismUpgradeProvider(new Object(), new Object());

        assertThatCode(() -> {
            provider.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            provider.clearSlots();
        }).doesNotThrowAnyException();
    }
}
