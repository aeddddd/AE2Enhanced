package com.github.aeddddd.ae2enhanced.storage;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一个独立附着的注销句柄，关闭幂等且不影响其他附着者。 */
public final class AttachmentToken implements AutoCloseable {
    private final AtomicBoolean attached = new AtomicBoolean(true);
    private final Runnable detachAction;

    public AttachmentToken(Runnable detachAction) {
        this.detachAction = detachAction;
    }

    public static AttachmentToken combine(AttachmentToken... tokens) {
        List<AttachmentToken> copy = Arrays.asList(tokens.clone());
        return new AttachmentToken(() -> {
            RuntimeException failure = null;
            for (AttachmentToken token : copy) {
                if (token == null) continue;
                try {
                    token.close();
                } catch (RuntimeException e) {
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            }
            if (failure != null) throw failure;
        });
    }

    public boolean isAttached() {
        return attached.get();
    }

    @Override
    public void close() {
        if (attached.compareAndSet(true, false)) {
            detachAction.run();
        }
    }
}
