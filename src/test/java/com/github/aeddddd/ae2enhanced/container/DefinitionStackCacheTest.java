package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.util.DefinitionStackCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定义 → 代表物品栈缓存的回归测试（见 {@link DefinitionStackCache} 的类注释）。
 *
 * <p>只用普通对象当值：缓存本身不依赖任何 Minecraft 类，纯 JVM 里就能验证两条性质——
 * 都是真实事故（验收文档 K 节）的直接反例：</p>
 * <ol>
 *   <li><b>键必须与对象身份无关</b>：{@code identifier} 相同的不同实例只能有一个条目
 *       （旧实现按对象身份键 + 弱引用 → 每次调用都 miss，还让清理遍历越来越长）；</li>
 *   <li><b>条目数不能随调用次数增长</b>。</li>
 * </ol>
 */
class DefinitionStackCacheTest {

    @org.junit.jupiter.api.BeforeEach
    void clearCache() {
        DefinitionStackCache.clearForTest();
    }

    @Test
    void keysAreValueBasedNotIdentityBased() {
        String a = new String("ae2e:test.definition");
        String b = new String("ae2e:test.definition");
        assertThat(a).isNotSameAs(b);

        DefinitionStackCache.put(a, "representative-a");
        DefinitionStackCache.put(b, "representative-b");

        assertThat(DefinitionStackCache.size()).isEqualTo(1);
        assertThat(DefinitionStackCache.<String>get(new String("ae2e:test.definition"))).isNotNull();
    }

    @Test
    void repeatedCallsDoNotGrowTheCache() {
        int before = DefinitionStackCache.size(); // clearCache() 之后为 0
        for (int i = 0; i < 5000; i++) {
            String cached = DefinitionStackCache.<String>get("ae2e:test.repeat");
            if (cached == null) {
                DefinitionStackCache.put("ae2e:test.repeat", "representative");
            }
        }
        // 5000 次"取不到才填"之后，条目数只增加 1（而不是 5000）
        assertThat(DefinitionStackCache.size()).isEqualTo(before + 1);
    }

    @Test
    void nullIdentifierIsNotCached() {
        int before = DefinitionStackCache.size();
        DefinitionStackCache.put(null, "value");
        assertThat(DefinitionStackCache.<String>get(null)).isNull();
        assertThat(DefinitionStackCache.size()).isEqualTo(before);
    }
}
