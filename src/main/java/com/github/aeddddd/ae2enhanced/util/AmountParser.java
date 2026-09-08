package com.github.aeddddd.ae2enhanced.util;

import appeng.client.gui.MathExpressionParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数量输入解析器：在 AE2 MathExpressionParser 基础上支持数量级后缀。
 * 支持 k/K(千)、m/M(百万)、b/B 或 g/G(十亿)，可出现在表达式任意位置（如 2k*3+500）。
 */
public final class AmountParser {

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)([kKmMbBgG])");

    private AmountParser() {
    }

    /**
     * 先将后缀展开为乘法表达式，再委托给 MathExpressionParser 计算。
     */
    public static double parse(String expression) {
        if (expression == null) {
            return Double.NaN;
        }
        return MathExpressionParser.parse(expandSuffixes(expression));
    }

    static String expandSuffixes(String expression) {
        Matcher matcher = SUFFIX_PATTERN.matcher(expression);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = "(" + matcher.group(1) + "*" + multiplierFor(matcher.group(2).charAt(0)) + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static long multiplierFor(char suffix) {
        switch (Character.toLowerCase(suffix)) {
            case 'k':
                return 1_000L;
            case 'm':
                return 1_000_000L;
            case 'b':
            case 'g':
                return 1_000_000_000L;
            default:
                return 1L;
        }
    }
}
