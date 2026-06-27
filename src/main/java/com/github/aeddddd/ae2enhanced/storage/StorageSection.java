package com.github.aeddddd.ae2enhanced.storage;

/**
 * 超维度存储文件中的 section 类型.
 * 用于 dirty 标记,只持久化发生变更的 section.
 */
public enum StorageSection {
    ITEM,
    FLUID,
    GAS,
    ESSENTIA,
    ENERGY,
    MANA,
    STARLIGHT
}
