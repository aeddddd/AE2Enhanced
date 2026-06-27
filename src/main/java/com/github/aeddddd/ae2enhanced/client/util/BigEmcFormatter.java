package com.github.aeddddd.ae2enhanced.client.util;

import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.TransmutationEMCFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * BigInteger EMC 格式化工具。
 *
 * <ul>
 *   <li>&lt; 1e12：使用 ProjectE 原格式</li>
 *   <li>1e12 ~ &lt;1e18：使用 ProjectE 后缀格式（T/P/E）</li>
 *   <li>&ge; 1e18：科学记数法，保留 3 位小数</li>
 * </ul>
 */
public final class BigEmcFormatter {

    private static final BigInteger THRESHOLD_SCIENTIFIC = BigInteger.TEN.pow(18);
    private static final BigInteger THRESHOLD_SUFFIX = BigInteger.TEN.pow(12);

    private BigEmcFormatter() {}

    public static String format(BigInteger emc) {
        if (emc == null || emc.signum() < 0) {
            emc = BigInteger.ZERO;
        }
        if (emc.compareTo(THRESHOLD_SCIENTIFIC) >= 0) {
            return formatScientific(emc);
        }
        if (emc.compareTo(THRESHOLD_SUFFIX) >= 0) {
            long clamped = emc.longValue();
            return TransmutationEMCFormatter.EMCFormat(clamped);
        }
        return Constants.EMC_FORMATTER.format(emc);
    }

    private static String formatScientific(BigInteger emc) {
        BigDecimal decimal = new BigDecimal(emc);
        return String.format(Locale.ROOT, "%.3e", decimal.setScale(3, RoundingMode.HALF_UP));
    }
}
