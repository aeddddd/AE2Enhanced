package com.github.aeddddd.ae2enhanced.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinyinSearchTest {
    @Test
    void matchesFullPinyinAndInitialsFromBundledTable() {
        assertTrue(PinyinSearch.contains("水桶", "shui"));
        assertTrue(PinyinSearch.contains("水桶", "sh"));
        assertTrue(PinyinSearch.contains("水桶", "st"));
        // 中文直查必须先于拼音路径命中
        assertTrue(PinyinSearch.contains("水桶", "水"));
        assertTrue(PinyinSearch.contains("水桶", "水桶"));
    }

    @Test
    void matchesEnglishSubstringsCaseInsensitively() {
        assertTrue(PinyinSearch.contains("Iron Ingot", "iro"));
        assertTrue(PinyinSearch.contains("Iron Ingot", "INGOT"));
        assertFalse(PinyinSearch.contains("Iron Ingot", "shui"));
    }
}
