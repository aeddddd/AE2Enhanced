package com.github.aeddddd.ae2enhanced.util.termination;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminationRegistryTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void ensureDeadWritesTheBaseField() {
        Entity entity = new EntityItem(null);

        assertThat(entity.isDead).isFalse();
        TerminationRegistry.ensureDead(entity);
        assertThat(entity.isDead).isTrue();
    }

    @Test
    void isTerminatingIsFalseWithoutAnActiveServerScope() {
        Entity entity = new EntityItem(null);

        assertThat(TerminationRegistry.isTerminating(entity)).isFalse();
    }
}
