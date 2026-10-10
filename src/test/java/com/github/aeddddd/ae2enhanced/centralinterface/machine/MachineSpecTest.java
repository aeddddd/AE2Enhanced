package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.centralinterface.DefaultSingleBatchHandler;

/**
 * {@link MachineSpec} 表项语义测试（离线，无 mod 依赖）.
 *
 * <p>表项是 blockId 匹配规则的<b>唯一</b>声明处：{@code AbstractMachineHandler#canHandle} 用它裁定匹配，
 * {@code HandlerRegistry} 用它建两级索引。因此这里钉住三件事：</p>
 * <ol>
 *   <li>精确 id / 命名空间前缀的匹配语义（含 null 与空串这些边界）；</li>
 *   <li>构造时的校验（必须给行为描述；没有 id/前缀时必须是显式的兜底行）；</li>
 *   <li>兜底行（{@link MachineSpec.Builder#fallback()}）不匹配任何 blockId —— 这是
 *       {@code DefaultSingleBatchHandler} 的 {@code canHandle} 恒 false 的唯一依据。</li>
 * </ol>
 */
public class MachineSpecTest {

    private static final BlockPos POS = new BlockPos(1, 64, 1);

    /** 最小行为描述：接口只有三个抽象方法（catalog / idle / canHandle），其余都有框架默认实现。 */
    private static final class FakeAccess implements MachineStorageAccess {

        @Override
        public ISlotCatalog catalog(World world, BlockPos pos) {
            return null;
        }

        @Override
        public MachineIdle idle(World world, BlockPos pos) {
            return MachineIdle.none();
        }

        @Override
        public boolean canHandle(String blockId) {
            return false;
        }
    }

    private static MachineSpec.Builder specBuilder() {
        return MachineSpec.builder().access(new FakeAccess());
    }

    // ------------------------------------------------------------------
    // 匹配语义
    // ------------------------------------------------------------------

    /** 精确 id 只匹配逐字相同的 blockId。 */
    @Test
    public void testExactIdsMatchOnlyIdenticalBlockIds() {
        MachineSpec spec = specBuilder().exactIds("techreborn:grinder", "techreborn:compressor").build();

        assertThat(spec.matches("techreborn:grinder")).isTrue();
        assertThat(spec.matches("techreborn:compressor")).isTrue();
        assertThat(spec.matches("techreborn:grinders")).isFalse();
        assertThat(spec.matches("techreborn:industrial_grinder")).isFalse();
        assertThat(spec.matches("techreborn")).isFalse();
        assertThat(spec.matches("")).isFalse();
    }

    /** 命名空间前缀匹配整个 mod（含"只有前缀"这种边界，与重构前的 startsWith 一致）。 */
    @Test
    public void testPrefixesMatchWholeNamespace() {
        MachineSpec spec = specBuilder().prefixes("thermalexpansion:").build();

        assertThat(spec.matches("thermalexpansion:machine_pulverizer")).isTrue();
        assertThat(spec.matches("thermalexpansion:")).isTrue();
        assertThat(spec.matches("thermalexpansion")).isFalse();
        assertThat(spec.matches("thermalexpansionx:block")).isFalse();
        assertThat(spec.matches("minecraft:furnace")).isFalse();
    }

    /** null blockId 一律不匹配（{@code HandlerRegistry#findHandler(null)} 依赖这条）。 */
    @Test
    public void testNullBlockIdNeverMatches() {
        assertThat(specBuilder().exactIds("a:b").build().matches(null)).isFalse();
        assertThat(specBuilder().prefixes("a:").build().matches(null)).isFalse();
        assertThat(specBuilder().fallback().build().matches(null)).isFalse();
    }

    /** 重复声明的 id 会被去重：索引里同一个 handler 不会出现两次。 */
    @Test
    public void testDuplicateIdsAreDeduplicated() {
        MachineSpec spec = specBuilder().exactIds("a:b", "a:b", "a:c").build();

        assertThat(spec.exactBlockIds()).containsExactly("a:b", "a:c");
    }

    /** 表项如实暴露声明内容与行为描述（注册表据此建索引）。 */
    @Test
    public void testSpecExposesDeclaredRulesAndAccess() {
        MachineStorageAccess access = new FakeAccess();
        MachineSpec spec = MachineSpec.builder()
                .exactIds("a:b")
                .prefixes("a:")
                .access(access)
                .build();

        assertThat(spec.access()).isSameAs(access);
        assertThat(spec.exactBlockIds()).containsExactly("a:b");
        assertThat(spec.blockIdPrefixes()).containsExactly("a:");
        assertThat(spec.matches("a:b")).isTrue();
        assertThat(spec.matches("a:anything")).isTrue();
    }

    // ------------------------------------------------------------------
    // 构造校验
    // ------------------------------------------------------------------

    /** 表项必须绑定行为描述，否则无处理器可用。 */
    @Test
    public void testBuildRejectsMissingAccess() {
        assertThatThrownBy(() -> MachineSpec.builder().exactIds("a:b").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MachineStorageAccess");
    }

    /** 没有 id/前缀又不是显式兜底行 → 拒绝：这种表项永远命中不了，只会静默失效。 */
    @Test
    public void testBuildRejectsEmptyRulesWithoutFallback() {
        assertThatThrownBy(() -> specBuilder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blockId");
    }

    // ------------------------------------------------------------------
    // 兜底行
    // ------------------------------------------------------------------

    /** 兜底行不匹配任何 blockId：这是 DefaultSingleBatchHandler.canHandle 恒 false 的唯一依据。 */
    @Test
    public void testFallbackRowMatchesNothing() {
        MachineSpec spec = specBuilder().fallback().build();

        assertThat(spec.matches("minecraft:furnace")).isFalse();
        assertThat(spec.matches("thermalexpansion:machine_pulverizer")).isFalse();
        assertThat(spec.matches("")).isFalse();
        assertThat(spec.exactBlockIds()).isEmpty();
        assertThat(spec.blockIdPrefixes()).isEmpty();
    }

    /** 兜底行即使同时声明了 fallback 与真实规则，也按声明的规则匹配（不会"因为 fallback 就失效"）。 */
    @Test
    public void testFallbackFlagDoesNotDisableDeclaredRules() {
        MachineSpec spec = specBuilder().prefixes("a:").fallback().build();

        assertThat(spec.matches("a:x")).isTrue();
        assertThat(spec.matches("b:x")).isFalse();
    }

    /** 生产兜底处理器（DefaultSingleBatchHandler）声明的正是兜底行。 */
    @Test
    public void testDefaultHandlerSpecIsFallbackRow() {
        MachineSpec spec = new DefaultSingleBatchHandler().spec();

        assertThat(spec.matches("minecraft:furnace")).isFalse();
        assertThat(spec.matches(null)).isFalse();
        assertThat(spec.exactBlockIds()).isEmpty();
        assertThat(spec.blockIdPrefixes()).isEmpty();
        assertThat(spec.access()).isInstanceOf(DefaultSingleBatchHandler.class);
    }
}
