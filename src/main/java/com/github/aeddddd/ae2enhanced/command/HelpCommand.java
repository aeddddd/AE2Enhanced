package com.github.aeddddd.ae2enhanced.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

final class HelpCommand implements CommandAdapter {
    private static void line(ICommandSender sender, TextFormatting color, String text) {
        sender.sendMessage(new TextComponentString(color + text));
    }

    public String name() { return "help"; }

    public Permission permission() { return Permission.PLAYER; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        line(sender, TextFormatting.AQUA, "========== AE2Enhanced Command Help ==========");
        line(sender, TextFormatting.YELLOW, "/ae2e channels <enable|disable|status>");
        line(sender, TextFormatting.GRAY, "  enable:  Enable AE2 channel checking (normal mode).");
        line(sender, TextFormatting.GRAY, "  disable: Disable AE2 channel checking (infinite channels).");
        line(sender, TextFormatting.GRAY, "  status:  Show current channel checking status.");
        line(sender, TextFormatting.YELLOW, "/ae2e fastpathing <enable|disable|status>");
        line(sender, TextFormatting.GRAY, "  enable:  Use experimental O(N) channel pathing (PR #8285 port).");
        line(sender, TextFormatting.GRAY, "  disable: Use vanilla AE2-UEL PathSegment pathing.");
        line(sender, TextFormatting.GRAY, "  status:  Show current fast pathing status.");
        line(sender, TextFormatting.YELLOW, "/ae2e recoverhd list");
        line(sender, TextFormatting.GRAY, "  List all hyperdimensional storage UUIDs (sorted by mtime).");
        line(sender, TextFormatting.YELLOW, "/ae2e recoverhd <uuid>");
        line(sender, TextFormatting.GRAY, "  Give the player a Hyperdimensional Controller block carrying the specified UUID.");
        line(sender, TextFormatting.YELLOW, "/ae2e testhd <uuid> <count>");
        line(sender, TextFormatting.GRAY, "  Inject <count> random enchanted gear types into the controller with the specified UUID.");
        line(sender, TextFormatting.YELLOW, "/ae2e migratefluids");
        line(sender, TextFormatting.GRAY, "  Convert AE2E ItemFluidDrop in all ME networks to ae2fc format.");
        line(sender, TextFormatting.GRAY, "  Requires ae2fc to be loaded and OP permission.");
        line(sender, TextFormatting.YELLOW, "/ae2e pd list|info|delete [player]|tp <player>|invite <player>|kick <player>");
        line(sender, TextFormatting.GRAY, "  Manage personal dimensions and team invites. All players can use these; no OP required.");
        line(sender, TextFormatting.GRAY, "  invite/kick: manage the team whitelist of your own dimension; tp: enter your own or a teammate's dimension.");
        line(sender, TextFormatting.YELLOW, "/ae2e specialcrafting <enable|disable|status>");
        line(sender, TextFormatting.GRAY, "  Toggle special crafting plans (self-referencing/cyclic productive recipes).");
        line(sender, TextFormatting.GRAY, "  When enabled, such plans are solved in closed form and run on Computation Cores.");
        line(sender, TextFormatting.YELLOW, "/ae2e chamberdebug");
        line(sender, TextFormatting.GRAY, "  Diagnose Singularity Chamber recipe index and held-item/looked-at chamber matching.");
        line(sender, TextFormatting.YELLOW, "/ae2e diag check [all|storage|channels|recipes|grid|personaldim]");
        line(sender, TextFormatting.GRAY, "  Run read-only health checks (default: all systems).");
        line(sender, TextFormatting.YELLOW, "/ae2e diag report");
        line(sender, TextFormatting.GRAY, "  Run all checks and write a full report to logs/ae2enhanced/.");
        line(sender, TextFormatting.YELLOW, "/ae2e debug [list|<switch> on|off|reset|status]");
        line(sender, TextFormatting.GRAY, "  Toggle diagnostic switches at runtime (overrides built-in defaults, not persisted).");
        line(sender, TextFormatting.GRAY, "  Switches: specialcrafting|virtualbatch|batchaggregate|perf");
        line(sender, TextFormatting.YELLOW, "/ae2e perf tps");
        line(sender, TextFormatting.GRAY, "  Show server TPS / tick time stats (5s / 1min / 2min windows).");
        line(sender, TextFormatting.YELLOW, "/ae2e perf top [machine|grid] [n]");
        line(sender, TextFormatting.GRAY, "  Top-N grids or machine classes by avg tick time (AE2 TickManager data).");
        line(sender, TextFormatting.YELLOW, "/ae2e perf grid <rank>");
        line(sender, TextFormatting.GRAY, "  Drill into the grid at the given rank from 'perf top grid'.");
        line(sender, TextFormatting.YELLOW, "/ae2e perf slow [thresholdMs] [n]");
        line(sender, TextFormatting.GRAY, "  List nodes whose avg tick time exceeds the threshold (default 1.0ms), with locations.");
        line(sender, TextFormatting.YELLOW, "/ae2e perf metrics");
        line(sender, TextFormatting.GRAY, "  Dump the internal metrics registry (counters/timers/gauges).");
        line(sender, TextFormatting.YELLOW, "/ae2e perf baseline <set|show|clear>");
        line(sender, TextFormatting.GRAY, "  Manage the performance baseline used by anomaly alerts (alerts broadcast to OPs every minute check).");
        line(sender, TextFormatting.YELLOW, "/ae2e perf export");
        line(sender, TextFormatting.GRAY, "  Export TPS/grid/machine/metrics snapshot to logs/ae2enhanced/perf-<ts>.json.");
        line(sender, TextFormatting.YELLOW, "/ae2e perf alert [multiplier]");
        line(sender, TextFormatting.GRAY, "  Show or set the alert threshold multiplier vs baseline (default 1.5).");
        line(sender, TextFormatting.YELLOW, "/ae2e plan stats");
        line(sender, TextFormatting.GRAY, "  Crafting plan path stats (special/dag/fallback/native counts, compute time, conservation check).");
        line(sender, TextFormatting.YELLOW, "/ae2e plan running");
        line(sender, TextFormatting.GRAY, "  List busy crafting CPUs with progress, co-processors and elapsed time.");
        line(sender, TextFormatting.YELLOW, "/ae2e harvest");
        line(sender, TextFormatting.GRAY, "  Harvest final recipe registries (crafting/furnace/ore dict/machines) to logs/ae2enhanced/harvest-<ts>.json.");
        line(sender, TextFormatting.GRAY, "  Dev/test workflow: snapshot feeds the modpack recipe simulation fixture (research/harvest/, not committed).");
        line(sender, TextFormatting.YELLOW, "/ae2e help");
        line(sender, TextFormatting.GRAY, "  Display this help message.");
        line(sender, TextFormatting.AQUA, "==============================================");
    }
}
