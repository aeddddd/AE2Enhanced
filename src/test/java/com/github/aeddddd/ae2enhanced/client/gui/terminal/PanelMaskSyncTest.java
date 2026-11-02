package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

class PanelMaskSyncTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void initialPushIsAcknowledgedWithoutDuplicates() {
        List<Integer> sent = new ArrayList<Integer>();
        PanelMaskSync sync = new PanelMaskSync(sent::add, mask -> { });

        sync.tick(7, 3);
        sync.tick(7, 7);
        sync.tick(7, 7);

        assertEquals(Arrays.asList(7), sent);
    }

    @Test
    void remoteRepairIsAppliedBeforeAcknowledging() {
        List<Integer> sent = new ArrayList<Integer>();
        List<Integer> applied = new ArrayList<Integer>();
        PanelMaskSync sync = new PanelMaskSync(sent::add, applied::add);
        sync.tick(3, 3);

        sync.tick(3, 7);

        assertEquals(Arrays.asList(7), applied);
        assertEquals(Arrays.asList(7), sent);
    }

    @Test
    void retriesAreBoundedAndDoNotResetTheirOwnBudget() {
        List<Integer> sent = new ArrayList<Integer>();
        List<Integer> applied = new ArrayList<Integer>();
        PanelMaskSync sync = new PanelMaskSync(sent::add, applied::add);
        sync.tick(7, 3);
        for (int tick = 0; tick < 6 * 40; tick++) {
            sync.tick(7, 3);
        }

        assertEquals(6, sent.size());
        assertEquals(Arrays.asList(3), applied);
    }
}
