package com.github.aeddddd.ae2enhanced.integration.guide;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuideService;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuideSnapshot;
import com.github.aeddddd.ae2enhanced.network.packet.PacketGuideSync;
import net.minecraft.entity.player.EntityPlayerMP;

import java.io.IOException;
import java.util.Arrays;

/** 登录时将当前脚本指南快照按协议预算分块发送。 */
public final class GuideSyncService {
    private GuideSyncService() {
    }

    public static void sendTo(EntityPlayerMP player) throws IOException {
        ScriptGuideSnapshot snapshot = ScriptGuideService.serverRepository().snapshot();
        byte[] encoded = snapshot.encode();
        int chunks = Math.max(1, (encoded.length + PacketGuideSync.MAX_CHUNK_BYTES - 1)
                / PacketGuideSync.MAX_CHUNK_BYTES);
        for (int i = 0; i < chunks; i++) {
            int from = i * PacketGuideSync.MAX_CHUNK_BYTES;
            int to = Math.min(encoded.length, from + PacketGuideSync.MAX_CHUNK_BYTES);
            AE2Enhanced.network.sendTo(new PacketGuideSync(snapshot.getRevision(), encoded.length, i, chunks,
                    Arrays.copyOfRange(encoded, from, to)), player);
        }
    }
}
