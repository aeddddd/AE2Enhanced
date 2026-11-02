package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContainerOmniToolConfigTest {
    @Test
    void containerBindsTheActualOpeningHandAndRejectsSubstitution() {
        EntityPlayer player = mock(EntityPlayer.class);
        ItemStack main = new ItemStack(new ItemAdvancedMEOmniTool());
        ItemStack off = new ItemStack(new ItemAdvancedMEOmniTool());
        when(player.getHeldItem(EnumHand.MAIN_HAND)).thenReturn(main);
        when(player.getHeldItem(EnumHand.OFF_HAND)).thenReturn(off);

        ContainerOmniToolConfig mainContainer = new ContainerOmniToolConfig(player, EnumHand.MAIN_HAND.ordinal());
        ContainerOmniToolConfig offContainer = new ContainerOmniToolConfig(player, EnumHand.OFF_HAND.ordinal());
        assertThat(mainContainer.getToolStack(player)).isSameAs(main);
        assertThat(offContainer.getToolStack(player)).isSameAs(off);

        when(player.getHeldItem(EnumHand.OFF_HAND)).thenReturn(main);
        assertThat(offContainer.getToolStack(player).isEmpty()).isTrue();
        assertThat(offContainer.canInteractWith(player)).isFalse();
    }
}
