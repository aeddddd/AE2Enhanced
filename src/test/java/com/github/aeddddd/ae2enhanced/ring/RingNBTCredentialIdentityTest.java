package com.github.aeddddd.ae2enhanced.ring;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RingNBTCredentialIdentityTest {
    private static ItemStack stack(NBTTagCompound tag) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.hasTagCompound()).thenReturn(true);
        when(stack.getTagCompound()).thenReturn(tag);
        return stack;
    }

    @Test
    void credentialIdentityIsStableAndDistinctPerStack() {
        ItemStack first = stack(new NBTTagCompound());
        ItemStack second = stack(new NBTTagCompound());

        String firstId = RingNBT.getOrCreateCredentialId(first);
        String secondId = RingNBT.getOrCreateCredentialId(second);

        assertThat(firstId).isNotBlank();
        assertThat(secondId).isNotBlank();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(RingNBT.getOrCreateCredentialId(first)).isEqualTo(firstId);
        assertThat(RingNBT.getCredentialId(first)).isEqualTo(firstId);
    }
}
