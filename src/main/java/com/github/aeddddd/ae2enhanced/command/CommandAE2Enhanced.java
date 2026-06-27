package com.github.aeddddd.ae2enhanced.command;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.hooks.TickHandler;
import appeng.me.Grid;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcCompat;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidCompat;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Config;
import appeng.util.item.AEItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;

/**
 * AE2Enhanced main command.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>{@code /ae2e spgc} — Manual Smart Pattern garbage collection</li>
 *   <li>{@code /ae2e channels enable|disable|status} — Toggle AE2 channel checking</li>
 *   <li>{@code /ae2e fastpathing enable|disable|status} — Toggle experimental O(N) channel pathing</li>
 *   <li>{@code /ae2e recoverhd list} — List all hyperdimensional storage UUIDs</li>
 *   <li>{@code /ae2e recoverhd <uuid>} — Get controller block with specified UUID</li>
 * </ul>
 */
public class CommandAE2Enhanced extends CommandBase {

    @Override
    @Nonnull
    public String getName() {
        return "ae2enhanced";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/ae2e <spgc|channels|fastpathing|recoverhd|testhd|migratefluids|pd|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @Nonnull
    public List<String> getAliases() {
        return java.util.Collections.singletonList("ae2e");
    }

    private static final String[] SUBCOMMANDS = {
            "spgc", "channels", "fastpathing", "recoverhd", "testhd", "migratefluids", "pd", "help"
    };
    private static final String[] TOGGLE_OPTIONS = {"enable", "disable", "status"};
    private static final String[] PD_SUBCOMMANDS = {
            "list", "info", "delete", "tp", "invite", "kick", "setperm"
    };
    private static final String[] PD_PERMISSIONS;
    static {
        PersonalDimPermission[] values = PersonalDimPermission.values();
        PD_PERMISSIONS = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            PD_PERMISSIONS[i] = values[i].name().toLowerCase();
        }
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                           @Nonnull String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if ("channels".equals(sub) || "fastpathing".equals(sub)) {
                return CommandBase.getListOfStringsMatchingLastWord(args, TOGGLE_OPTIONS);
            }
            if ("recoverhd".equals(sub)) {
                List<String> list = new ArrayList<>();
                list.add("list");
                list.addAll(collectHdUuids(server));
                return CommandBase.getListOfStringsMatchingLastWord(args, list);
            }
            if ("testhd".equals(sub)) {
                return CommandBase.getListOfStringsMatchingLastWord(args, collectHdUuids(server));
            }
            if ("pd".equals(sub)) {
                return CommandBase.getListOfStringsMatchingLastWord(args, PD_SUBCOMMANDS);
            }
        }
        if (args.length == 3 && "pd".equals(sub)) {
            String pdSub = args[1].toLowerCase();
            if ("setperm".equals(pdSub)) {
                return CommandBase.getListOfStringsMatchingLastWord(args, PD_PERMISSIONS);
            }
            if ("tp".equals(pdSub) || "info".equals(pdSub) || "delete".equals(pdSub)
                    || "invite".equals(pdSub) || "kick".equals(pdSub)) {
                return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            }
        }
        if (args.length == 4 && "pd".equals(sub) && "setperm".equals(args[1].toLowerCase())) {
            return CommandBase.getListOfStringsMatchingLastWord(args, "true", "false");
        }
        return Collections.emptyList();
    }

    private static List<String> collectHdUuids(@Nonnull MinecraftServer server) {
        List<String> result = new ArrayList<>();
        WorldServer world = server.getWorld(0);
        if (world == null) {
            return result;
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists()) {
            return result;
        }
        File[] entries = storageDir.listFiles((dir, name) -> {
            if (name.startsWith("smartpattern_")) return false;
            File f = new File(dir, name);
            return f.isDirectory() || name.endsWith(".dat");
        });
        if (entries == null) {
            return result;
        }
        for (File entry : entries) {
            String name = entry.getName();
            result.add(entry.isDirectory() ? name : name.substring(0, name.length() - 4));
        }
        return result;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: " + getUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spgc":
                executeSpgc(sender);
                break;
            case "channels":
                executeChannels(sender, args);
                break;
            case "fastpathing":
                executeFastPathing(sender, args);
                break;
            case "recoverhd":
                executeRecoverHd(server, sender, args);
                break;
            case "testhd":
                executeTestHd(server, sender, args);
                break;
            case "migratefluids":
                executeMigrateFluids(sender);
                break;
            case "pd":
                executePersonalDimension(server, sender, args);
                break;
            case "help":
                executeHelp(sender);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Unknown subcommand: " + sub));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + getUsage(sender)));
        }
    }

    // ---- help ----

    private void executeHelp(@Nonnull ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "========== AE2Enhanced Command Help =========="));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e spgc"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Manually trigger Smart Pattern garbage collection."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e channels <enable|disable|status>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  enable:  Enable AE2 channel checking (normal mode)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  disable: Disable AE2 channel checking (infinite channels)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  status:  Show current channel checking status."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e fastpathing <enable|disable|status>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  enable:  Use experimental O(N) channel pathing (PR #8285 port)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  disable: Use vanilla AE2-UEL PathSegment pathing."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  status:  Show current fast pathing status."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e recoverhd list"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  List all hyperdimensional storage UUIDs (sorted by mtime)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e recoverhd <uuid>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Give the player a Hyperdimensional Controller block carrying the specified UUID."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e testhd <uuid> <count>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Inject <count> random enchanted gear types into the controller with the specified UUID."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e migratefluids"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Convert AE2E ItemFluidDrop in all ME networks to ae2fc format."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Requires ae2fc to be loaded and OP permission."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e pd list|info|delete|tp|invite|kick|setperm"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Manage personal dimensions."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e help"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Display this help message."));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "=============================================="));
    }

    // ---- personal dimension ----

    private void executePersonalDimension(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd <list|info|delete|tp|invite|kick|setperm>"));
            return;
        }
        String pdSub = args[1].toLowerCase();
        switch (pdSub) {
            case "list":
                executePdList(server, sender);
                break;
            case "info":
                executePdInfo(server, sender, args);
                break;
            case "delete":
                executePdDelete(server, sender, args);
                break;
            case "tp":
                executePdTp(server, sender, args);
                break;
            case "invite":
                executePdInvite(server, sender, args);
                break;
            case "kick":
                executePdKick(server, sender, args);
                break;
            case "setperm":
                executePdSetPerm(server, sender, args);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Unknown personal dimension subcommand: " + pdSub));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Usage: /ae2e pd <list|info|delete|tp|invite|kick|setperm>"));
        }
    }

    private void executePdList(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        WorldServer world = server.getWorld(0);
        if (world == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Cannot access the overworld."));
            return;
        }
        com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData data =
                com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData.get(world);
        java.util.Collection<PlayerDimEntry> entries = data.getAllEntries();
        if (entries.isEmpty()) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No personal dimensions found."));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Personal dimensions (" + entries.size() + "):"));
        for (PlayerDimEntry entry : entries) {
            String playerName = resolvePlayerName(server, entry.playerId);
            boolean online = server.getPlayerList().getPlayerByUUID(entry.playerId) != null;
            String line = String.format("  - %s (%s) dim=%d %s",
                    playerName,
                    entry.playerId,
                    entry.dimensionId == Integer.MIN_VALUE ? -1 : entry.dimensionId,
                    online ? TextFormatting.GREEN + "[online]" : TextFormatting.GRAY + "[offline]");
            sender.sendMessage(new TextComponentString(line));
        }
    }

    private void executePdInfo(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd info <player>"));
            return;
        }
        UUID targetId = PlayerArgumentUtil.parseUuid(server, args[2]);
        if (targetId == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(targetId);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Player has no personal dimension."));
            return;
        }
        String playerName = resolvePlayerName(server, entry.playerId);
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Personal dimension info for " + playerName + ":"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Dimension ID: " + entry.dimensionId));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Entry point: " + formatBlockPos(entry.entryPoint)));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Mob spawning: " + (entry.rules.disableMobSpawning ? "disabled" : "enabled")));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Lock weather: " + entry.rules.lockWeather));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Lock time: " + entry.rules.lockTime + " (daylightCycle=" + entry.rules.daylightCycle + ", time=" + entry.rules.timeValue + ")"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Flight: " + entry.rules.flightEnabled + ", Speed: " + entry.rules.movementSpeed));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Allowed players: " + entry.allowedPlayers.size()));
        for (UUID id : entry.allowedPlayers) {
            String name = resolvePlayerName(server, id);
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "    - " + name + ": " + formatPermissions(entry.getPermissions(id))));
        }
    }

    private void executePdDelete(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd delete <player>"));
            return;
        }
        UUID targetId = PlayerArgumentUtil.parseUuid(server, args[2]);
        if (targetId == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        String name = resolvePlayerName(server, targetId);
        if (PersonalDimensionManager.deleteDimension(targetId)) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Deleted personal dimension of " + name + ". It will be recreated on next entry."));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Player " + name + " has no personal dimension to delete."));
        }
    }

    private void executePdTp(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd tp <player>"));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        UUID targetId = PlayerArgumentUtil.parseUuid(server, args[2]);
        if (targetId == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        if (PersonalDimensionManager.teleportPlayerToDimension(player, targetId)) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Teleported to " + resolvePlayerName(server, targetId) + "'s personal dimension."));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Failed to teleport. The player has no personal dimension or you don't have permission to enter."));
        }
    }

    private void executePdInvite(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd invite <player>"));
            return;
        }
        EntityPlayerMP owner = (EntityPlayerMP) sender;
        EntityPlayerMP target = PlayerArgumentUtil.parseOnlinePlayer(server, sender, args[2]);
        if (target == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        if (target.getUniqueID().equals(owner.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] You don't need to invite yourself."));
            return;
        }
        PersonalDimensionManager.invitePlayer(owner.getUniqueID(), target.getUniqueID());
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Invited " + target.getName() + " to your personal dimension."));
    }

    private void executePdKick(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd kick <player>"));
            return;
        }
        EntityPlayerMP owner = (EntityPlayerMP) sender;
        UUID targetId = PlayerArgumentUtil.parseUuid(server, args[2]);
        if (targetId == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        if (targetId.equals(owner.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] You cannot kick yourself."));
            return;
        }
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(owner.getUniqueID());
        if (entry != null && entry.dimensionId != Integer.MIN_VALUE) {
            EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(targetId);
            if (target != null && target.dimension == entry.dimensionId) {
                PersonalDimensionManager.teleportToReturnPoint(target);
            }
        }
        PersonalDimensionManager.kickPlayer(owner.getUniqueID(), targetId);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Kicked " + resolvePlayerName(server, targetId) + " from your personal dimension."));
    }

    private void executePdSetPerm(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e pd setperm <player> <enter|build|interact|manage_rules> <true|false>"));
            return;
        }
        EntityPlayerMP owner = (EntityPlayerMP) sender;
        UUID targetId = PlayerArgumentUtil.parseUuid(server, args[2]);
        if (targetId == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        PersonalDimPermission perm;
        try {
            perm = PersonalDimPermission.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Unknown permission: " + args[3]));
            return;
        }
        boolean value = Boolean.parseBoolean(args[4]);
        PersonalDimensionManager.setPermission(owner.getUniqueID(), targetId, perm, value);
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Set permission " + perm.name().toLowerCase() + " for " + resolvePlayerName(server, targetId) + " to " + value + "."));
    }

    private static String resolvePlayerName(@Nonnull MinecraftServer server, @Nonnull UUID playerId) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) return player.getName();
        // 尝试从 usercache.json 解析（若存在）
        return playerId.toString();
    }

    private static String formatBlockPos(net.minecraft.util.math.BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private static String formatPermissions(java.util.Set<PersonalDimPermission> perms) {
        if (perms.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (PersonalDimPermission p : perms) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p.name().toLowerCase());
        }
        return sb.toString();
    }

    // ---- migrate fluids ----

    private void executeMigrateFluids(@Nonnull ICommandSender sender) {
        if (!Ae2fcCompat.AE2FC_LOADED) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] ae2fc is not loaded, no migration needed."));
            return;
        }

        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command must be executed by a player."));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        IActionSource source = new PlayerSource(player, null);

        int convertedStacks = 0;
        long convertedAmount = 0;

        try {
            IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

            for (Grid grid : TickHandler.INSTANCE.getGridList()) {
                GridStorageCache storageCache = grid.getCache(GridStorageCache.class);
                if (storageCache == null) continue;

                appeng.api.storage.IMEMonitor<IAEItemStack> itemMonitor = storageCache.getInventory(itemChannel);
                if (itemMonitor == null) continue;

                appeng.api.storage.data.IItemList<IAEItemStack> itemList = itemChannel.createList();
                itemMonitor.getAvailableItems(itemList);

                for (IAEItemStack stack : itemList) {
                    if (stack == null || stack.getStackSize() <= 0) continue;

                    ItemStack mcStack = stack.createItemStack();
                    if (!ItemFluidDrop.isFluidDrop(mcStack)) continue;

                    FluidStack fluid = ItemFluidDrop.getFluidStack(mcStack);
                    if (fluid == null || fluid.getFluid() == null) continue;

                    // 提取全部 AE2E fluid drop
                    IAEItemStack toExtract = stack.copy();
                    IAEItemStack notExtracted = itemMonitor.extractItems(toExtract, Actionable.MODULATE, source);
                    long extracted = stack.getStackSize() - (notExtracted != null ? notExtracted.getStackSize() : 0);
                    if (extracted <= 0) continue;

                    // 转换为 ae2fc 格式
                    FluidStack toConvert = fluid.copy();
                    toConvert.amount = (int) Math.min(extracted, Integer.MAX_VALUE);
                    ItemStack ae2fcDrop = Ae2fcFluidCompat.createFluidDrop(toConvert);
                    if (ae2fcDrop.isEmpty()) {
                        // 转换失败,把原 drop 还回去
                        ItemStack returnStack = mcStack.copy();
                        returnStack.setCount((int) extracted);
                        itemMonitor.injectItems(AEItemStack.fromItemStack(returnStack), Actionable.MODULATE, source);
                        continue;
                    }

                    // 注回物品通道,由 ae2fc 接管
                    IAEItemStack toInsert = AEItemStack.fromItemStack(ae2fcDrop);
                    IAEItemStack notInserted = itemMonitor.injectItems(toInsert, Actionable.MODULATE, source);
                    long inserted = toConvert.amount - (notInserted != null ? notInserted.getStackSize() : 0);
                    convertedAmount += inserted;
                    convertedStacks++;
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to migrate fluid drops", e);
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Migration failed: " + e.getMessage()));
            return;
        }

        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Migrated " + convertedStacks + " AE2E fluid drop stacks (" + convertedAmount + " mB) to ae2fc format."));
    }

    // ---- spgc ----

    private void executeSpgc(@Nonnull ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Scanning ME interfaces and cleaning orphaned Smart Pattern files..."));
        int deleted = SmartPatternGarbageCollector.runManualGC();
        if (deleted < 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] An error occurred during cleanup. Check the server log."));
        } else if (deleted == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] No orphaned files found."));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Cleaned up " + deleted + " orphaned Smart Pattern file(s)."));
        }
    }

    // ---- channels ----

    private void executeChannels(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e channels <enable|disable|status>"));
            return;
        }
        String action = args[1].toLowerCase();
        AEConfig config = AEConfig.instance();
        switch (action) {
            case "enable":
                setChannelsEnabled(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking enabled."));
                break;
            case "disable":
                setChannelsEnabled(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking disabled (infinite channels)."));
                break;
            case "status":
                boolean enabled = config.isFeatureEnabled(AEFeature.CHANNELS);
                String status = enabled ? TextFormatting.GREEN + "Enabled" : TextFormatting.YELLOW + "Disabled (infinite channels)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] AE2 channel checking status: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e channels <enable|disable|status>"));
        }
    }

    private void setChannelsEnabled(boolean enabled) {
        try {
            AEConfig config = AEConfig.instance();
            Field field = AEConfig.class.getDeclaredField("featureFlags");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            EnumSet<AEFeature> flags = (EnumSet<AEFeature>) field.get(config);
            if (enabled) {
                flags.add(AEFeature.CHANNELS);
            } else {
                flags.remove(AEFeature.CHANNELS);
            }
            net.minecraftforge.common.config.Property prop = config.get("Features.NetworkFeatures", "Channels", true);
            prop.set(enabled);
            config.save();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to toggle AE2 channel feature.", e);
        }
    }

    // ---- fastpathing ----

    private void executeFastPathing(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e fastpathing <enable|disable|status>"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "enable":
                setFastPathing(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Experimental fast channel pathing enabled."));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Existing networks will switch on next repath."));
                break;
            case "disable":
                setFastPathing(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Experimental fast channel pathing disabled."));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Networks will fall back to vanilla AE2-UEL pathing on next repath."));
                break;
            case "status":
                boolean enabled = AE2EnhancedConfig.channelPathing.fastPathing;
                String status = enabled
                        ? TextFormatting.GREEN + "Enabled (O(N) hierarchical BFS + iterative DFS)"
                        : TextFormatting.YELLOW + "Disabled (vanilla PathSegment)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Fast channel pathing status: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e fastpathing <enable|disable|status>"));
        }
    }

    private void setFastPathing(boolean enabled) {
        AE2EnhancedConfig.channelPathing.fastPathing = enabled;
        ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
    }

    // ---- recoverhd ----

    private void executeRecoverHd(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e recoverhd list  or  /ae2e recoverhd <uuid>"));
            return;
        }
        String arg = args[1].toLowerCase();
        if ("list".equals(arg)) {
            listHdUuids(sender);
        } else {
            giveHdController(server, sender, arg);
        }
    }

    private void listHdUuids(@Nonnull ICommandSender sender) {
        WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        if (world == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Cannot access the overworld."));
            return;
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists()) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No hyperdimensional storage data found."));
            return;
        }
        File[] entries = storageDir.listFiles((dir, name) -> {
            if (name.startsWith("smartpattern_")) return false;
            File f = new File(dir, name);
            return f.isDirectory() || name.endsWith(".dat");
        });
        if (entries == null || entries.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No hyperdimensional storage data found."));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Found " + entries.length + " hyperdimensional storage entry(s) (click UUID to copy):"));
        Arrays.sort(entries, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File entry : entries) {
            String name = entry.getName();
            String uuidStr = entry.isDirectory() ? name : name.substring(0, name.length() - 4);

            TextComponentString uuidText = new TextComponentString(uuidStr);
            uuidText.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuidStr));
            uuidText.getStyle().setColor(TextFormatting.AQUA);
            uuidText.getStyle().setUnderlined(true);

            TextComponentString suffix = new TextComponentString("  ("
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.lastModified())) + ")");
            suffix.getStyle().setColor(TextFormatting.GRAY);

            TextComponentString line = new TextComponentString("  - ");
            line.getStyle().setColor(TextFormatting.GRAY);
            line.appendSibling(uuidText);
            line.appendSibling(suffix);
            sender.sendMessage(line);
        }
    }

    private void giveHdController(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid UUID: " + uuidStr));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        ItemStack stack = new ItemStack(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("nexusId", uuid);
        stack.setTagCompound(tag);
        boolean added = player.inventory.addItemStackToInventory(stack);
        if (!added) {
            player.dropItem(stack, false);
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Given Hyperdimensional Controller block carrying UUID " + uuidStr + "."));
    }

    // ---- testhd ----

    private static final List<Item> GEAR_CACHE = new ArrayList<>();

    private void executeTestHd(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e testhd <uuid> <count>"));
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid UUID: " + args[1]));
            return;
        }
        int count;
        try {
            count = Integer.parseInt(args[2]);
            if (count <= 0 || count > 100_000) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Count must be between 1 and 100000."));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid count: " + args[2]));
            return;
        }

        // Locate controller by UUID
        TileHyperdimensionalController targetController = null;
        for (WorldServer world : server.worlds) {
            if (world == null) continue;
            for (net.minecraft.tileentity.TileEntity te : world.loadedTileEntityList) {
                if (te instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController controller = (TileHyperdimensionalController) te;
                    if (controller.isFormed() && uuid.equals(controller.getNexusId())) {
                        targetController = controller;
                        break;
                    }
                }
            }
            if (targetController != null) break;
        }

        if (targetController == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] No formed hyperdimensional controller found with UUID " + uuid + "."));
            return;
        }

        ItemStorageAdapter adapter = targetController.getItemAdapter();
        if (adapter == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Controller found but item adapter is not initialized."));
            return;
        }

        // Cache gear items on first run
        synchronized (GEAR_CACHE) {
            if (GEAR_CACHE.isEmpty()) {
                for (Item item : Item.REGISTRY) {
                    if (item instanceof ItemSword || item instanceof ItemTool
                            || item instanceof ItemArmor || item instanceof ItemBow) {
                        GEAR_CACHE.add(item);
                    }
                }
            }
        }
        if (GEAR_CACHE.isEmpty()) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] No gear items found in registry."));
            return;
        }

        IActionSource actionSource;
        if (sender instanceof EntityPlayerMP) {
            actionSource = new PlayerSource((EntityPlayerMP) sender, null);
        } else {
            actionSource = new MachineSource(targetController);
        }

        Random random = new Random();
        for (int i = 0; i < count; i++) {
            ItemStack stack = generateRandomGear(random);
            if (stack.isEmpty()) continue;

            IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
            if (aeStack == null) continue;

            long amountLong = Math.abs(random.nextLong());
            if (amountLong <= 0) amountLong = 1;
            IAEItemStack toInject = aeStack.copy();
            toInject.setStackSize(amountLong);
            adapter.injectItems(toInject, Actionable.MODULATE, actionSource);
        }

        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Injected " + count + " random enchanted gear type(s) into controller " + uuid + "."));
    }

    private static ItemStack generateRandomGear(Random random) {
        Item item = GEAR_CACHE.get(random.nextInt(GEAR_CACHE.size()));
        ItemStack stack = new ItemStack(item);

        int enchantCount = random.nextInt(6); // 0..5 enchantments
        if (enchantCount > 0) {
            List<Enchantment> possible = new ArrayList<>();
            for (Enchantment ench : Enchantment.REGISTRY) {
                if (ench != null && ench.canApply(stack)) {
                    possible.add(ench);
                }
            }
            if (!possible.isEmpty()) {
                Collections.shuffle(possible, random);
                for (int i = 0; i < Math.min(enchantCount, possible.size()); i++) {
                    Enchantment ench = possible.get(i);
                    int level = 1 + random.nextInt(ench.getMaxLevel());
                    stack.addEnchantment(ench, level);
                }
            }
        }
        return stack;
    }


}
