package com.github.aeddddd.ae2enhanced.command;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.diag.perf.PerfAnalyzer;
import com.github.aeddddd.ae2enhanced.diag.perf.PerfBaseline;
import com.github.aeddddd.ae2enhanced.diag.perf.PerfExporter;
import com.github.aeddddd.ae2enhanced.diag.perf.TpsTracker;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.io.File;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

final class PerfCommand implements CommandAdapter {
    private static final String[] SUBCOMMANDS = {"tps", "top", "grid", "slow", "metrics", "baseline", "export", "alert"};
    private static final String[] TOP_SCOPES = {"machine", "grid"};
    private static final String[] BASELINE_ACTIONS = {"set", "show", "clear"};

    private static void usage(ICommandSender sender) {
        CommandSupport.usage(sender, "Usage: /ae2e perf <tps|top [machine|grid] [n]|grid <rank>|slow [thresholdMs] [n]|metrics|baseline <set|show|clear>|export|alert [multiplier]>");
    }

    private static void tps(ICommandSender sender) {
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, DiagSwitch.isEnabled(DiagSwitch.PERF)
                ? "chat.ae2enhanced.perf.tps_header" : "chat.ae2enhanced.perf.tps_header_off"));
        window(sender, "5s", 100); window(sender, "1min", 1200); window(sender, "2min", 2400);
    }

    private static void window(ICommandSender sender, String label, int window) {
        TpsTracker.Stats stats = TpsTracker.stats(window);
        if (stats == null) { sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.no_data", label)); return; }
        TextFormatting color = stats.tps >= 19.5 ? TextFormatting.GREEN : stats.tps >= 15.0 ? TextFormatting.YELLOW : TextFormatting.RED;
        sender.sendMessage(CommandSupport.msg(color, "chat.ae2enhanced.perf.tps_line", label, String.valueOf(stats)));
    }

    private static void top(ICommandSender sender, String[] args) {
        String scope = args.length >= 3 ? args[2].toLowerCase() : "machine";
        int limit = args.length >= 4 ? CommandSupport.parsePositiveInt(args[3], 10) : 10;
        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(-1.0);
        if ("grid".equals(scope)) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.top_grid_header", limit));
            int rank = 0;
            for (PerfAnalyzer.GridStat stat : scan.grids) {
                if (++rank > limit) break;
                sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_line", rank, stat.nodes,
                        PerfAnalyzer.formatNanos(stat.totalAvgNanos), CommandSupport.formatDouble(stat.avgPowerUsage), stat.cpuBusy,
                        stat.cpuTotal, String.valueOf(stat.controllerState)));
            }
            if (rank == 0) sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.no_grids"));
        } else if ("machine".equals(scope)) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.top_machine_header", limit));
            int rank = 0;
            for (PerfAnalyzer.MachineStat stat : scan.machines) {
                if (++rank > limit) break;
                sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.machine_line", rank,
                        stat.className, stat.nodes, PerfAnalyzer.formatNanos(stat.totalAvgNanos)));
            }
            if (rank == 0) sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.no_tracked"));
        } else { CommandSupport.usage(sender, "Usage: /ae2e perf top <machine|grid> [n]"); return; }
        sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.scan_ms", CommandSupport.formatDouble(scan.scanMs())));
    }

    private static void grid(ICommandSender sender, String[] args) {
        if (args.length < 3) { CommandSupport.usage(sender, "Usage: /ae2e perf grid <rank>"); return; }
        int rank = CommandSupport.parsePositiveInt(args[2], -1);
        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(-1.0);
        if (rank < 1 || rank > scan.grids.size()) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.perf.invalid_rank", args[2], scan.grids.size())); return;
        }
        PerfAnalyzer.GridStat stat = scan.grids.get(rank - 1);
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.grid_detail_header", rank));
        sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_detail_nodes", stat.nodes, PerfAnalyzer.formatNanos(stat.totalAvgNanos)));
        sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_detail_power", String.valueOf(stat.controllerState),
                CommandSupport.formatDouble(stat.avgPowerUsage), CommandSupport.formatDouble(stat.storedPower)));
        sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_detail_cpus", stat.cpuBusy, stat.cpuTotal));
    }

    private static void slow(ICommandSender sender, String[] args) {
        double threshold = args.length >= 3 ? CommandSupport.parsePositiveDouble(args[2], 1.0) : 1.0;
        int limit = args.length >= 4 ? CommandSupport.parsePositiveInt(args[3], 10) : 10;
        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(threshold);
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.slow_header", CommandSupport.formatDouble(threshold), limit));
        int rank = 0;
        for (PerfAnalyzer.SlowNode node : scan.slowNodes) {
            if (++rank > limit) break;
            sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.slow_line", rank, node.machine,
                    PerfAnalyzer.formatNanos(node.avgNanos), node.location));
        }
        if (rank == 0) sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.none_over"));
        sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.scan_ms", CommandSupport.formatDouble(scan.scanMs())));
    }

    private static void metrics(ICommandSender sender) {
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.metrics_header"));
        int shown = 0;
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Counter counter : MetricsRegistry.counters()) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.metrics_counter", counter.name(), counter.get())); shown++;
        }
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Timer timer : MetricsRegistry.timers()) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.metrics_timer", timer.name(), timer.snapshot().toString())); shown++;
        }
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Gauge gauge : MetricsRegistry.gauges()) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.metrics_gauge", gauge.name(), CommandSupport.formatDouble(gauge.get()))); shown++;
        }
        if (shown == 0) sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.metrics_empty"));
    }

    private static void baseline(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 3) { CommandSupport.usage(sender, "Usage: /ae2e perf baseline <set|show|clear>"); return; }
        switch (args[2].toLowerCase()) {
            case "set": {
                PerfBaseline.Baseline baseline = PerfBaseline.capture(server);
                if (baseline == null) { sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.perf.baseline_no_data")); return; }
                if (PerfBaseline.save(server, baseline)) sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.baseline_set",
                        CommandSupport.formatDouble(baseline.avgTickMs), PerfAnalyzer.formatNanos(baseline.gridTotalNanos), baseline.machineTotals.size(),
                        CommandSupport.formatDouble(baseline.alertMultiplier)));
                else sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.perf.baseline_save_failed"));
                break;
            }
            case "show": {
                PerfBaseline.Baseline baseline = PerfBaseline.load(server);
                if (baseline == null) { sender.sendMessage(CommandSupport.msg(TextFormatting.YELLOW, "chat.ae2enhanced.perf.baseline_none")); return; }
                sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.baseline_show_header"));
                sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.baseline_created",
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(baseline.createdAt))));
                sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.baseline_summary", CommandSupport.formatDouble(baseline.avgTickMs),
                        PerfAnalyzer.formatNanos(baseline.gridTotalNanos), CommandSupport.formatDouble(baseline.alertMultiplier)));
                for (Map.Entry<String, Long> entry : baseline.machineTotals.entrySet()) sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY,
                        "chat.ae2enhanced.perf.baseline_machine", entry.getKey(), PerfAnalyzer.formatNanos(entry.getValue())));
                break;
            }
            case "clear":
                if (PerfBaseline.clear(server)) sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.baseline_cleared"));
                else sender.sendMessage(CommandSupport.msg(TextFormatting.YELLOW, "chat.ae2enhanced.perf.baseline_not_exist"));
                break;
            default: CommandSupport.usage(sender, "Usage: /ae2e perf baseline <set|show|clear>");
        }
    }

    private static void export(MinecraftServer server, ICommandSender sender) {
        File file = PerfExporter.export(server);
        if (file == null) sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.perf.export_failed"));
        else sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.export_done", file.getPath()));
    }

    private static void alert(MinecraftServer server, ICommandSender sender, String[] args) {
        PerfBaseline.Baseline baseline = PerfBaseline.load(server);
        if (args.length < 3) {
            Object multiplier = baseline == null ? CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.alert_no_baseline")
                    : CommandSupport.formatDouble(baseline.alertMultiplier);
            sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.alert_current", multiplier)); return;
        }
        double multiplier = CommandSupport.parsePositiveDouble(args[2], -1.0);
        if (multiplier <= 0) { CommandSupport.usage(sender, "Usage: /ae2e perf alert <multiplier> (e.g. 1.5)"); return; }
        if (baseline == null) { sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.perf.alert_need_baseline")); return; }
        baseline.alertMultiplier = multiplier;
        if (PerfBaseline.save(server, baseline)) sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.alert_set", CommandSupport.formatDouble(multiplier)));
        else sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.perf.baseline_save_failed"));
    }

    public String name() { return "perf"; }

    public Permission permission() { return Permission.ANALYSIS; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender); return; }
        switch (args[1].toLowerCase()) {
            case "tps": tps(sender); break;
            case "top": top(sender, args); break;
            case "grid": grid(sender, args); break;
            case "slow": slow(sender, args); break;
            case "metrics": metrics(sender); break;
            case "baseline": baseline(server, sender, args); break;
            case "export": export(server, sender); break;
            case "alert": alert(server, sender, args); break;
            default: usage(sender);
        }
    }

    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 2) return CommandSupport.matching(args, SUBCOMMANDS);
        if (args.length == 3 && "top".equalsIgnoreCase(args[1])) return CommandSupport.matching(args, TOP_SCOPES);
        if (args.length == 3 && "baseline".equalsIgnoreCase(args[1])) return CommandSupport.matching(args, BASELINE_ACTIONS);
        return Collections.emptyList();
    }
}

