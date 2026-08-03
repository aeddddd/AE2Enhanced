package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import appeng.api.networking.crafting.ICraftingPlan;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanMarker;

/**
 * {@link SpecialPlanMarker} 单元测试:特殊计划标记的登记与查询.
 */
class SpecialPlanMarkerTest {

    /** 标记后可查询到. */
    @Test
    void testMarkThenIsSpecial() {
        var plan = mock(ICraftingPlan.class);
        assertThat(SpecialPlanMarker.isSpecial(plan)).isFalse();

        SpecialPlanMarker.mark(plan);
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }

    /** 未标记的计划不为特殊. */
    @Test
    void testUnmarkedPlanIsNotSpecial() {
        var marked = mock(ICraftingPlan.class);
        var unmarked = mock(ICraftingPlan.class);

        SpecialPlanMarker.mark(marked);

        assertThat(SpecialPlanMarker.isSpecial(marked)).isTrue();
        assertThat(SpecialPlanMarker.isSpecial(unmarked)).isFalse();
    }

    /** null 计划查询返回 false(不抛异常). */
    @Test
    void testNullPlanIsNotSpecial() {
        assertThat(SpecialPlanMarker.isSpecial(null)).isFalse();
    }

    /** 标记以对象身份为键:重复标记同一对象幂等. */
    @Test
    void testMarkIsIdempotent() {
        var plan = mock(ICraftingPlan.class);
        SpecialPlanMarker.mark(plan);
        SpecialPlanMarker.mark(plan);
        assertThat(SpecialPlanMarker.isSpecial(plan)).isTrue();
    }
}
