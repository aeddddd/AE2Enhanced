package com.github.aeddddd.ae2enhanced.util;

/**
 * 通用格式化工具类.
 */
public final class FormatUtil {

    private FormatUtil() {}

    /**
     * 将大数值缩写为 K / M / G / T / P / E 格式.
     *
     * @param count 原始数值
     * @return 缩写字符串,如 "1.2M"、"3.5K",小于 1000 返回原值
     */
    public static String formatCount(long count) {
        if (count >= 1_000_000_000_000_000_000L) {
            return String.format("%.1fE", count / 1_000_000_000_000_000_000.0);
        }
        if (count >= 1_000_000_000_000_000L) {
            return String.format("%.1fP", count / 1_000_000_000_000_000.0);
        }
        if (count >= 1_000_000_000_000L) {
            return String.format("%.1fT", count / 1_000_000_000_000.0);
        }
        if (count >= 1_000_000_000L) {
            return String.format("%.1fG", count / 1_000_000_000.0);
        }
        if (count >= 1_000_000L) {
            return String.format("%.1fM", count / 1_000_000.0);
        }
        if (count >= 1000L) {
            return String.format("%.1fK", count / 1000.0);
        }
        return String.valueOf(count);
    }
}
