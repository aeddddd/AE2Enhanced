package com.github.aeddddd.ae2enhanced.chamber;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LongItemStoreTest {
    @BeforeAll
    static void boot() {
        Bootstrap.register();
    }

    @Test
    void overflowReturnsExactRemainderWithoutSaturatingOrLosingItems() {
        LongItemStore store = new LongItemStore(2);
        ItemStack apple = new ItemStack(Items.APPLE);
        String key = LongItemStore.keyOf(apple);
        assertThat(store.insert(apple, Long.MAX_VALUE - 1L)).isZero();

        assertThat(store.insert(apple, 10L)).isEqualTo(9L);
        assertThat(store.getCount(key)).isEqualTo(Long.MAX_VALUE);
        assertThat(store.extract(key, 5L)).isEqualTo(5L);
        assertThat(store.insert(apple, 5L)).isZero();
        assertThat(store.getCount(key)).isEqualTo(Long.MAX_VALUE);
    }
}
