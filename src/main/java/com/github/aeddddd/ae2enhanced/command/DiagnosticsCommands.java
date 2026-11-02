package com.github.aeddddd.ae2enhanced.command;

import com.github.aeddddd.ae2enhanced.diag.DiagReport;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.check.CheckResult;
import com.github.aeddddd.ae2enhanced.diag.check.DiagChecks;
import com.github.aeddddd.ae2enhanced.diag.check.SystemCheck;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DiagCommand implements CommandAdapter {
    private static final String[] ACTIONS = {"check", "report"};
    private static final String[] SYSTEMS = {"all", "storage", "channels", "recipes", "grid", "personaldim"};

    private static void usage(ICommandSender sender) {
        CommandSupport.usage(sender, "Usage: /ae2e diag <check [system]|report>");
    }

    private static void check(MinecraftServer server, ICommandSender sender, String[] args) {
        String system = args.length >= 3 ? args[2].toLowerCase() : "all";
        if ("all".equals(system)) {
            int warnings = 0;
            int errors = 0;
            for (SystemCheck check : DiagChecks.all()) {
                List<CheckResult> results = DiagChecks.runOne(server, check);
                sendResults(sender, check, results);
                warnings += DiagChecks.countLevel(results, CheckResult.Level.WARN);
                errors += DiagChecks.countLevel(results, CheckResult.Level.ERROR);
            }
            sender.sendMessage(CommandSupport.msg(errors > 0 ? TextFormatting.RED
                    : warnings > 0 ? TextFormatting.YELLOW : TextFormatting.GREEN,
                    "chat.ae2enhanced.diag.all_done", warnings, errors));
            return;
        }
        SystemCheck check = DiagChecks.byName(system);
        if (check == null) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.diag.unknown_system",
                    system, "all|" + String.join("|", systemNames())));
            return;
        }
        sendResults(sender, check, DiagChecks.runOne(server, check));
    }

    private static void sendResults(ICommandSender sender, SystemCheck check, List<CheckResult> results) {
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.diag.header",
                new TextComponentTranslation(check.displayName()), check.name()));
        for (CheckResult result : results) {
            TextFormatting color = result.level == CheckResult.Level.OK ? TextFormatting.GREEN
                    : result.level == CheckResult.Level.WARN ? TextFormatting.YELLOW : TextFormatting.RED;
            TextComponentString line = new TextComponentString(color + "  [" + result.level + "] ");
            line.appendSibling(result.toComponent());
            sender.sendMessage(line);
        }
    }

    private static void report(MinecraftServer server, ICommandSender sender) {
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.diag.report_generating"));
        File file = DiagReport.generate(server);
        if (file == null) sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.diag.report_failed"));
        else sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.diag.report_done", file.getPath()));
    }

    private static List<String> systemNames() {
        List<String> names = new ArrayList<>();
        for (SystemCheck check : DiagChecks.all()) names.add(check.name());
        return names;
    }

    public String name() { return "diag"; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { usage(sender); return; }
        if ("check".equalsIgnoreCase(args[1])) check(server, sender, args);
        else if ("report".equalsIgnoreCase(args[1])) report(server, sender);
        else usage(sender);
    }

    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 2) return CommandSupport.matching(args, ACTIONS);
        if (args.length == 3 && "check".equalsIgnoreCase(args[1])) return CommandSupport.matching(args, SYSTEMS);
        return Collections.emptyList();
    }
}

final class DebugCommand implements CommandAdapter {
    private static final String[] ACTIONS = {"on", "off", "reset", "status"};

    private static void sendState(ICommandSender sender, String name) {
        boolean enabled = DiagSwitch.isEnabled(name);
        ITextComponent state = CommandSupport.msg(enabled ? TextFormatting.GREEN : TextFormatting.GRAY,
                enabled ? "chat.ae2enhanced.debug.state_on" : "chat.ae2enhanced.debug.state_off");
        ITextComponent source = CommandSupport.msg(TextFormatting.GRAY, DiagSwitch.hasOverride(name)
                ? "chat.ae2enhanced.debug.source_override" : "chat.ae2enhanced.debug.source_default");
        sender.sendMessage(CommandSupport.msg(TextFormatting.YELLOW, "chat.ae2enhanced.debug.state", name, state, source));
    }

    public String name() { return "debug"; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.debug.switches"));
            for (String name : DiagSwitch.names()) sendState(sender, name);
            return;
        }
        String name = args[1].toLowerCase();
        if (!DiagSwitch.isKnown(name)) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.debug.unknown",
                    name, String.join("|", DiagSwitch.names())));
            return;
        }
        if (args.length < 3 || "status".equalsIgnoreCase(args[2])) { sendState(sender, name); return; }
        switch (args[2].toLowerCase()) {
            case "on":
                DiagSwitch.setOverride(name, true);
                sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.debug.on", name));
                break;
            case "off":
                DiagSwitch.setOverride(name, false);
                sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.debug.off", name));
                break;
            case "reset":
                DiagSwitch.clearOverride(name);
                sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.debug.reset", name));
                break;
            default:
                CommandSupport.usage(sender, "Usage: /ae2e debug " + name + " <on|off|reset|status>");
        }
    }

    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 2) {
            List<String> options = new ArrayList<>(DiagSwitch.names());
            options.add("list");
            return CommandSupport.matching(args, options);
        }
        if (args.length == 3 && DiagSwitch.isKnown(args[1].toLowerCase())) return CommandSupport.matching(args, ACTIONS);
        return Collections.emptyList();
    }
}

final class HarvestCommand implements CommandAdapter {
    public String name() { return "harvest"; }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        sender.sendMessage(CommandSupport.msg(TextFormatting.AQUA, "chat.ae2enhanced.harvest.start"));
        File file = RecipeHarvester.harvest();
        if (file == null) sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.harvest.failed"));
        else sender.sendMessage(CommandSupport.msg(TextFormatting.GREEN, "chat.ae2enhanced.harvest.done", file.getPath()));
    }
}
