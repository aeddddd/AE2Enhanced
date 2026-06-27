package com.github.aeddddd.ae2enhanced.util.compat;

/**
 * ae2fc 兼容性检测工具类.
 * 当 ae2fc (ae2-fluid-crafting) 已安装时,本 mod 的流体/气体假物品功能应自动禁用,
 * 避免与 ae2fc 的 FakeMonitor 体系冲突(重复显示、双重提取等).
 */
public class Ae2fcCompat {

    public static final boolean AE2FC_LOADED;

    static {
        boolean loaded = false;
        try {
            Class.forName("com.glodblock.github.FluidCraft");
            loaded = true;
        } catch (ClassNotFoundException e) {
            // ae2fc 未安装
        }
        AE2FC_LOADED = loaded;
    }
}
