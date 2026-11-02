package com.github.aeddddd.ae2enhanced.guide.client;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guide.GuideManager;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import com.github.aeddddd.ae2enhanced.guide.loader.GuideLoader;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuidePage;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuideSnapshot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/** 客户端脚本指南快照缓存；只在客户端主线程合并，不读取服务器文件。 */
@SideOnly(Side.CLIENT)
public final class ScriptGuideClient {
    private static ScriptGuideSnapshot snapshot = new ScriptGuideSnapshot(0L, java.util.Collections.emptyList());
    private static long receivingRevision = Long.MIN_VALUE;
    private static int receivingTotal;
    private static byte[][] receivingChunks;

    private ScriptGuideClient() {
    }

    public static synchronized void accept(long revision, int totalSize, int chunkIndex,
            int chunkCount, byte[] payload) {
        if (receivingRevision != revision) {
            receivingRevision = revision;
            receivingTotal = totalSize;
            receivingChunks = new byte[chunkCount][];
        }
        if (receivingChunks == null || receivingChunks.length != chunkCount || receivingTotal != totalSize) {
            clearPartial();
            return;
        }
        if (receivingChunks[chunkIndex] != null) {
            return;
        }
        receivingChunks[chunkIndex] = payload.clone();
        int received = 0;
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(totalSize);
        for (byte[] chunk : receivingChunks) {
            if (chunk == null) {
                return;
            }
            received += chunk.length;
            encoded.write(chunk, 0, chunk.length);
        }
        try {
            if (received != totalSize) {
                throw new IllegalArgumentException("Script guide chunk sizes do not match total");
            }
            snapshot = ScriptGuideSnapshot.decode(revision, encoded.toByteArray());
            clearPartial();
            GuideManager.getInstance().reload();
        } catch (Exception exception) {
            AE2Enhanced.LOGGER.warn("[AE2E] Rejected script guide snapshot: {}", exception.getMessage());
            clearPartial();
        }
    }

    public static synchronized void clear() {
        clearPartial();
        snapshot = new ScriptGuideSnapshot(0L, java.util.Collections.emptyList());
        GuideManager.getInstance().reload();
    }

    public static Map<String, GuidePage> merge(Map<String, GuidePage> builtInPages, String language) {
        ScriptGuideSnapshot current = snapshot;
        Map<String, Map<String, String>> pages = new TreeMap<>();
        for (ScriptGuidePage page : current.getPages()) {
            pages.computeIfAbsent(page.getPageId(), ignored -> new TreeMap<>())
                    .put(page.getLanguage(), page.getContent());
        }
        for (Map.Entry<String, Map<String, String>> page : pages.entrySet()) {
            String content = page.getValue().get(language);
            if (content == null) {
                content = page.getValue().get("");
            }
            if (content == null) {
                continue;
            }
            try {
                builtInPages.put(page.getKey(), GuideLoader.parsePage(page.getKey(), content));
            } catch (Exception exception) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to parse script guide page '{}': {}",
                        page.getKey(), exception.getMessage());
            }
        }
        return builtInPages;
    }

    private static void clearPartial() {
        receivingRevision = Long.MIN_VALUE;
        receivingTotal = 0;
        receivingChunks = null;
    }
}
