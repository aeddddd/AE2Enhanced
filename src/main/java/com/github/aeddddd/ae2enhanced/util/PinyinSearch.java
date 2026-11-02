package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 名称搜索的拼音支持。
 *
 * <p>匹配顺序：先做大小写不敏感的原文子串（中文/英文都走这一条），再走拼音。
 * 拼音本身是可选联动：安装了 JECH/HECH 时优先调用它的 {@code me.towdium.pinin.PinIn#contains}
 * （与 JEI 搜索完全同源，支持 {@code sh} → 水 这类前缀与首字母组合）；未安装时回退到随 jar
 * 打包的静态汉字表。{@code integrations.pinyinSearch = false} 时不做任何拼音匹配，
 * 只保留原文子串语义。</p>
 */
public final class PinyinSearch {
    private static final int CACHE_LIMIT = 8192;
    private static final Map<String, Forms> CACHE = new ConcurrentHashMap<>();
    private static volatile Map<Character, String> table;

    private static volatile boolean pininChecked;
    private static volatile Object pininInstance;
    private static volatile Method pininContains;

    private PinyinSearch() {
    }

    public static boolean contains(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        String lower = haystack.toLowerCase(Locale.ROOT);
        String plainNeedle = stripDiacritics(needle.toLowerCase(Locale.ROOT));
        if (lower.contains(plainNeedle)) {
            return true;
        }
        if (!enabled()) {
            return false;
        }
        String need = compact(plainNeedle);
        if (need.isEmpty()) {
            return false;
        }
        if (externalContains(haystack, needle)) {
            return true;
        }
        Forms forms = forms(haystack);
        return forms.full.contains(need) || forms.initials.contains(need);
    }

    /** 搜索联动开关（Forge config 的 integrations.pinyinSearch）。 */
    public static boolean enabled() {
        try {
            return AE2EnhancedConfig.integrations == null || AE2EnhancedConfig.integrations.pinyinSearch;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * JECH/HECH 的 {@code PinIn}。类不存在（未安装）或构造失败时返回 false，
     * 后续调用直接走内置表，不再反复反射。
     */
    private static boolean externalContains(String haystack, String needle) {
        if (!pininChecked) {
            synchronized (PinyinSearch.class) {
                if (!pininChecked) {
                    try {
                        Class<?> type = Class.forName("me.towdium.pinin.PinIn");
                        pininInstance = type.getConstructor().newInstance();
                        pininContains = type.getMethod("contains", String.class, String.class);
                    } catch (Throwable throwable) {
                        pininInstance = null;
                        pininContains = null;
                        AE2Enhanced.LOGGER.debug("[AE2E] JECH/HECH pinyin matcher not available", throwable);
                    }
                    pininChecked = true;
                }
            }
        }
        Object instance = pininInstance;
        Method method = pininContains;
        if (instance == null || method == null) {
            return false;
        }
        try {
            Object result = method.invoke(instance, haystack, needle);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable throwable) {
            AE2Enhanced.LOGGER.debug("[AE2E] JECH/HECH pinyin matcher failed", throwable);
            return false;
        }
    }

    private static Forms forms(String haystack) {
        Forms cached = CACHE.get(haystack);
        if (cached != null) {
            return cached;
        }
        Map<Character, String> mapping = table();
        StringBuilder full = new StringBuilder(haystack.length() * 3);
        StringBuilder initials = new StringBuilder(haystack.length());
        String lower = haystack.toLowerCase(Locale.ROOT);
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            String syllable = mapping.get(character);
            if (syllable != null) {
                full.append(syllable);
                initials.append(syllable.charAt(0));
            } else if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                full.append(character);
                initials.append(character);
            }
        }
        Forms result = new Forms(full.toString(), initials.toString());
        if (CACHE.size() < CACHE_LIMIT) {
            CACHE.put(haystack, result);
        }
        return result;
    }

    private static String compact(String value) {
        return value.replaceAll("[^a-z0-9]", "");
    }

    private static String stripDiacritics(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static Map<Character, String> table() {
        Map<Character, String> loaded = table;
        if (loaded != null) {
            return loaded;
        }
        synchronized (PinyinSearch.class) {
            if (table != null) {
                return table;
            }
            Map<Character, String> map = new HashMap<>(24576);
            try (InputStream input = AE2Enhanced.class.getResourceAsStream(
                    "/assets/ae2enhanced/pinyin.txt")) {
                if (input != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(input, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            int split = line.indexOf('\t');
                            if (split == 1 && line.length() > 2) {
                                map.put(line.charAt(0), line.substring(2));
                            }
                        }
                    }
                } else {
                    AE2Enhanced.LOGGER.warn("[AE2E] Bundled pinyin table is missing");
                }
            } catch (Exception exception) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to load bundled pinyin table", exception);
            }
            table = map;
            return map;
        }
    }

    private static final class Forms {
        private final String full;
        private final String initials;

        private Forms(String full, String initials) {
            this.full = full;
            this.initials = initials;
        }
    }
}
