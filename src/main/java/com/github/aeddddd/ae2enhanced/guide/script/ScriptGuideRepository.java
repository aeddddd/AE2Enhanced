package com.github.aeddddd.ae2enhanced.guide.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 服务端脚本指南的唯一可变仓库；对外快照与页面均不可变。 */
public final class ScriptGuideRepository {
    public static final int MAX_PAGES = 512;
    public static final int MAX_PAGE_BYTES = 64 * 1024;
    public static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;

    private final Map<String, Map<String, String>> pages = new TreeMap<>();
    private long revision;
    private int totalBytes;

    private static void validatePage(ScriptGuidePage page) {
        if (page == null || page.getContent() == null) {
            throw new IllegalArgumentException("Guide page and content must not be null");
        }
        ScriptGuideService.validatePageId(page.getPageId());
        ScriptGuideService.validateLanguage(page.getLanguage(), true);
    }

    private static void validateSize(Map<String, Map<String, String>> candidate) {
        if (candidate.size() > MAX_PAGES) {
            throw new IllegalArgumentException("Too many script guide pages: " + candidate.size());
        }
        int encodedSize = 4;
        for (Map.Entry<String, Map<String, String>> page : candidate.entrySet()) {
            if (page.getValue().size() > 16) {
                throw new IllegalArgumentException("Script guide page exceeds 16 translations");
            }
            for (Map.Entry<String, String> translation : page.getValue().entrySet()) {
                int bytes = translation.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (bytes > MAX_PAGE_BYTES) {
                    throw new IllegalArgumentException("Script guide page exceeds 64 KiB");
                }
                int entryBytes = 2 + page.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + 2 + translation.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + 4 + bytes;
                if (encodedSize > MAX_TOTAL_BYTES - entryBytes) {
                    throw new IllegalArgumentException("Script guide exceeds 2 MiB total sync budget");
                }
                encodedSize += entryBytes;
            }
        }
    }

    public synchronized void put(ScriptGuidePage page) {
        putAll(Collections.singletonList(page));
    }

    public synchronized void putAll(List<ScriptGuidePage> additions) {
        validateAdditions(additions);
        for (ScriptGuidePage page : additions) {
            pages.computeIfAbsent(page.getPageId(), ignored -> new TreeMap<>())
                    .put(page.getLanguage(), page.getContent());
        }
        revision++;
    }

    public synchronized void validateAdditions(List<ScriptGuidePage> additions) {
        Map<String, Map<String, String>> candidate = copyPages();
        for (ScriptGuidePage page : additions) {
            validatePage(page);
            candidate.computeIfAbsent(page.getPageId(), ignored -> new TreeMap<>())
                    .put(page.getLanguage(), page.getContent());
        }
        validateSize(candidate);
    }

    public synchronized void remove(String pageId) {
        Map<String, String> removed = pages.remove(pageId);
        if (removed != null) {
            revision++;
        }
    }

    public synchronized boolean contains(String pageId) {
        return pages.containsKey(pageId);
    }

    public synchronized ScriptGuideSnapshot snapshot() {
        List<ScriptGuidePage> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> page : pages.entrySet()) {
            for (Map.Entry<String, String> translation : page.getValue().entrySet()) {
                result.add(new ScriptGuidePage(page.getKey(), translation.getKey(), translation.getValue()));
            }
        }
        return new ScriptGuideSnapshot(revision, Collections.unmodifiableList(result));
    }

    private Map<String, Map<String, String>> copyPages() {
        Map<String, Map<String, String>> copy = new TreeMap<>();
        for (Map.Entry<String, Map<String, String>> entry : pages.entrySet()) {
            copy.put(entry.getKey(), new TreeMap<>(entry.getValue()));
        }
        return copy;
    }
}
