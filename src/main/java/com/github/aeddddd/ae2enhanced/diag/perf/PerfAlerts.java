package com.github.aeddddd.ae2enhanced.diag.perf;

import com.github.aeddddd.ae2enhanced.diag.DiagEvents;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 性能异常预警：与基准线周期比对，超阈值向 OP 广播并写入异常事件流.
 *
 * <p>由 {@link TpsTracker} 每分钟驱动一次。比对项：</p>
 * <ul>
 *   <li>2 分钟窗口平均 tick 耗时 vs 基准线</li>
 *   <li>全网格总 tick 耗时 vs 基准线</li>
 *   <li>基准线 Top10 机器类的当前耗时 vs 基准值</li>
 * </ul>
 *
 * <p>预警只提示不干预；冷却 5 分钟防刷屏；消息为本地化键，客户端按各自语言渲染。
 * 需先 {@code /ae2e perf baseline set} 建立基准线。</p>
 */
public final class PerfAlerts {

    /** 比对间隔（1 分钟） */
    public static final int CHECK_INTERVAL_TICKS = 1200;
    private static final long ALERT_COOLDOWN_MS = 300_000L;
    private static final String KEY_PREFIX = "chat.ae2enhanced.perf.alert.";

    private static long lastAlertMillis = 0L;

    private PerfAlerts() {
    }

    /** 每分钟由 TpsTracker 调用。 */
    public static void check(MinecraftServer server) {
        if (server == null || !DiagSwitch.isEnabled(DiagSwitch.PERF)) {
            return;
        }
        PerfBaseline.Baseline baseline = PerfBaseline.load(server);
        if (baseline == null) {
            return;
        }
        TpsTracker.Stats stats = TpsTracker.stats(1200);
        if (stats == null) {
            return;
        }
        double mult = baseline.alertMultiplier;

        List<ITextComponent> alerts = new ArrayList<>();
        List<String> plainAlerts = new ArrayList<>();
        if (stats.avgMs > baseline.avgTickMs * mult) {
            addAlert(alerts, plainAlerts, KEY_PREFIX + "tick",
                    fmt(stats.avgMs), fmt(baseline.avgTickMs), fmt(mult));
        }

        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(-1.0);
        long gridTotal = 0;
        for (PerfAnalyzer.GridStat gs : scan.grids) {
            gridTotal += gs.totalAvgNanos;
        }
        if (baseline.gridTotalNanos > 0 && gridTotal > baseline.gridTotalNanos * mult) {
            addAlert(alerts, plainAlerts, KEY_PREFIX + "grid_total",
                    PerfAnalyzer.formatNanos(gridTotal),
                    PerfAnalyzer.formatNanos(baseline.gridTotalNanos), fmt(mult));
        }
        for (PerfAnalyzer.MachineStat ms : scan.machines) {
            Long base = baseline.machineTotals.get(ms.className);
            if (base != null && base > 0 && ms.totalAvgNanos > base * mult) {
                addAlert(alerts, plainAlerts, KEY_PREFIX + "machine",
                        ms.className, PerfAnalyzer.formatNanos(ms.totalAvgNanos),
                        PerfAnalyzer.formatNanos(base), fmt(mult));
            }
        }

        if (alerts.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAlertMillis < ALERT_COOLDOWN_MS) {
            return;
        }
        lastAlertMillis = now;
        for (String alert : plainAlerts) {
            DiagEvents.warn("perf", alert);
        }
        broadcastToOps(server, alerts);
    }

    /** 同时产出本地化组件（玩家广播）与英文纯文本（事件流/日志）。 */
    private static void addAlert(List<ITextComponent> alerts, List<String> plainAlerts,
                                 String key, Object... args) {
        TextComponentTranslation component = new TextComponentTranslation(key, args);
        alerts.add(component);
        plainAlerts.add(component.getUnformattedComponentText());
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static void broadcastToOps(MinecraftServer server, List<ITextComponent> alerts) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (!player.canUseCommand(2, "ae2enhanced")) {
                continue;
            }
            ITextComponent header = new TextComponentTranslation(KEY_PREFIX + "header");
            header.getStyle().setColor(TextFormatting.RED);
            player.sendMessage(header);
            for (ITextComponent alert : alerts) {
                alert.getStyle().setColor(TextFormatting.YELLOW);
                player.sendMessage(new TextComponentTranslation(KEY_PREFIX + "line", alert));
            }
            ITextComponent hint = new TextComponentTranslation(KEY_PREFIX + "hint");
            hint.getStyle().setColor(TextFormatting.GRAY);
            player.sendMessage(hint);
        }
    }
}
