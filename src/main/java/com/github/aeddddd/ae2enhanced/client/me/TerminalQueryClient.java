package com.github.aeddddd.ae2enhanced.client.me;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResultV2;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 按容器会话投递，子界面打开时仍直接更新其父终端的 repo。 */
public final class TerminalQueryClient {
    private static final Map<Long, WeakReference<OmniItemRepo>> REPOS = new HashMap<>();

    private TerminalQueryClient() {
    }

    static synchronized void register(long sessionId, OmniItemRepo repo) {
        Iterator<Map.Entry<Long, WeakReference<OmniItemRepo>>> iterator = REPOS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().get() == null) iterator.remove();
        }
        REPOS.put(sessionId, new WeakReference<>(repo));
    }

    public static synchronized void dispatch(PacketOmniPageResultV2 packet) {
        WeakReference<OmniItemRepo> reference = REPOS.get(packet.getContainerSessionId());
        OmniItemRepo repo = reference == null ? null : reference.get();
        if (repo == null) {
            REPOS.remove(packet.getContainerSessionId());
            return;
        }
        repo.handlePageResult(packet);
    }
}
