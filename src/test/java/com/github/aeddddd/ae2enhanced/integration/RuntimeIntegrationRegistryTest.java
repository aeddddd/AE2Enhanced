package com.github.aeddddd.ae2enhanced.integration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeIntegrationRegistryTest {

    @Test
    void reflectionCacheReusesClassAndMethodWithoutChangingLookupShape() throws Exception {
        Class<?> first = RuntimeIntegrationRegistry.classForName("java.lang.String");
        Class<?> second = RuntimeIntegrationRegistry.classForName("java.lang.String");
        assertThat(second).isSameAs(first);

        Method firstMethod = RuntimeIntegrationRegistry.method(first, "substring", int.class, int.class);
        Method secondMethod = RuntimeIntegrationRegistry.method(first, "substring", int.class, int.class);
        assertThat(secondMethod).isSameAs(firstMethod);
        assertThat(firstMethod.invoke("abcdef", 1, 3)).isEqualTo("bc");
    }

    @Test
    void bootstrapCapabilitiesUseResourcesWithoutRuntimeClassLoading() {
        assertThat(BootstrapCapabilities.hasResource(
                "com/github/aeddddd/ae2enhanced/integration/BootstrapCapabilities.class")).isTrue();
        assertThat(BootstrapCapabilities.hasResource(
                "com/github/aeddddd/ae2enhanced/integration/DefinitelyMissing.class")).isFalse();
    }
}
