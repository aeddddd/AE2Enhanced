package com.github.aeddddd.ae2enhanced.command;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
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
import com.github.aeddddd.ae2enhanced.diag.DiagReport;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.harvest.RecipeHarvester;
import com.github.aeddddd.ae2enhanced.diag.check.CheckResult;
import com.github.aeddddd.ae2enhanced.diag.check.DiagChecks;
import com.github.aeddddd.ae2enhanced.diag.check.SystemCheck;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.diag.perf.PerfAnalyzer;
import com.github.aeddddd.ae2enhanced.diag.perf.PerfBaseline;
import com.github.aeddddd.ae2enhanced.diag.perf.PerfExporter;
import com.github.aeddddd.ae2enhanced.diag.perf.TpsTracker;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IAEConfigAccessor;
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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Config;
import appeng.util.item.AEItemStack;
import com.mojang.authlib.GameProfile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.math.BigInteger;
import java.util.*;

/**
 * AE2Enhanced main command.
 *
 * <p>Subcommands:</p>
 * <ul>
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
        return "/ae2e <channels|fastpathing|specialcrafting|recoverhd|testhd|migratefluids|chamberdebug|diag|debug|perf|harvest|pd|help>";
    }

    /**
     * 顶级命令放行所有玩家（level 0），具体权限在 execute 内按子命令分别判定：
     * pd 的自管理子命令（list/info/tp/invite/kick/setperm）面向普通玩家，
     * 管理员工具子命令（channels/recoverhd/testhd/migratefluids/pd delete 等）仍需 level 2。
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    @Nonnull
    public List<String> getAliases() {
        return java.util.Collections.singletonList("ae2e");
    }

    private static final String[] SUBCOMMANDS = {
            "channels", "fastpathing", "specialcrafting", "recoverhd", "testhd", "migratefluids",
            "chamberdebug", "diag", "debug", "perf", "plan", "harvest", "pd", "help"
    };
    private static final String[] DIAG_SUBCOMMANDS = {"check", "report"};
    private static final String[] DIAG_CHECK_SYSTEMS = {"all", "storage", "channels", "recipes", "grid", "personaldim"};
    private static final String[] DEBUG_ACTIONS = {"on", "off", "reset", "status"};
    private static final String[] PERF_SUBCOMMANDS = {"tps", "top", "grid", "slow", "metrics", "baseline", "export", "alert"};
    private static final String[] PERF_TOP_SCOPES = {"machine", "grid"};
    private static final String[] PERF_BASELINE_ACTIONS = {"set", "show", "clear"};
    private static final String[] PLAN_SUBCOMMANDS = {"stats", "running"};
    /** 普通玩家可用的顶级子命令（用于 tab 补全，避免泄露管理员工具） */
    private static final String[] PLAYER_SUBCOMMANDS = {"pd", "help"};
    private static final String[] TOGGLE_OPTIONS = {"enable", "disable", "status"};
    private static final String[] PD_SUBCOMMANDS = {
            "list", "info", "delete", "tp", "invite", "kick", "setperm"
    };
    /** 普通玩家可用的 pd 子命令（delete 为管理员操作） */
    private static final String[] PD_PLAYER_SUBCOMMANDS = {
            "list", "info", "tp", "invite", "kick", "setperm"
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
        // 非管理员不补全管理员工具子命令，避免泄露
        boolean admin = sender.canUseCommand(2, getName());
        if (args.length == 1) {
            if (admin) {
                return CommandBase.getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
            }
            List<String> opts = new ArrayList<>(Arrays.asList(PLAYER_SUBCOMMANDS));
            // 性能分析工具按配置的权限等级放行（默认全员）
            if (canUseAnalysis(sender)) {
                opts.add("perf");
                opts.add("plan");
            }
            return CommandBase.getListOfStringsMatchingLastWord(args, opts);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if ("channels".equals(sub) || "fastpathing".equals(sub) || "specialcrafting".equals(sub)) {
                if (!admin) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, TOGGLE_OPTIONS);
            }
            if ("recoverhd".equals(sub)) {
                if (!admin) return Collections.emptyList();
                List<String> list = new ArrayList<>();
                list.add("list");
                list.addAll(collectHdUuids(server));
                return CommandBase.getListOfStringsMatchingLastWord(args, list);
            }
            if ("testhd".equals(sub)) {
                if (!admin) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, collectHdUuids(server));
            }
            if ("pd".equals(sub)) {
                return CommandBase.getListOfStringsMatchingLastWord(args, admin ? PD_SUBCOMMANDS : PD_PLAYER_SUBCOMMANDS);
            }
            if ("diag".equals(sub)) {
                if (!admin) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, DIAG_SUBCOMMANDS);
            }
            if ("debug".equals(sub)) {
                if (!admin) return Collections.emptyList();
                List<String> opts = new ArrayList<>(DiagSwitch.names());
                opts.add("list");
                return CommandBase.getListOfStringsMatchingLastWord(args, opts);
            }
            if ("perf".equals(sub)) {
                if (!canUseAnalysis(sender)) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, PERF_SUBCOMMANDS);
            }
            if ("plan".equals(sub)) {
                if (!canUseAnalysis(sender)) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, PLAN_SUBCOMMANDS);
            }
        }
        if (args.length == 3) {
            if ("diag".equals(sub) && "check".equalsIgnoreCase(args[1])) {
                if (!admin) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, DIAG_CHECK_SYSTEMS);
            }
            if ("debug".equals(sub) && DiagSwitch.isKnown(args[1].toLowerCase())) {
                if (!admin) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, DEBUG_ACTIONS);
            }
            if ("perf".equals(sub) && "top".equalsIgnoreCase(args[1])) {
                if (!canUseAnalysis(sender)) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, PERF_TOP_SCOPES);
            }
            if ("perf".equals(sub) && "baseline".equalsIgnoreCase(args[1])) {
                if (!canUseAnalysis(sender)) return Collections.emptyList();
                return CommandBase.getListOfStringsMatchingLastWord(args, PERF_BASELINE_ACTIONS);
            }
        }
        if (args.length == 3 && "pd".equals(sub)) {
            String pdSub = args[1].toLowerCase();
            if ("delete".equals(pdSub) && !admin) {
                return Collections.emptyList();
            }
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
            case "channels":
                if (!requireAdmin(sender)) return;
                executeChannels(sender, args);
                break;
            case "fastpathing":
                if (!requireAdmin(sender)) return;
                executeFastPathing(sender, args);
                break;
            case "specialcrafting":
                if (!requireAdmin(sender)) return;
                executeSpecialCrafting(sender, args);
                break;
            case "recoverhd":
                if (!requireAdmin(sender)) return;
                executeRecoverHd(server, sender, args);
                break;
            case "testhd":
                if (!requireAdmin(sender)) return;
                executeTestHd(server, sender, args);
                break;
            case "migratefluids":
                if (!requireAdmin(sender)) return;
                executeMigrateFluids(sender);
                break;
            case "pd":
                executePersonalDimension(server, sender, args);
                break;
            case "chamberdebug":
                if (!requireAdmin(sender)) return;
                executeChamberDebug(sender);
                break;
            case "diag":
                if (!requireAdmin(sender)) return;
                executeDiag(server, sender, args);
                break;
            case "debug":
                if (!requireAdmin(sender)) return;
                executeDebug(sender, args);
                break;
            case "perf":
                if (!requireAnalysis(sender)) return;
                executePerf(server, sender, args);
                break;
            case "plan":
                if (!requireAnalysis(sender)) return;
                executePlan(sender, args);
                break;
            case "harvest":
                if (!requireAdmin(sender)) return;
                executeHarvest(sender);
                break;
            case "help":
                executeHelp(sender);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Unknown subcommand: " + sub));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + getUsage(sender)));
        }
    }

    /**
     * 校验管理员工具权限（level 2）。pd 的自管理子命令放行普通玩家，
     * 其余子命令（channels/recoverhd/testhd/migratefluids/pd delete 等）仅 OP 可用。
     */
    private boolean requireAdmin(@Nonnull ICommandSender sender) {
        if (sender.canUseCommand(2, getName())) {
            return true;
        }
        sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.command.no_permission"));
        return false;
    }

    /**
     * 性能分析工具（perf/plan）权限：默认全员可用（level 0），
     * 可在配置 Diagnostics.analysisPermissionLevel 调整（2 = 仅 OP）。
     */
    private boolean canUseAnalysis(@Nonnull ICommandSender sender) {
        return sender.canUseCommand(AE2EnhancedConfig.diagnostics.analysisPermissionLevel, getName());
    }

    private boolean requireAnalysis(@Nonnull ICommandSender sender) {
        if (canUseAnalysis(sender)) {
            return true;
        }
        sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.command.no_permission"));
        return false;
    }

    /**
     * 奇点处理仓诊断：索引统计 + 手持物品/准星指向处理仓的配方匹配情况.
     */
    private void executeChamberDebug(@Nonnull ICommandSender sender) {
        List<com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe> all =
                com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex.allRecipes();
        Map<String, Integer> counts = new TreeMap<>();
        for (com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe r : all) {
            String prefix = r.getId().contains(":")
                    ? r.getId().substring(0, r.getId().indexOf(':')) : r.getId();
            counts.merge(prefix, 1, Integer::sum);
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD
                + "[ChamberDebug] index total=" + all.size() + " byType=" + counts));

        if (!(sender instanceof net.minecraft.entity.player.EntityPlayer)) {
            return;
        }
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) sender;

        ItemStack held = player.getHeldItemMainhand();
        if (!held.isEmpty()) {
            String key = com.github.aeddddd.ae2enhanced.chamber.LongItemStore.keyOf(held);
            List<com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe> rs =
                    com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex.recipesForInput(key, held);
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW
                    + "[ChamberDebug] held=" + held.getDisplayName() + " key=" + key
                    + " matched=" + recipeIds(rs)));
        }

        net.minecraft.util.math.RayTraceResult rt = player.rayTrace(6.0, 1.0f);
        if (rt == null || rt.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            return;
        }
        net.minecraft.tileentity.TileEntity te = player.world.getTileEntity(rt.getBlockPos());
        if (!(te instanceof com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber)) {
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY
                    + "[ChamberDebug] not looking at a singularity chamber"));
            return;
        }
        com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber chamber =
                (com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber) te;
        sender.sendMessage(new TextComponentString(TextFormatting.GOLD
                + "[ChamberDebug] chamber energy=" + chamber.getEnergy()
                + " jobs=" + chamber.getActiveJobCount()
                + " channels=" + chamber.getUsedChannels() + "/" + chamber.getParallelChannels()));
        Map<String, Long> available = new HashMap<>();
        for (com.github.aeddddd.ae2enhanced.chamber.LongItemStore.Entry entry
                : chamber.getInputStore().getEntries()) {
            String key = com.github.aeddddd.ae2enhanced.chamber.LongItemStore.keyOf(entry.getTemplate());
            available.put(key, entry.getCount());
        }
        for (com.github.aeddddd.ae2enhanced.chamber.LongItemStore.Entry entry
                : chamber.getInputStore().getEntries()) {
            String key = com.github.aeddddd.ae2enhanced.chamber.LongItemStore.keyOf(entry.getTemplate());
            List<com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe> rs =
                    com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex.recipesForInput(
                            key, entry.getTemplate());
            sender.sendMessage(new TextComponentString("  store " + key + " x" + entry.getCount()
                    + " -> " + recipeIds(rs)));
            for (com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe r : rs) {
                sender.sendMessage(new TextComponentString("    " + r.getId()
                        + " maxBatches=" + r.maxBatches(available)));
            }
        }
    }

    private static String recipeIds(List<com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe> recipes) {
        if (recipes.isEmpty()) {
            return "[]";
        }
        List<String> ids = new ArrayList<>();
        for (com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe r : recipes) {
            ids.add(r.getId());
        }
        return ids.toString();
    }

    // ---- help ----

    private void executeHelp(@Nonnull ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "========== AE2Enhanced Command Help =========="));
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
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e specialcrafting <enable|disable|status>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Toggle special crafting plans (self-referencing/cyclic productive recipes)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  When enabled, such plans are solved in closed form and run on Computation Cores."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e chamberdebug"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Diagnose Singularity Chamber recipe index and held-item/looked-at chamber matching."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e diag check [all|storage|channels|recipes|grid|personaldim]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Run read-only health checks (default: all systems)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e diag report"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Run all checks and write a full report to logs/ae2enhanced/."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e debug [list|<switch> on|off|reset|status]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Toggle diagnostic switches at runtime (overrides built-in defaults, not persisted)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Switches: specialcrafting|virtualbatch|batchaggregate|perf"));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf tps"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Show server TPS / tick time stats (5s / 1min / 2min windows)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf top [machine|grid] [n]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Top-N grids or machine classes by avg tick time (AE2 TickManager data)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf grid <rank>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Drill into the grid at the given rank from 'perf top grid'."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf slow [thresholdMs] [n]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  List nodes whose avg tick time exceeds the threshold (default 1.0ms), with locations."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf metrics"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Dump the internal metrics registry (counters/timers/gauges)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf baseline <set|show|clear>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Manage the performance baseline used by anomaly alerts (alerts broadcast to OPs every minute check)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf export"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Export TPS/grid/machine/metrics snapshot to logs/ae2enhanced/perf-<ts>.json."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e perf alert [multiplier]"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Show or set the alert threshold multiplier vs baseline (default 1.5)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e plan stats"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Crafting plan path stats (special/dag/fallback/native counts, compute time, conservation check)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e plan running"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  List busy crafting CPUs with progress, co-processors and elapsed time."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e harvest"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Harvest final recipe registries (crafting/furnace/ore dict/machines) to logs/ae2enhanced/harvest-<ts>.json."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Dev/test workflow: snapshot feeds the modpack recipe simulation fixture (research/harvest/, not committed)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e help"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Display this help message."));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "=============================================="));
    }

    // ---- diagnostics ----

    private void executeDiag(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e diag <check [system]|report>"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "check":
                executeDiagCheck(server, sender, args);
                break;
            case "report":
                executeDiagReport(server, sender);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e diag <check [system]|report>"));
        }
    }

    private void executeDiagCheck(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        String system = args.length >= 3 ? args[2].toLowerCase() : "all";
        if ("all".equals(system)) {
            int totalWarn = 0;
            int totalError = 0;
            for (SystemCheck check : DiagChecks.all()) {
                List<CheckResult> results = DiagChecks.runOne(server, check);
                sendCheckResults(sender, check, results);
                totalWarn += DiagChecks.countLevel(results, CheckResult.Level.WARN);
                totalError += DiagChecks.countLevel(results, CheckResult.Level.ERROR);
            }
            sender.sendMessage(msg(totalError > 0 ? TextFormatting.RED
                    : totalWarn > 0 ? TextFormatting.YELLOW : TextFormatting.GREEN,
                    "chat.ae2enhanced.diag.all_done", totalWarn, totalError));
            return;
        }
        SystemCheck check = DiagChecks.byName(system);
        if (check == null) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.diag.unknown_system",
                    system, "all|" + String.join("|", diagSystemNames())));
            return;
        }
        sendCheckResults(sender, check, DiagChecks.runOne(server, check));
    }

    private void sendCheckResults(@Nonnull ICommandSender sender, SystemCheck check, List<CheckResult> results) {
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.diag.header",
                new TextComponentTranslation(check.displayName()), check.name()));
        for (CheckResult r : results) {
            TextFormatting color;
            switch (r.level) {
                case OK: color = TextFormatting.GREEN; break;
                case WARN: color = TextFormatting.YELLOW; break;
                default: color = TextFormatting.RED; break;
            }
            TextComponentString line = new TextComponentString(color + "  [" + r.level + "] ");
            line.appendSibling(r.toComponent());
            sender.sendMessage(line);
        }
    }

    private void executeDiagReport(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.diag.report_generating"));
        File file = DiagReport.generate(server);
        if (file == null) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.diag.report_failed"));
        } else {
            sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.diag.report_done", file.getPath()));
        }
    }

    /**
     * 整合包配方快照采集（测试工作流）:终态注册表 → logs/ae2enhanced/harvest-*.json,
     * 供 ModpackFixture 在虚拟测试中重放（快照拷入 research/harvest/,不入 Git）.
     */
    private void executeHarvest(@Nonnull ICommandSender sender) {
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.harvest.start"));
        File file = RecipeHarvester.harvest();
        if (file == null) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.harvest.failed"));
        } else {
            sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.harvest.done", file.getPath()));
        }
    }

    private static List<String> diagSystemNames() {
        List<String> names = new ArrayList<>();
        for (SystemCheck check : DiagChecks.all()) {
            names.add(check.name());
        }
        return names;
    }

    private void executeDebug(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.debug.switches"));
            for (String name : DiagSwitch.names()) {
                sendSwitchState(sender, name);
            }
            return;
        }
        String name = args[1].toLowerCase();
        if (!DiagSwitch.isKnown(name)) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.debug.unknown",
                    name, String.join("|", DiagSwitch.names())));
            return;
        }
        if (args.length < 3 || "status".equalsIgnoreCase(args[2])) {
            sendSwitchState(sender, name);
            return;
        }
        switch (args[2].toLowerCase()) {
            case "on":
                DiagSwitch.setOverride(name, true);
                sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.debug.on", name));
                break;
            case "off":
                DiagSwitch.setOverride(name, false);
                sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.debug.off", name));
                break;
            case "reset":
                DiagSwitch.clearOverride(name);
                sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.debug.reset", name));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Usage: /ae2e debug " + name + " <on|off|reset|status>"));
        }
    }

    private void sendSwitchState(@Nonnull ICommandSender sender, String name) {
        boolean enabled = DiagSwitch.isEnabled(name);
        ITextComponent state = msg(enabled ? TextFormatting.GREEN : TextFormatting.GRAY,
                enabled ? "chat.ae2enhanced.debug.state_on" : "chat.ae2enhanced.debug.state_off");
        ITextComponent source = msg(TextFormatting.GRAY,
                DiagSwitch.hasOverride(name)
                        ? "chat.ae2enhanced.debug.source_override" : "chat.ae2enhanced.debug.source_default");
        sender.sendMessage(msg(TextFormatting.YELLOW, "chat.ae2enhanced.debug.state", name, state, source));
    }

    // ---- performance ----

    private void executePerf(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Usage: /ae2e perf <tps|top [machine|grid] [n]|grid <rank>|slow [thresholdMs] [n]|metrics|baseline <set|show|clear>|export|alert [multiplier]>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "tps":
                executePerfTps(sender);
                break;
            case "top":
                executePerfTop(sender, args);
                break;
            case "grid":
                executePerfGrid(sender, args);
                break;
            case "slow":
                executePerfSlow(sender, args);
                break;
            case "metrics":
                executePerfMetrics(sender);
                break;
            case "baseline":
                executePerfBaseline(server, sender, args);
                break;
            case "export":
                executePerfExport(server, sender);
                break;
            case "alert":
                executePerfAlert(server, sender, args);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Usage: /ae2e perf <tps|top [machine|grid] [n]|grid <rank>|slow [thresholdMs] [n]|metrics|baseline <set|show|clear>|export|alert [multiplier]>"));
        }
    }

    private void executePerfTps(@Nonnull ICommandSender sender) {
        sender.sendMessage(msg(TextFormatting.AQUA, DiagSwitch.isEnabled(DiagSwitch.PERF)
                ? "chat.ae2enhanced.perf.tps_header" : "chat.ae2enhanced.perf.tps_header_off"));
        sendTpsWindow(sender, "5s", 100);
        sendTpsWindow(sender, "1min", 1200);
        sendTpsWindow(sender, "2min", 2400);
    }

    private void sendTpsWindow(@Nonnull ICommandSender sender, String label, int window) {
        TpsTracker.Stats stats = TpsTracker.stats(window);
        if (stats == null) {
            sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.no_data", label));
            return;
        }
        TextFormatting color = stats.tps >= 19.5 ? TextFormatting.GREEN
                : stats.tps >= 15.0 ? TextFormatting.YELLOW : TextFormatting.RED;
        sender.sendMessage(msg(color, "chat.ae2enhanced.perf.tps_line", label, String.valueOf(stats)));
    }

    private void executePerfTop(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        String scope = args.length >= 3 ? args[2].toLowerCase() : "machine";
        int limit = args.length >= 4 ? parsePositiveInt(args[3], 10) : 10;
        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(-1.0);
        if ("grid".equals(scope)) {
            sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.top_grid_header", limit));
            int rank = 0;
            for (PerfAnalyzer.GridStat gs : scan.grids) {
                if (++rank > limit) break;
                sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_line",
                        rank, gs.nodes, PerfAnalyzer.formatNanos(gs.totalAvgNanos),
                        formatDouble(gs.avgPowerUsage), gs.cpuBusy, gs.cpuTotal,
                        String.valueOf(gs.controllerState)));
            }
            if (rank == 0) {
                sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.no_grids"));
            }
        } else if ("machine".equals(scope)) {
            sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.top_machine_header", limit));
            int rank = 0;
            for (PerfAnalyzer.MachineStat ms : scan.machines) {
                if (++rank > limit) break;
                sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.machine_line",
                        rank, ms.className, ms.nodes, PerfAnalyzer.formatNanos(ms.totalAvgNanos)));
            }
            if (rank == 0) {
                sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.no_tracked"));
            }
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Usage: /ae2e perf top <machine|grid> [n]"));
            return;
        }
        sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.scan_ms",
                formatDouble(scan.scanMs())));
    }

    private void executePerfGrid(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e perf grid <rank>"));
            return;
        }
        int rank = parsePositiveInt(args[2], -1);
        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(-1.0);
        if (rank < 1 || rank > scan.grids.size()) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.perf.invalid_rank",
                    args[2], scan.grids.size()));
            return;
        }
        PerfAnalyzer.GridStat gs = scan.grids.get(rank - 1);
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.grid_detail_header", rank));
        sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_detail_nodes",
                gs.nodes, PerfAnalyzer.formatNanos(gs.totalAvgNanos)));
        sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_detail_power",
                String.valueOf(gs.controllerState), formatDouble(gs.avgPowerUsage),
                formatDouble(gs.storedPower)));
        sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.grid_detail_cpus",
                gs.cpuBusy, gs.cpuTotal));
    }

    private void executePerfSlow(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        double thresholdMs = args.length >= 3 ? parsePositiveDouble(args[2], 1.0) : 1.0;
        int limit = args.length >= 4 ? parsePositiveInt(args[3], 10) : 10;
        PerfAnalyzer.ScanResult scan = PerfAnalyzer.scan(thresholdMs);
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.slow_header",
                formatDouble(thresholdMs), limit));
        int rank = 0;
        for (PerfAnalyzer.SlowNode sn : scan.slowNodes) {
            if (++rank > limit) break;
            sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.slow_line",
                    rank, sn.machine, PerfAnalyzer.formatNanos(sn.avgNanos), sn.location));
        }
        if (rank == 0) {
            sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.none_over"));
        }
        sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.scan_ms",
                formatDouble(scan.scanMs())));
    }

    private void executePerfMetrics(@Nonnull ICommandSender sender) {
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.metrics_header"));
        int shown = 0;
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Counter c : MetricsRegistry.counters()) {
            sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.metrics_counter",
                    c.name(), c.get()));
            shown++;
        }
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Timer t : MetricsRegistry.timers()) {
            sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.metrics_timer",
                    t.name(), t.snapshot().toString()));
            shown++;
        }
        for (com.github.aeddddd.ae2enhanced.diag.metrics.Gauge g : MetricsRegistry.gauges()) {
            sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.metrics_gauge",
                    g.name(), formatDouble(g.get())));
            shown++;
        }
        if (shown == 0) {
            sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.metrics_empty"));
        }
    }

    private static int parsePositiveInt(String s, int fallback) {
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parsePositiveDouble(String s, double fallback) {
        try {
            double v = Double.parseDouble(s);
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatDouble(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** 带颜色的本地化消息组件（客户端按各自语言渲染）。 */
    private static ITextComponent msg(TextFormatting color, String key, Object... args) {
        TextComponentTranslation t = new TextComponentTranslation(key, args);
        t.getStyle().setColor(color);
        return t;
    }

    private void executePerfBaseline(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                     @Nonnull String[] args) {
        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Usage: /ae2e perf baseline <set|show|clear>"));
            return;
        }
        switch (args[2].toLowerCase()) {
            case "set": {
                PerfBaseline.Baseline b = PerfBaseline.capture(server);
                if (b == null) {
                    sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.perf.baseline_no_data"));
                    return;
                }
                if (PerfBaseline.save(server, b)) {
                    sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.baseline_set",
                            formatDouble(b.avgTickMs), PerfAnalyzer.formatNanos(b.gridTotalNanos),
                            b.machineTotals.size(), formatDouble(b.alertMultiplier)));
                } else {
                    sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.perf.baseline_save_failed"));
                }
                break;
            }
            case "show": {
                PerfBaseline.Baseline b = PerfBaseline.load(server);
                if (b == null) {
                    sender.sendMessage(msg(TextFormatting.YELLOW, "chat.ae2enhanced.perf.baseline_none"));
                    return;
                }
                sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.baseline_show_header"));
                sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.baseline_created",
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(new java.util.Date(b.createdAt))));
                sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.perf.baseline_summary",
                        formatDouble(b.avgTickMs), PerfAnalyzer.formatNanos(b.gridTotalNanos),
                        formatDouble(b.alertMultiplier)));
                for (Map.Entry<String, Long> e : b.machineTotals.entrySet()) {
                    sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.baseline_machine",
                            e.getKey(), PerfAnalyzer.formatNanos(e.getValue())));
                }
                break;
            }
            case "clear": {
                if (PerfBaseline.clear(server)) {
                    sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.baseline_cleared"));
                } else {
                    sender.sendMessage(msg(TextFormatting.YELLOW, "chat.ae2enhanced.perf.baseline_not_exist"));
                }
                break;
            }
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Usage: /ae2e perf baseline <set|show|clear>"));
        }
    }

    private void executePerfExport(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        File file = PerfExporter.export(server);
        if (file == null) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.perf.export_failed"));
        } else {
            sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.export_done", file.getPath()));
        }
    }

    private void executePerfAlert(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                  @Nonnull String[] args) {
        PerfBaseline.Baseline b = PerfBaseline.load(server);
        if (args.length < 3) {
            Object mult = b == null
                    ? msg(TextFormatting.GRAY, "chat.ae2enhanced.perf.alert_no_baseline")
                    : formatDouble(b.alertMultiplier);
            sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.perf.alert_current", mult));
            return;
        }
        double mult = parsePositiveDouble(args[2], -1.0);
        if (mult <= 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Usage: /ae2e perf alert <multiplier> (e.g. 1.5)"));
            return;
        }
        if (b == null) {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.perf.alert_need_baseline"));
            return;
        }
        b.alertMultiplier = mult;
        if (PerfBaseline.save(server, b)) {
            sender.sendMessage(msg(TextFormatting.GREEN, "chat.ae2enhanced.perf.alert_set", formatDouble(mult)));
        } else {
            sender.sendMessage(msg(TextFormatting.RED, "chat.ae2enhanced.perf.baseline_save_failed"));
        }
    }

    // ---- crafting plan ----

    private void executePlan(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED
                    + "Usage: /ae2e plan <stats|running>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "stats":
                executePlanStats(sender);
                break;
            case "running":
                executePlanRunning(sender);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED
                        + "Usage: /ae2e plan <stats|running>"));
        }
    }

    /** 效果评估：各计划路径的执行次数与计算耗时分布 + 守恒校验结果。 */
    private void executePlanStats(@Nonnull ICommandSender sender) {
        ITextComponent verifyState = msg(TextFormatting.GRAY, DiagSwitch.isEnabled(DiagSwitch.PLAN_VERIFY)
                ? "chat.ae2enhanced.debug.state_on" : "chat.ae2enhanced.debug.state_off");
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.plan.stats_header", verifyState));
        String[] paths = {"special", "dag", "dagFallback", "native"};
        for (String path : paths) {
            long count = MetricsRegistry.counter("plan.path." + path).get();
            com.github.aeddddd.ae2enhanced.diag.metrics.Timer.Snapshot snap =
                    MetricsRegistry.timer("plan.computeMs." + path).snapshot();
            if (count == 0 && snap.count == 0) {
                continue;
            }
            sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.plan.stats_line",
                    path, count, formatDouble(snap.avgMs), formatDouble(snap.maxMs),
                    formatDouble(snap.p95Ms)));
        }
        long passed = MetricsRegistry.counter("plan.verify.passed").get();
        long violations = MetricsRegistry.counter("plan.verify.violation").get();
        long skipped = MetricsRegistry.counter("plan.verify.skippedSimulation").get();
        TextFormatting color = violations > 0 ? TextFormatting.RED : TextFormatting.GREEN;
        sender.sendMessage(msg(color, "chat.ae2enhanced.plan.verify_summary", passed, violations, skipped));
    }

    /** 执行状态追踪：列出全部忙碌中的合成 CPU 及进度。 */
    private void executePlanRunning(@Nonnull ICommandSender sender) {
        sender.sendMessage(msg(TextFormatting.AQUA, "chat.ae2enhanced.plan.running_header"));
        int shown = 0;
        for (appeng.me.Grid grid : appeng.hooks.TickHandler.INSTANCE.getGridList()) {
            if (grid.isEmpty()) {
                continue;
            }
            ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            for (ICraftingCPU cpu : crafting.getCpus()) {
                if (!cpu.isBusy()) {
                    continue;
                }
                shown++;
                long start = cpu.getStartItemCount();
                long remaining = cpu.getRemainingItemCount();
                String progress = start > 0
                        ? formatDouble((start - remaining) * 100.0 / start) + "%" : "?";
                long elapsedMs = cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster
                        ? ((appeng.me.cluster.implementations.CraftingCPUCluster) cpu).getElapsedTime() / 1_000_000L
                        : -1L;
                Object name = cpu.getName().isEmpty()
                        ? new TextComponentTranslation("chat.ae2enhanced.plan.unnamed_cpu")
                        : cpu.getName();
                if (elapsedMs >= 0) {
                    sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.plan.running_line",
                            name, String.valueOf(cpu.getFinalOutput()), progress,
                            start - remaining, start, cpu.getCoProcessors(),
                            formatDouble(elapsedMs / 1000.0)));
                } else {
                    sender.sendMessage(msg(TextFormatting.WHITE, "chat.ae2enhanced.plan.running_line_no_elapsed",
                            name, String.valueOf(cpu.getFinalOutput()), progress,
                            start - remaining, start, cpu.getCoProcessors()));
                }
            }
        }
        if (shown == 0) {
            sender.sendMessage(msg(TextFormatting.GRAY, "chat.ae2enhanced.plan.running_none"));
        }
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
                // pd delete 可删除他人维度，属于管理员操作
                if (!requireAdmin(sender)) return;
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
        // 查看他人维度信息需要管理权限：本人、OP 或拥有该维度 MANAGE_RULES 权限的玩家
        if (sender instanceof EntityPlayerMP
                && !PersonalDimensionManager.canManageRules((EntityPlayerMP) sender, targetId)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] You don't have permission to view this dimension's info."));
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
        // 本命令操作发送者自己的维度：仅所有者本人、拥有 MANAGE_RULES 权限的玩家或 OP 可管理
        if (!PersonalDimensionManager.canManageRules(owner, owner.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] You don't have permission to manage this dimension."));
            return;
        }
        EntityPlayerMP target = PlayerArgumentUtil.parseOnlinePlayer(server, sender, args[2]);
        if (target == null) {
            PlayerArgumentUtil.sendPlayerNotFound(sender, args[2]);
            return;
        }
        if (target.getUniqueID().equals(owner.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] You don't need to invite yourself."));
            return;
        }
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(owner.getUniqueID());
        if (entry != null && entry.allowedPlayers.contains(target.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] " + target.getName() + " is already invited."));
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
        // 本命令操作发送者自己的维度：仅所有者本人、拥有 MANAGE_RULES 权限的玩家或 OP 可管理
        if (!PersonalDimensionManager.canManageRules(owner, owner.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] You don't have permission to manage this dimension."));
            return;
        }
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
        if (entry == null || !entry.allowedPlayers.contains(targetId)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] " + resolvePlayerName(server, targetId) + " is not in your personal dimension whitelist."));
            return;
        }
        if (entry.dimensionId != Integer.MIN_VALUE) {
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
        // 本命令操作发送者自己的维度：仅所有者本人、拥有 MANAGE_RULES 权限的玩家或 OP 可管理
        if (!PersonalDimensionManager.canManageRules(owner, owner.getUniqueID())) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] You don't have permission to manage this dimension."));
            return;
        }
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
        boolean value;
        if ("true".equalsIgnoreCase(args[4])) {
            value = true;
        } else if ("false".equalsIgnoreCase(args[4])) {
            value = false;
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid boolean value: " + args[4] + ". Use true or false."));
            return;
        }
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

                    // 分批转换为 ae2fc 格式：FluidStack.amount 为 int，
                    // 单一流体超过 Integer.MAX_VALUE 时必须拆分为多个 drop，否则超出部分静默丢失
                    long remaining = extracted;
                    while (remaining > 0) {
                        int batch = (int) Math.min(remaining, Integer.MAX_VALUE);
                        FluidStack toConvert = fluid.copy();
                        toConvert.amount = batch;
                        ItemStack ae2fcDrop = Ae2fcFluidCompat.createFluidDrop(toConvert);
                        if (ae2fcDrop.isEmpty()) {
                            // 转换失败,把本批次原 drop 还回去
                            ItemStack returnStack = mcStack.copy();
                            returnStack.setCount(batch);
                            itemMonitor.injectItems(AEItemStack.fromItemStack(returnStack), Actionable.MODULATE, source);
                            remaining -= batch;
                            continue;
                        }

                        // 注回物品通道,由 ae2fc 接管
                        IAEItemStack toInsert = AEItemStack.fromItemStack(ae2fcDrop);
                        IAEItemStack notInserted = itemMonitor.injectItems(toInsert, Actionable.MODULATE, source);
                        long rejected = notInserted != null ? notInserted.getStackSize() : 0;
                        if (rejected > 0) {
                            // 注回失败的部分回退为等量原 AE2E fluid drop，避免静默丢失
                            ItemStack returnStack = mcStack.copy();
                            returnStack.setCount((int) rejected);
                            itemMonitor.injectItems(AEItemStack.fromItemStack(returnStack), Actionable.MODULATE, source);
                        }
                        long inserted = batch - rejected;
                        convertedAmount += inserted;
                        convertedStacks++;
                        remaining -= batch;
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to migrate fluid drops", e);
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Migration failed: " + e.getMessage()));
            return;
        }

        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Migrated " + convertedStacks + " AE2E fluid drop stacks (" + convertedAmount + " mB) to ae2fc format."));
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
            // AEConfig 为 final 类, 需经 (Object) 中转强转 accessor 接口
            EnumSet<AEFeature> flags = ((IAEConfigAccessor)(Object) config).ae2e$getFeatureFlags();
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

    // ---- specialcrafting ----

    private void executeSpecialCrafting(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e specialcrafting <enable|disable|status>"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "enable":
                setSpecialCrafting(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Special crafting plans enabled."));
                break;
            case "disable":
                setSpecialCrafting(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Special crafting plans disabled (vanilla calculation)."));
                break;
            case "status":
                boolean enabled = AE2EnhancedConfig.crafting.specialCrafting;
                String status = enabled
                        ? TextFormatting.GREEN + "Enabled (closed-form solver, Computation Core execution)"
                        : TextFormatting.YELLOW + "Disabled (vanilla calculation)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Special crafting status: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e specialcrafting <enable|disable|status>"));
        }
    }

    private void setSpecialCrafting(boolean enabled) {
        AE2EnhancedConfig.crafting.specialCrafting = enabled;
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
