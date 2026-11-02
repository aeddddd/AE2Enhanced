package com.github.aeddddd.ae2enhanced.container;

import appeng.container.guisync.GuiSync;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerOmniSyncContractTest {
    @Test
    void omniSyncIdsAreUniqueAcrossTheEntireContainerHierarchy() {
        Map<Integer, String> owners = new HashMap<>();
        for (Class<?> type = ContainerSplicedTerm.class; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                GuiSync sync = field.getAnnotation(GuiSync.class);
                if (sync == null) continue;
                String owner = type.getName() + "." + field.getName();
                String previous = owners.put(sync.value(), owner);
                assertThat(previous)
                        .as("@GuiSync(%d) 同时用于 %s 和 %s", sync.value(), previous, owner)
                        .isNull();
            }
        }
        assertThat(owners).containsKeys(90, 96, 97, 98, 99, 100, 101);
    }
}
