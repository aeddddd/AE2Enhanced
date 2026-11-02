package com.github.aeddddd.ae2enhanced.container;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplicedTermVisibilityTest {
    @Test
    void jeiTransfersRequireEveryTargetPanelToBeVisible() {
        int all = SplicedTermModules.ALL;
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(all, (byte) 0, true)).isTrue();
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(all, (byte) 2, false)).isTrue();

        int noCrafting = all & ~SplicedTermModules.bit(SplicedTermModules.CRAFTING);
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(noCrafting, (byte) 2, true)).isFalse();
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(noCrafting, (byte) 0, true)).isFalse();
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(noCrafting, (byte) 1, true)).isTrue();

        int noPattern = all & ~SplicedTermModules.bit(SplicedTermModules.PATTERN);
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(noPattern, (byte) 1, true)).isFalse();
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(noPattern, (byte) 0, false)).isFalse();

        int noStorage = all & ~SplicedTermModules.bit(SplicedTermModules.STORAGE);
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(noStorage, (byte) 2, false)).isFalse();
        assertThat(ContainerSplicedTerm.allowsRecipeTransfer(all, (byte) 3, true)).isFalse();
    }
}
