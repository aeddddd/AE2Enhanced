package com.github.aeddddd.ae2enhanced.container.omni;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TerminalSessionScopeTest {
    @Test
    void closeDetachesEveryListenerExactlyOnce() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        TerminalSessionScope scope = new TerminalSessionScope();
        scope.track(first::incrementAndGet);
        scope.track(second::incrementAndGet);

        scope.close();
        scope.close();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void lateRegistrationIsClosedImmediately() {
        AtomicInteger calls = new AtomicInteger();
        TerminalSessionScope scope = new TerminalSessionScope();
        scope.close();
        scope.track(calls::incrementAndGet);
        assertEquals(1, calls.get());
    }
}
