package com.github.aeddddd.ae2enhanced.command;

import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/** Forge 命令入口；子命令实现与补全均归各自 adapter。 */
public class CommandAE2Enhanced extends CommandBase {
    private static final List<CommandAdapter> ADAPTERS = new ArrayList<>();
    private static final Map<String, CommandAdapter> BY_NAME = new LinkedHashMap<>();

    static {
        register(new ChannelsCommand());
        register(new FastPathingCommand());
        register(new SpecialCraftingCommand());
        register(new RecoverHdCommand());
        register(new TestHdCommand());
        register(new MigrateFluidsCommand());
        register(new ChamberDebugCommand());
        register(new DiagCommand());
        register(new DebugCommand());
        register(new PerfCommand());
        register(new PlanCommand());
        register(new HarvestCommand());
        register(new PersonalDimensionCommand());
        register(new HelpCommand());
    }

    private static void register(CommandAdapter adapter) {
        ADAPTERS.add(adapter);
        BY_NAME.put(adapter.name(), adapter);
    }

    @Override
    @Nonnull
    public String getName() {
        return "ae2enhanced";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/ae2e <channels|fastpathing|specialcrafting|recoverhd|testhd|migratefluids|chamberdebug|diag|debug|perf|harvest|pd|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    private static boolean allowed(ICommandSender sender, CommandAdapter adapter) {
        switch (adapter.permission()) {
            case ADMIN:
                return sender.canUseCommand(2, com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionPermissions.ADMIN);
            case ANALYSIS:
                return sender.canUseCommand(ServerConfig.current().diagnostics.analysisPermissionLevel,
                        com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionPermissions.ADMIN);
            default:
                return true;
        }
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    @Nonnull
    public List<String> getAliases() {
        return Collections.singletonList("ae2e");
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: " + getUsage(sender)));
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        CommandAdapter adapter = BY_NAME.get(name);
        if (adapter == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Unknown subcommand: " + name));
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + getUsage(sender)));
            return;
        }
        if (!allowed(sender, adapter)) {
            sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.command.no_permission"));
            return;
        }
        adapter.execute(server, sender, args);
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
            @Nonnull String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (CommandAdapter adapter : ADAPTERS) {
                if (allowed(sender, adapter)) options.add(adapter.name());
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, options);
        }
        CommandAdapter adapter = BY_NAME.get(args[0].toLowerCase(Locale.ROOT));
        if (adapter == null || !allowed(sender, adapter)) return Collections.emptyList();
        return adapter.tabComplete(server, sender, args, targetPos);
    }
}
