package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SplicedActionRouterTest {
    @Test
    void providerActionsUseInterfaceOnlyWhenAvailableAndVisible() {
        assertEquals(SplicedActionRouter.Target.INTERFACE,
                SplicedActionRouter.route(true, true, 1L, 0, 20, true));
        assertEquals(SplicedActionRouter.Target.DROP,
                SplicedActionRouter.route(true, false, 1L, 0, 20, true));
        assertEquals(SplicedActionRouter.Target.OMNI,
                SplicedActionRouter.route(false, false, 1L, 0, 20, true));
    }

    @Test
    void zeroProviderIdUsesOrdinaryContainerSemantics() {
        assertEquals(SplicedActionRouter.Target.OMNI,
                SplicedActionRouter.route(true, true, 0L, 0, 20, true));
        assertEquals(SplicedActionRouter.Target.DROP,
                SplicedActionRouter.route(true, true, 0L, 0, 20, false));
    }

    @Test
    void unknownActionsAreRejectedAndKnownActionsRespectVisibility() {
        int visible = SplicedTermModules.ALL;
        int hidden = SplicedTermModules.withVisibility(visible, SplicedTermSlotPolicy.MODULE_PATTERN, false);

        assertFalse(SplicedActionRouter.isActionAllowed(visible, "UnknownAction"));
        assertTrue(SplicedActionRouter.isActionAllowed(visible, "Scroll"));
        assertTrue(SplicedActionRouter.isActionAllowed(visible, "Encode"));
        assertTrue(SplicedActionRouter.isActionAllowed(visible, "CraftMode"));
        assertFalse(SplicedActionRouter.isActionAllowed(hidden, "Scroll"));
        assertTrue(SplicedActionRouter.isActionAllowed(hidden, "PanelVisibility"));
    }
}