final class PlanCommand implements CommandAdapter {
    private static final String[] SUBCOMMANDS = {"stats", "running"};

    private static void usage(ICommandSender sender) { CommandSupport.usage(sender, "Usage: /ae2e plan <stats|running>"); }

    private static void stats(ICommandSender sender) {
        ITextComponent verify = CommandSupport.msg(TextFormatting.GRAY, DiagSwitch.isEnabled(DiagSwitch.PLAN_VERIFY)
                ? "chat.ae2enhanced.debug.state_on" : "chat.ae2enhanced.debug.state_off");
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.plan.stats_header", verify));
        for (String path : new String[]{"special", "dag", "dagFallback", "native"}) {
            long count = MetricsRegistry.counter("plan.path." + path).get();
            com.github.aeddddd.ae2enhanced.diag.metrics.Timer.Snapshot snapshot = MetricsRegistry.timer("plan.computeMs." + path).snapshot();
            if (count == 0 && snapshot.count == 0) continue;
            sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.plan.stats_line", path, count,
                    CommandSupport.formatDouble(snapshot.avgMs), CommandSupport.formatDouble(snapshot.maxMs), CommandSupport.formatDouble(snapshot.p95Ms)));
        }
        long passed = MetricsRegistry.counter("plan.verify.passed").get();
        long violations = MetricsRegistry.counter("plan.verify.violation").get();
        long skipped = MetricsRegistry.counter("plan.verify.skippedSimulation").get();
        sender.sendMessage(CommandSupport.msg(violations > 0 ? TextFormatting.RED : TextFormatting.GREEN,
                "chat.ae2enhanced.plan.verify_summary", passed, violations, skipped));
    }

    private static void running(ICommandSender sender) {
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.plan.running_header"));
        int shown = 0;
        for (appeng.me.Grid grid : appeng.hooks.TickHandler.INSTANCE.getGridList()) {
            if (grid.isEmpty()) continue;
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            for (ICraftingCPU cpu : crafting.getCpus()) {
                if (!cpu.isBusy()) continue;
                shown++;
                long start = cpu.getStartItemCount();
                long remaining = cpu.getRemainingItemCount();
                String progress = start > 0 ? CommandSupport.formatDouble((start - remaining) * 100.0 / start) + "%" : "?";
                long elapsed = cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster
                        ? ((appeng.me.cluster.implementations.CraftingCPUCluster) cpu).getElapsedTime() / 1_000_000L : -1L;
                Object name = cpu.getName().isEmpty() ? new TextComponentTranslation("chat.ae2enhanced.plan.unnamed_cpu") : cpu.getName();
                if (elapsed >= 0) sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.plan.running_line", name,
                        String.valueOf(cpu.getFinalOutput()), progress, start - remaining, start, cpu.getCoProcessors(), CommandSupport.formatDouble(elapsed / 1000.0)));
                else sender.sendMessage(CommandSupport.msg(TextFormatting.WHITE, "chat.ae2enhanced.plan.running_line_no_elapsed", name,
                        String.valueOf(cpu.getFinalOutput()), progress, start - remaining, start, cpu.getCoProcessors()));
            }
        }
        if (shown == 0) sender.sendMessage(CommandSupport.msg(TextFormatting.GRAY, "chat.ae2enhanced.plan.running_none"));
    }

    public String name() { return "plan"; }

    public Permission permission() { return Permission.ANALYSIS; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender); return; }
        if ("stats".equalsIgnoreCase(args[1])) stats(sender);
        else if ("running".equalsIgnoreCase(args[1])) running(sender);
        else usage(sender);
    }

    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        return args.length == 2 ? CommandSupport.matching(args, SUBCOMMANDS) : Collections.emptyList();
    }
}
