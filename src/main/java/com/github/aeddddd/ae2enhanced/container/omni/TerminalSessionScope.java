package com.github.aeddddd.ae2enhanced.container.omni;

import java.util.ArrayList;
import java.util.List;

public final class TerminalSessionScope {
    private final List<Runnable> closeActions = new ArrayList<Runnable>();
    private boolean closed;

    public void track(Runnable closeAction) {
        if (closed) {
            closeAction.run();
            return;
        }
        closeActions.add(closeAction);
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int index = closeActions.size() - 1; index >= 0; index--) {
            closeActions.get(index).run();
        }
        closeActions.clear();
    }
}
