package com.github.aeddddd.ae2enhanced.client.gui.terminal;

import java.util.function.IntConsumer;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

final class PanelMaskSync {
    private final IntConsumer pusher;
    private final IntConsumer remoteApplier;
    private boolean initialized;
    private int lastPushed = -1;
    private int pending = -1;
    private int pendingTicks;
    private int retries;

    PanelMaskSync(IntConsumer pusher, IntConsumer remoteApplier) {
        this.pusher = pusher;
        this.remoteApplier = remoteApplier;
    }

    void tick(int local, int remote) {
        if (!initialized) {
            initialized = true;
            lastPushed = remote;
            if (local != remote) {
                push(local);
            }
            return;
        }
        if (pending != -1 && remote == pending) {
            pending = -1;
        }
        if (local == remote) {
            return;
        }
        if (pending != -1) {
            if (++pendingTicks < 40) {
                return;
            }
            pendingTicks = 0;
            if (++retries <= 5) {
                send(local, false);
                return;
            }
            AE2Enhanced.LOGGER.warn("[AE2E] panelMask 推送 0x{} 多次无回同步，采纳服务端值 0x{} 收敛",
                    Integer.toHexString(pending), Integer.toHexString(remote));
            remoteApplier.accept(remote);
            pending = -1;
            retries = 0;
            return;
        }
        if (remote != lastPushed) {
            remoteApplier.accept(remote);
            push(remote);
            return;
        }
        push(local);
    }

    String diagnose() {
        return " lastPushed=0x" + Integer.toHexString(lastPushed)
                + " pending=0x" + Integer.toHexString(pending)
                + " pendingTicks=" + pendingTicks
                + " retries=" + retries
                + " init=" + initialized;
    }

    void push(int mask) {
        send(mask, true);
    }

    private void send(int mask, boolean resetRetries) {
        lastPushed = mask;
        pending = mask;
        pendingTicks = 0;
        if (resetRetries) {
            retries = 0;
        }
        pusher.accept(mask);
    }
}
