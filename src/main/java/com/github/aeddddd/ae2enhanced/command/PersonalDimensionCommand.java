package com.github.aeddddd.ae2enhanced.command;

import com.github.aeddddd.ae2enhanced.dimension.DimensionOwner;
import com.github.aeddddd.ae2enhanced.dimension.DimensionPreference;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.dimension.TeamDimEntry;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamDimensionAccess;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamInfo;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class PersonalDimensionCommand implements CommandAdapter {
    private static final String[] SUBCOMMANDS = {"list", "info", "delete", "tp", "enter", "mode", "floor"};
    private static final String[] MODES = {"auto", "team", "personal"};

    private static void usage(ICommandSender sender) {
        CommandSupport.usage(sender, "Usage: /ae2e pd <list|info|delete|tp|enter|mode>");
    }

    private static void list(MinecraftServer server, ICommandSender sender) {
        WorldServer world = server.getWorld(0);
        if (world == null) {
            sender.sendMessage(error("Cannot access the overworld."));
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(world);
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Personal dimensions ("
                + data.getAllEntries().size() + "), team dimensions (" + data.getAllTeamEntries().size() + "):"));
        for (PlayerDimEntry entry : data.getAllEntries()) {
            sender.sendMessage(new TextComponentString(String.format("  - PERSONAL %s (%s) dim=%d",
                    playerName(server, entry.playerId), entry.playerId, entry.dimensionId)));
        }
        for (TeamDimEntry entry : data.getAllTeamEntries()) {
            sender.sendMessage(new TextComponentString(String.format("  - TEAM %s dim=%d binding=%s",
                    entry.teamId, entry.dimensionId, entry.bindingId)));
        }
    }

    private static void info(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("team")) {
            if (!(sender instanceof EntityPlayerMP)) {
                sender.sendMessage(error("Team info requires a player."));
                return;
            }
            Optional<TeamInfo> team = TeamDimensionAccess.currentTeam(((EntityPlayerMP) sender).getUniqueID());
            if (!team.isPresent()) {
                sender.sendMessage(error("You are not in an FTB player team."));
                return;
            }
            TeamDimEntry entry = PersonalDimensionData.get(server.getWorld(0)).peekTeamEntry(team.get().getTeamId());
            sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Team " + team.get().getDisplayName()
                    + " (" + team.get().getTeamId() + ")"));
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Dimension: "
                    + (entry == null ? "not created" : entry.dimensionId)));
            if (entry != null) {
                sendEntryInfo(sender, entry.entryPoint, entry.rules, entry.floor.getFloorStyleId(), entry.floor.getRevision());
            }
            return;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("personal") && sender instanceof EntityPlayerMP) {
            infoPersonal(server, sender, ((EntityPlayerMP) sender).getUniqueID());
            return;
        }
        if (args.length >= 3) {
            UUID target = PlayerArgumentUtil.parseUuid(server, args[2]);
            if (target == null) {
                PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
                return;
            }
            infoPersonal(server, sender, target);
            return;
        }
        if (sender instanceof EntityPlayerMP) {
            DimensionOwner owner = PersonalDimensionManager.getOwnerByDimension(((EntityPlayerMP) sender).dimension);
            if (owner != null) {
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Current scope: " + owner.scope
                        + ", owner=" + owner.ownerId + ", dim=" + owner.dimensionId));
                sendEntryInfo(sender, owner.entryPoint, owner.rules, owner.floor.getFloorStyleId(), owner.floor.getRevision());
                return;
            }
            infoPersonal(server, sender, ((EntityPlayerMP) sender).getUniqueID());
            return;
        }
        CommandSupport.usage(sender, "Usage: /ae2e pd info <player|personal|team>");
    }

    private static void infoPersonal(MinecraftServer server, ICommandSender sender, UUID target) {
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(target);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Player has no personal dimension."));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Personal dimension of "
                + playerName(server, target) + ":"));
        sendEntryInfo(sender, entry.entryPoint, entry.rules, entry.floor.getFloorStyleId(), entry.floor.getRevision());
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Legacy guests: " + entry.allowedPlayers.size()));
    }

    private static void sendEntryInfo(ICommandSender sender, BlockPos entryPoint,
            com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules rules, String floorStyle, long floorRevision) {
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Entry point: " + pos(entryPoint)));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Mob spawning disabled: " + rules.disableMobSpawning
                + ", weather locked: " + rules.lockWeather + ", time locked: " + rules.lockTime));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Flight: " + rules.flightEnabled
                + ", speed: " + rules.movementSpeed));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Floor: " + floorStyle + ", revision=" + floorRevision));
    }

    private static void delete(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("team")) {
            if (!(sender instanceof EntityPlayerMP)) {
                sender.sendMessage(error("Team deletion requires a player or console-supported personal target."));
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) sender;
            Optional<TeamInfo> team = TeamDimensionAccess.currentTeam(player.getUniqueID());
            PersonalDimensionData data = PersonalDimensionData.get(server.getWorld(0));
            if (team.isPresent() && (TeamDimensionAccess.canManage(team.get().getTeamId(), player.getUniqueID())
                    || com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionPermissions.canManageOther(player))) {
                PersonalDimensionManager.onTeamDeleted(team.get().getTeamId());
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Team dimension quarantined; world files retained."));
            } else {
                sender.sendMessage(error("You are not a team MOD/OWNER or administrator."));
            }
            return;
        }
        UUID target;
        if (args.length < 3) {
            if (!(sender instanceof EntityPlayerMP)) {
                CommandSupport.usage(sender, "Usage: /ae2e pd delete [player|team]");
                return;
            }
            target = ((EntityPlayerMP) sender).getUniqueID();
        } else {
            target = PlayerArgumentUtil.parseUuid(server, args[2]);
            if (target == null) {
                PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
                return;
            }
            boolean self = sender instanceof EntityPlayerMP && ((EntityPlayerMP) sender).getUniqueID().equals(target);
            if (!self && !sender.canUseCommand(2, com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionPermissions.ADMIN)) {
                sender.sendMessage(CommandSupport.msg(TextFormatting.RED, "chat.ae2enhanced.command.no_permission"));
                return;
            }
        }
        if (PersonalDimensionManager.deleteDimension(target)) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Deleted personal dimension of "
                    + playerName(server, target) + "."));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No personal dimension to delete."));
        }
    }

    private static void teleport(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof EntityPlayerMP)) {
            CommandSupport.usage(sender, "Usage: /ae2e pd tp <player>");
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        UUID target = PlayerArgumentUtil.parseUuid(server, args[2]);
        if (target == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        if (PersonalDimensionManager.teleportPlayerToDimension(player, target)) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Teleported to "
                    + playerName(server, target) + "'s personal dimension."));
        } else {
            sender.sendMessage(error("Teleport failed or access denied."));
        }
    }

    private static void enter(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(error("Only players can enter a private dimension."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        DimensionPreference preference = args.length >= 3 ? parseMode(args[2])
                : PersonalDimensionData.get(player.getServerWorld()).getPreference(player.getUniqueID());
        if (preference == null || !PersonalDimensionManager.enterDimension(player, preference)) {
            sender.sendMessage(error("Unable to enter the requested dimension or access denied."));
        }
    }

    private static void mode(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(error("Only players have a dimension preference."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        PersonalDimensionData data = PersonalDimensionData.get(player.getServerWorld());
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Dimension preference: "
                    + data.getPreference(player.getUniqueID())));
            return;
        }
        DimensionPreference preference = parseMode(args[2]);
        if (preference == null) {
            CommandSupport.usage(sender, "Usage: /ae2e pd mode <auto|team|personal>");
            return;
        }
        data.setPreference(player.getUniqueID(), preference);
        PersonalDimensionManager.sendInfoToPlayer(player);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Dimension preference set to "
                + preference + "; existing worlds were not moved."));
    }

    private static DimensionPreference parseMode(String value) {
        if (value == null) {
            return null;
        }
        switch (value.toLowerCase(Locale.ROOT)) {
            case "auto": return DimensionPreference.AUTO;
            case "team": return DimensionPreference.TEAM;
            case "personal": return DimensionPreference.PERSONAL;
            default: return null;
        }
    }

    private static void floor(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(error("Only players can configure a dimension floor."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        DimensionOwner owner = PersonalDimensionManager.getOwnerByDimension(player.dimension);
        if (owner == null) {
            sender.sendMessage(error("This command must be used inside a personal or team dimension."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Floor style=" + owner.floor.getFloorStyleId()
                    + ", revision=" + owner.floor.getRevision() + ", colors=" + java.util.Arrays.toString(owner.floor.getColorScheme())));
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if ("apply".equals(action)) {
            if (com.github.aeddddd.ae2enhanced.dimension.FloorRepaintManager.startRepaint(player)) {
                PersonalDimensionManager.sendInfoToPlayer(player);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Floor repaint queued; unloaded chunks resume on load."));
            } else {
                sender.sendMessage(error("You cannot manage this floor."));
            }
            return;
        }
        int[] colors = owner.floor.getColorScheme();
        String style = owner.floor.getFloorStyleId();
        if ("style".equals(action) && args.length >= 4) {
            style = args[3];
        } else if ("color".equals(action) && args.length >= 5) {
            int role;
            try {
                role = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(error("Color role must be an integer from 0 to 15."));
                return;
            }
            if (role < 0 || role >= colors.length) {
                sender.sendMessage(error("Color role must be from 0 to 15."));
                return;
            }
            net.minecraft.item.EnumDyeColor dye = null;
            for (net.minecraft.item.EnumDyeColor candidate : net.minecraft.item.EnumDyeColor.values()) {
                if (candidate.getName().equalsIgnoreCase(args[4])) {
                    dye = candidate;
                    break;
                }
            }
            if (dye == null) {
                sender.sendMessage(error("Unknown dye color."));
                return;
            }
            colors[role] = dye.getMetadata();
        } else {
            CommandSupport.usage(sender, "Usage: /ae2e pd floor <style <id>|color <0-15> <dye>|apply>");
            return;
        }
        if (com.github.aeddddd.ae2enhanced.dimension.FloorRepaintManager.updateFloor(player, style, colors)) {
            PersonalDimensionManager.sendInfoToPlayer(player);
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Floor definition updated; use /ae2e pd floor apply to repaint."));
        } else {
            sender.sendMessage(error("Invalid style or insufficient permission."));
        }
    }

    private static String playerName(MinecraftServer server, UUID id) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(id);
        return player != null ? player.getName() : id.toString();
    }

    private static String pos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static TextComponentString error(String message) {
        return new TextComponentString(TextFormatting.RED + "[AE2E] " + message);
    }

    @Override
    public String name() {
        return "pd";
    }

    @Override
    public Permission permission() {
        return Permission.PLAYER;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list": list(server, sender); break;
            case "info": info(server, sender, args); break;
            case "delete": delete(server, sender, args); break;
            case "tp": teleport(server, sender, args); break;
            case "enter": enter(sender, args); break;
            case "mode": mode(sender, args); break;
            case "floor": floor(sender, args); break;
            case "invite":
            case "kick":
                sender.sendMessage(error("Per-player permission management was removed; use FTB player teams."));
                break;
            default:
                sender.sendMessage(error("Unknown personal dimension subcommand: " + args[1]));
                usage(sender);
                break;
        }
    }

    @Override
    public List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 2) {
            return CommandSupport.matching(args, SUBCOMMANDS);
        }
        if (args.length >= 4 && "floor".equalsIgnoreCase(args[1])) {
            if (args.length == 4 && "color".equalsIgnoreCase(args[2])) {
                return CommandSupport.matching(args, "0", "1", "2", "3", "4", "5", "6", "7",
                        "8", "9", "10", "11", "12", "13", "14", "15");
            }
            if (args.length == 5 && "color".equalsIgnoreCase(args[2])) {
                String[] dyes = new String[net.minecraft.item.EnumDyeColor.values().length];
                for (int i = 0; i < dyes.length; i++) {
                    dyes[i] = net.minecraft.item.EnumDyeColor.values()[i].getName();
                }
                return CommandSupport.matching(args, dyes);
            }
            return Collections.emptyList();
        }
        if (args.length != 3) {
            return Collections.emptyList();
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("mode".equals(action) || "enter".equals(action)) {
            return CommandSupport.matching(args, MODES);
        }
        if ("floor".equals(action)) {
            return CommandSupport.matching(args, "style", "color", "apply");
        }
        if ("info".equals(action)) {
            List<String> values = new ArrayList<>(Arrays.asList("personal", "team"));
            values.addAll(Arrays.asList(server.getOnlinePlayerNames()));
            return CommandSupport.matching(args, values.toArray(new String[0]));
        }
        if ("delete".equals(action)) {
            List<String> values = new ArrayList<>();
            values.add("team");
            values.addAll(Arrays.asList(server.getOnlinePlayerNames()));
            return CommandSupport.matching(args, values.toArray(new String[0]));
        }
        if ("tp".equals(action)) {
            return CommandSupport.matching(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
