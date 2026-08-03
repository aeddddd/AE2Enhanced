package com.github.aeddddd.ae2enhanced.test.dimension;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PersonalDimPermission} 单元测试.
 */
class PersonalDimPermissionTest {

    @Test
    void shouldContainExactlyFourValuesInDeclaredOrder() {
        // 序列化依赖 name(),枚举集合的顺序不应意外变动
        assertThat(PersonalDimPermission.values())
                .containsExactly(
                        PersonalDimPermission.ENTER,
                        PersonalDimPermission.BUILD,
                        PersonalDimPermission.INTERACT,
                        PersonalDimPermission.MANAGE_RULES);
    }

    @Test
    void valueOfShouldRoundTripAllValues() {
        for (PersonalDimPermission permission : PersonalDimPermission.values()) {
            assertThat(PersonalDimPermission.valueOf(permission.name())).isSameAs(permission);
        }
    }
}
