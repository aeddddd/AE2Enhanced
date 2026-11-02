package com.github.aeddddd.ae2enhanced.command;

import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IAEConfigAccessor;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import java.util.EnumSet;

final class ChannelsCommand implements CommandAdapter {
    private static void setEnabled(boolean enabled) {
        try {
            AEConfig config = AEConfig.instance();
            EnumSet<AEFeature> flags = ((IAEConfigAccessor) (Object) config).ae2e$getFeatureFlags();
            if (enabled) flags.add(AEFeature.CHANNELS); else flags.remove(AEFeature.CHANNELS);
            net.minecraftforge.common.config.Property property = config.get("Features.NetworkFeatures", "Channels", true);
            property.set(enabled);
            config.save();
        } catch (Exception exception) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to toggle AE2 channel feature.", exception);
        }
    }

    public String name() { return "channels"; }

    public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { CommandSupport.usage(sender, "Usage: /ae2e channels <enable|disable|status>"); return; }
        switch (args[1].toLowerCase()) {
            case "enable":
                setEnabled(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking enabled."));
                break;
            case "disable":
                setEnabled(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking disabled (infinite channels)."));
                break;
            case "status":
                boolean enabled = AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS);
                String status = enabled ? TextFormatting.GREEN + "Enabled" : TextFormatting.YELLOW + "Disabled (infinite channels)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] AE2 channel checking status: " + status));
                break;
            default:
                CommandSupport.usage(sender, "Usage: /ae2e channels <enable|disable|status>");
        }
    }

    public java.util.List<String> tabComplete(net.minecraft.server.MinecraftServer server, ICommandSender sender,
            String[] args, net.minecraft.util.math.BlockPos targetPos) {
        return args.length == 2 ? CommandSupport.matching(args, CommandSupport.TOGGLE_OPTIONS) : java.util.Collections.emptyList();
    }
}

final class FastPathingCommand implements CommandAdapter {
    private static void setEnabled(boolean enabled) {
        AE2EnhancedConfig.channelPathing.fastPathing = enabled;
        ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
        ServerConfig.publish();
    }

    public String name() { return "fastpathing"; }

    public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { CommandSupport.usage(sender, "Usage: /ae2e fastpathing <enable|disable|status>"); return; }
        switch (args[1].toLowerCase()) {
            case "enable":
                setEnabled(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Experimental fast channel pathing enabled."));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Existing networks will switch on next repath."));
                break;
            case "disable":
                setEnabled(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Experimental fast channel pathing disabled."));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Networks will fall back to vanilla AE2-UEL pathing on next repath."));
                break;
            case "status":
                boolean enabled = ServerConfig.current().channelPathing.fastPathing;
                String status = enabled ? TextFormatting.GREEN + "Enabled (O(N) hierarchical BFS + iterative DFS)"
                        : TextFormatting.YELLOW + "Disabled (vanilla PathSegment)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Fast channel pathing status: " + status));
                break;
            default:
                CommandSupport.usage(sender, "Usage: /ae2e fastpathing <enable|disable|status>");
        }
    }

    public java.util.List<String> tabComplete(net.minecraft.server.MinecraftServer server, ICommandSender sender,
            String[] args, net.minecraft.util.math.BlockPos targetPos) {
        return args.length == 2 ? CommandSupport.matching(args, CommandSupport.TOGGLE_OPTIONS) : java.util.Collections.emptyList();
    }
}

final class SpecialCraftingCommand implements CommandAdapter {
    private static void setEnabled(boolean enabled) {
        AE2EnhancedConfig.crafting.specialCrafting = enabled;
        ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
        ServerConfig.publish();
    }

    public String name() { return "specialcrafting"; }

    public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) { CommandSupport.usage(sender, "Usage: /ae2e specialcrafting <enable|disable|status>"); return; }
        switch (args[1].toLowerCase()) {
            case "enable":
                setEnabled(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Special crafting plans enabled."));
                break;
            case "disable":
                setEnabled(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Special crafting plans disabled (vanilla calculation)."));
                break;
            case "status":
                boolean enabled = ServerConfig.current().crafting.specialCrafting;
                String status = enabled ? TextFormatting.GREEN + "Enabled (closed-form solver, Computation Core execution)"
                        : TextFormatting.YELLOW + "Disabled (vanilla calculation)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Special crafting status: " + status));
                break;
            default:
                CommandSupport.usage(sender, "Usage: /ae2e specialcrafting <enable|disable|status>");
        }
    }

    public java.util.List<String> tabComplete(net.minecraft.server.MinecraftServer server, ICommandSender sender,
            String[] args, net.minecraft.util.math.BlockPos targetPos) {
        return args.length == 2 ? CommandSupport.matching(args, CommandSupport.TOGGLE_OPTIONS) : java.util.Collections.emptyList();
    }
}
