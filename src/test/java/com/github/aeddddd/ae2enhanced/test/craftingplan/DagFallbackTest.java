package com.github.aeddddd.ae2enhanced.test.craftingplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.craftingplan.dag.DagFallback;

/**
 * {@link DagFallback} 回落信号的语义测试.
 * <p>作为热路径上的控制流异常,构造时关闭了栈轨迹与抑制,
 * 此处把该性能取舍固化为断言.</p>
 */
public class DagFallbackTest {

    @Test
    public void testReasonExposedAsFieldAndMessage() {
        var fallback = new DagFallback("cycle_in_dag:stone");
        assertThat(fallback.reason).isEqualTo("cycle_in_dag:stone");
        assertThat(fallback.getMessage()).isEqualTo("cycle_in_dag:stone");
    }

    @Test
    public void testNoCause() {
        assertThat(new DagFallback("x").getCause()).isNull();
    }

    @Test
    public void testStackTraceDisabled() {
        // 构造参数 writableStackTrace=false:栈轨迹为空,避免控制流异常的填充开销
        assertThat(new DagFallback("x").getStackTrace()).isEmpty();
    }

    @Test
    public void testSuppressionDisabled() {
        assertThat(new DagFallback("x").getSuppressed()).isEmpty();
    }

    @Test
    public void testIsCheckedException() {
        // 受检异常:编译器强制调用方处理回落路径
        assertThat(Exception.class).isAssignableFrom(DagFallback.class);
    }
}
