package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import appeng.api.parts.IPart;
import appeng.blockentity.AEBaseBlockEntity;

import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.core.MemoryCardHandlerRegistry;
import com.github.aeddddd.ae2enhanced.memorycard.handler.ae2.AE2PartHandler;
import com.github.aeddddd.ae2enhanced.memorycard.handler.ae2.AE2TileHandler;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryCardHandlerRegistry} 单元测试.
 *
 * <p>注册表为全局静态状态,所有自定义假 handler 只匹配本类定义的标记接口,
 * 不会干扰同 JVM 内其他测试类.首次 findHandler 会触发 init(),
 * 其中可选 mod 探测依赖 {@link ModList#get()},纯 JUnit 环境下为 null,
 * 因此所有触发点统一用静态 mock 提供"无可选 mod"的 ModList.</p>
 */
class MemoryCardHandlerRegistryTest {

    /** 自定义 handler 的匹配标记. */
    private interface FakeTarget {
    }

    /** 排序验证的匹配标记. */
    private interface OrderingTarget {
    }

    private static class FakeHandler implements IMemoryCardHandler {
        private final String name;

        FakeHandler(String name) {
            this.name = name;
        }

        @Override
        public boolean canHandle(Object target) {
            return target instanceof FakeTarget;
        }

        @Override
        public CompoundTag copy(Object target) {
            return new CompoundTag();
        }

        @Override
        public PasteResult paste(Object target, CompoundTag data, Player player) {
            return PasteResult.SUCCESS;
        }

        @Override
        public String getDisplayName(Object target) {
            return name;
        }
    }

    private static class OrderingHandler implements IMemoryCardHandler {
        private final String name;

        OrderingHandler(String name) {
            this.name = name;
        }

        @Override
        public boolean canHandle(Object target) {
            return target instanceof OrderingTarget;
        }

        @Override
        public CompoundTag copy(Object target) {
            return new CompoundTag();
        }

        @Override
        public PasteResult paste(Object target, CompoundTag data, Player player) {
            return PasteResult.SUCCESS;
        }

        @Override
        public String getDisplayName(Object target) {
            return name;
        }
    }

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    /**
     * 在静态 mock 的 ModList(无可选 mod)下执行动作.
     * init() 只需一次,之后调用不再触碰 ModList,静态 mock 无害.
     */
    private static <T> T withModList(Supplier<T> action) {
        ModList modList = mock(ModList.class);
        when(modList.isLoaded(anyString())).thenReturn(false);
        try (MockedStatic<ModList> mocked = mockStatic(ModList.class)) {
            mocked.when(ModList::get).thenReturn(modList);
            return action.get();
        }
    }

    @Test
    void testFindHandlerReturnsRegisteredCustomHandler() {
        FakeHandler handler = new FakeHandler("custom");
        MemoryCardHandlerRegistry.register(handler);

        IMemoryCardHandler found = withModList(
                () -> MemoryCardHandlerRegistry.findHandler(mock(FakeTarget.class)));
        assertThat(found).isSameAs(handler);
    }

    @Test
    void testFindHandlerReturnsNullForUnknownTarget() {
        IMemoryCardHandler found = withModList(() -> MemoryCardHandlerRegistry.findHandler(new Object()));
        assertThat(found).isNull();
    }

    @Test
    void testDefaultAe2HandlersAreRegistered() {
        assertThat(withModList(() -> MemoryCardHandlerRegistry.findHandler(mock(IPart.class))))
                .isInstanceOf(AE2PartHandler.class);
        assertThat(withModList(() -> MemoryCardHandlerRegistry.findHandler(mock(AEBaseBlockEntity.class))))
                .isInstanceOf(AE2TileHandler.class);
    }

    @Test
    void testFirstMatchingHandlerWins() {
        MemoryCardHandlerRegistry.register(new OrderingHandler("first"));
        MemoryCardHandlerRegistry.register(new OrderingHandler("second"));

        IMemoryCardHandler found = withModList(
                () -> MemoryCardHandlerRegistry.findHandler(mock(OrderingTarget.class)));
        assertThat(found).isNotNull();
        assertThat(found.getDisplayName(new Object())).isEqualTo("first");
    }

    @Test
    void testInitIsIdempotent() {
        // 第一次触发 init,之后重复 init / findHandler 不应再触碰 ModList 也不应抛异常
        withModList(() -> {
            MemoryCardHandlerRegistry.init();
            return null;
        });
        MemoryCardHandlerRegistry.init();
        assertThat(MemoryCardHandlerRegistry.findHandler(mock(IPart.class)))
                .isInstanceOf(AE2PartHandler.class);
    }
}
