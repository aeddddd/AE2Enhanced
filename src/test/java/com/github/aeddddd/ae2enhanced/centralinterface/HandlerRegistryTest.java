package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraftforge.fml.common.Loader;

/**
 * {@link HandlerRegistry} 注册与查找语义测试。
 *
 * <p>无头环境下 {@link Loader#isModLoaded(String)} 会因 {@code namedMods == null} 抛 NPE
 * （{@code Loader()} 构造函数又依赖 FML 运行时数据，无法直接实例化），
 * 故在 {@link #stubLoader()} 中以 Unsafe 分配 Loader 实例并注入空 namedMods，
 * 使所有 mod 门控判定为 false，符合"测试环境无 mod"的预期。</p>
 */
public class HandlerRegistryTest {

    @BeforeAll
    public static void stubLoader() throws Exception {
        Field instanceField = Loader.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object loader = instanceField.get(null);
        if (loader == null) {
            // 跳过依赖 FML 运行时的构造函数
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            loader = unsafe.allocateInstance(Loader.class);
            instanceField.set(null, loader);
        }
        // isModLoaded 先查 namedMods，空表即短路返回 false，无需 modController
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(loader) == null) {
            namedMods.set(loader, new HashMap<String, Object>());
        }
    }

    private HandlerRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new HandlerRegistry();
    }

    /** 测试环境无任何 mod：init 只注册 DefaultSingleBatchHandler 兜底，且重复 init 幂等。 */
    @Test
    public void testInitRegistersOnlyDefaultFallback() {
        registry.initialize();

        List<IRemoteHandler> handlers = registry.handlers();
        assertThat(handlers).hasSize(1);
        assertThat(handlers.get(0)).isInstanceOf(DefaultSingleBatchHandler.class);

        registry.initialize();
        assertThat(registry.handlers()).hasSize(1);
    }

    /** 未知 blockId 返回兜底 handler（HANDLERS.get(0)，即 DefaultSingleBatchHandler）。 */
    @Test
    public void testFindHandlerFallbackForUnknownBlockId() {
        IRemoteHandler found = registry.find("minecraft:furnace");

        assertThat(found).isInstanceOf(DefaultSingleBatchHandler.class);
        assertThat(found).isSameAs(registry.handlers().get(0));
        assertThat(found.canHandle("minecraft:furnace")).isFalse();
    }

    /** 注入 fake handler 后，匹配的 blockId 优先返回 fake；不匹配仍回退到兜底。 */
    @Test
    public void testRegisterFakeHandlerPriority() {
        IRemoteHandler fake = mock(IRemoteHandler.class);
        when(fake.canHandle("test:machine")).thenReturn(true);
        registry.register(fake);

        assertThat(registry.find("test:machine")).isSameAs(fake);
        assertThat(registry.find("test:unknown")).isInstanceOf(DefaultSingleBatchHandler.class);
    }

    /** 多个 handler 同时匹配时，按注册顺序先注册者优先。 */
    @Test
    public void testFirstRegisteredWinsOnMultipleMatches() {
        IRemoteHandler first = mock(IRemoteHandler.class);
        IRemoteHandler second = mock(IRemoteHandler.class);
        when(first.canHandle("test:multi")).thenReturn(true);
        when(second.canHandle("test:multi")).thenReturn(true);

        registry.register(first);
        registry.register(second);

        assertThat(registry.find("test:multi")).isSameAs(first);
    }
}
