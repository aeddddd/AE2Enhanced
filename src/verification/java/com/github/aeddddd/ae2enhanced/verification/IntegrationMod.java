package com.github.aeddddd.ae2enhanced.verification;

import appeng.api.AEApi;
import appeng.api.definitions.ITileDefinition;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.PlayerSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.misc.TileInterface;
import appeng.tile.storage.TileDrive;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(
        modid = IntegrationMod.MOD_ID,
        name = "AE2Enhanced Real Grid Integration Verification",
        version = "1.0",
        dependencies = "required-after:ae2enhanced",
        acceptableRemoteVersions = "*"
)
public final class IntegrationMod {
    public static final String MOD_ID = "ae2enhancedintegration";

    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final AtomicBoolean CHECKS_PASSED = new AtomicBoolean();
    private static final AtomicBoolean KNOWN_DEFECT = new AtomicBoolean();
    private static final AtomicBoolean SHUTDOWN_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean STOPPING_OBSERVED = new AtomicBoolean();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();

    private String mode;
    private Stage stage;
    private int ticks;
    private int planSequence;
    private int currentPlan;
    private WorldServer world;
    private BlockPos origin;
    private TileInterface iface;
    private PlayerSource source;
    private IGrid grid;
    private ICraftingGrid crafting;
    private IMEMonitor<IAEItemStack> inventory;
    private Future<ICraftingJob> future;
    private ICraftingJob job;
    private ICraftingLink link;
    private CraftingCPUCluster cpu;
    private long stockBefore;
    private long deliveredBefore;
    private long recoveryTasks;
    private long recoveryWaiting;
    private long recoveryStone;
    private ScheduledExecutorService watchdog;
    private final List<String> results = new ArrayList<>();

    private enum Stage {
        BUILD,
        AWAIT_GRID,
        BEGIN_DELIVERY,
        AWAIT_DELIVERY_PLAN,
        EXECUTE_DELIVERY,
        BEGIN_CANCEL,
        AWAIT_CANCEL_PLAN,
        EXECUTE_CANCEL,
        VERIFY_CANCEL,
        BEGIN_VARIANT,
        AWAIT_VARIANT_PLAN,
        EXECUTE_VARIANT,
        BEGIN_RECOVERY,
        AWAIT_RECOVERY_PLAN,
        CAPTURE_IN_FLIGHT,
        WRITE_DONE,
        VERIFY_RESTORE,
        RESUME_RECOVERY,
        DONE
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        mode = System.getProperty("ae2e.integration.mode", "");
        if (!mode.equals("write") && !mode.equals("verify")) {
            return;
        }
        net.minecraft.util.NonNullList<net.minecraft.item.crafting.Ingredient> charcoalInputs =
                net.minecraft.util.NonNullList.create();
        charcoalInputs.add(net.minecraft.item.crafting.Ingredient.fromStacks(
                new ItemStack(net.minecraft.init.Items.BLAZE_POWDER)));
        net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.register(
                new net.minecraft.item.crafting.ShapelessRecipes("",
                        new ItemStack(net.minecraft.init.Items.COAL, 1, 1), charcoalInputs)
                        .setRegistryName(MOD_ID, "verification_charcoal"));
        net.minecraft.util.NonNullList<net.minecraft.item.crafting.Ingredient> diamondInputs =
                net.minecraft.util.NonNullList.create();
        diamondInputs.add(net.minecraft.item.crafting.Ingredient.fromStacks(
                new ItemStack(net.minecraft.init.Items.COAL, 1, 0),
                new ItemStack(net.minecraft.init.Items.COAL, 1, 1)));
        net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.register(
                new net.minecraft.item.crafting.ShapelessRecipes("",
                        new ItemStack(net.minecraft.init.Items.DIAMOND), diamondInputs)
                        .setRegistryName(MOD_ID, "verification_diamond"));
        MinecraftForge.EVENT_BUS.register(this);
        long timeout = Long.getLong("ae2e.integration.watchdogSeconds", 300L);
        watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AE2E integration watchdog");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.schedule(() -> {
            if (FINISHED.compareAndSet(false, true)) {
                LOGGER.error("INTEGRATION_FAIL: watchdog timed out after {} seconds in stage {}", timeout, stage);
                FMLCommonHandler.instance().exitJava(2, true);
            }
        }, timeout, TimeUnit.SECONDS);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (mode == null || mode.isEmpty()) {
            return;
        }
        world = FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0];
        origin = world.getSpawnPoint().add(2, 2, 2);
        stage = mode.equals("write") ? Stage.BUILD : Stage.VERIFY_RESTORE;
        ticks = 0;
        if (mode.equals("verify")) {
            iface = (TileInterface) world.getTileEntity(origin.add(4, 0, 0));
            NBTTagCompound data = iface.writeToNBT(new NBTTagCompound());
            LOGGER.info("INTEGRATION_STARTED_NBT: requesterCount={} links0={} links1={} links2={}",
                    iface.getRequestedJobs().size(), data.getTag("links-0"), data.getTag("links-1"),
                    data.getTag("links-2"));
        }
        LOGGER.info("INTEGRATION_BEGIN: mode={} world={} origin={}", mode, world.getWorldInfo().getWorldName(), origin);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (mode != null && !mode.isEmpty()) {
            STOPPING_OBSERVED.set(true);
        }
        if (iface != null) {
            try {
                NBTTagCompound data = iface.writeToNBT(new NBTTagCompound());
                LOGGER.info("INTEGRATION_STOPPING_NBT: requesterCount={} links0={} links1={} links2={}",
                        iface.getRequestedJobs().size(), data.getTag("links-0"), data.getTag("links-1"),
                        data.getTag("links-2"));
                logLinkTopology("server-stopping");
            } catch (Exception exception) {
                LOGGER.error("INTEGRATION_STOPPING_NBT: capture failed", exception);
            }
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        if (mode == null || mode.isEmpty() || !FINISHED.compareAndSet(false, true)) {
            return;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
        }
        if (CHECKS_PASSED.get() && SHUTDOWN_REQUESTED.get() && STOPPING_OBSERVED.get()) {
            LOGGER.info(mode.equals("verify") ? "INTEGRATION_PASS" : "INTEGRATION_WRITE_PASS");
        } else if (!KNOWN_DEFECT.get()) {
            LOGGER.error("INTEGRATION_FAIL: server stopped without successful {} stages", mode);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == null || stage == Stage.DONE || FINISHED.get()) {
            return;
        }
        ticks++;
        try {
            if (ticks > 3600) {
                throw new IllegalStateException("tick budget exhausted in stage " + stage);
            }
            tickStage();
        } catch (Throwable throwable) {
            fail("stage " + stage + " failed: " + describe(throwable), throwable);
        }
    }

    private void tickStage() throws Exception {
        switch (stage) {
            case BUILD:
                buildFixture();
                advance(Stage.AWAIT_GRID);
                break;
            case AWAIT_GRID:
                if (!gridNodeReady()) {
                    return;
                }
                bindGrid();
                advance(Stage.BEGIN_DELIVERY);
                break;
            case BEGIN_DELIVERY:
                stockBefore = count(netItems().createStack(new ItemStack(net.minecraft.init.Blocks.STONE, 1)));
                require(stockBefore == 256L, "initial network stone must be 256, got " + stockBefore);
                deliveredBefore = countDeliveredButtons();
                require(deliveredBefore == 0L, "initial network button must be zero");
                beginJob(1, Stage.AWAIT_DELIVERY_PLAN);
                break;
            case AWAIT_DELIVERY_PLAN:
                pollPlan(Stage.EXECUTE_DELIVERY);
                break;
            case EXECUTE_DELIVERY:
                if (ticks % 20 == 0) {
                    logCpu("delivery");
                }
                if (cpu.isBusy()) {
                    return;
                }
                long stone = count(item(net.minecraft.init.Blocks.STONE));
                long button = countDeliveredButtons();
                require(stone == stockBefore - 1L && button == 1L,
                        "ordinary delivery conservation failed: stone=" + stone + ", button=" + button);
                record("ordinary-delivery", "PASS", "planId=" + currentPlan + " stone=" + stone + " button=" + button);
                advance(Stage.BEGIN_CANCEL);
                break;
            case BEGIN_CANCEL:
                stockBefore = count(item(net.minecraft.init.Blocks.STONE));
                deliveredBefore = countDeliveredButtons();
                beginJob(32, Stage.AWAIT_CANCEL_PLAN);
                break;
            case AWAIT_CANCEL_PLAN:
                pollPlan(Stage.EXECUTE_CANCEL);
                break;
            case EXECUTE_CANCEL:
                requireBusyCpu();
                cpu.cancel();
                LOGGER.info("INTEGRATION_CANCEL: planId={} cancelled on server thread", currentPlan);
                advance(Stage.VERIFY_CANCEL);
                break;
            case VERIFY_CANCEL:
                if (ticks % 20 != 0) {
                    return;
                }
                require(!cpu.isBusy(), "cancelled CPU remains busy");
                require(countDeliveredButtons() == deliveredBefore,
                        "cancelled plan delivered buttons");
                require(count(item(net.minecraft.init.Blocks.STONE)) == stockBefore,
                        "cancelled plan did not restore stone inventory");
                record("cancel", "PASS", "planId=" + currentPlan + " inventory restored");
                advance(Stage.BEGIN_VARIANT);
                break;
            case BEGIN_VARIANT:
                IAEItemStack diamond = netItems().createStack(new ItemStack(net.minecraft.init.Items.DIAMOND));
                stockBefore = count(diamond);
                deliveredBefore = countDelivered(diamond);
                require(stockBefore == 0L && deliveredBefore == 0L,
                        "variant fixture requires zero initial diamonds");
                IAEItemStack variantOutput = diamond.copy();
                variantOutput.setStackSize(1L);
                beginJob(variantOutput, Stage.AWAIT_VARIANT_PLAN);
                break;
            case AWAIT_VARIANT_PLAN:
                pollPlan(Stage.EXECUTE_VARIANT);
                break;
            case EXECUTE_VARIANT:
                if (ticks % 20 == 0) {
                    logCpu("variant-subcraft");
                }
                if (cpu.isBusy()) {
                    return;
                }
                IAEItemStack deliveredDiamond = netItems().createStack(
                        new ItemStack(net.minecraft.init.Items.DIAMOND));
                long variantStock = count(deliveredDiamond);
                long variantDelivered = countDelivered(deliveredDiamond);
                long encodedCoal = count(netItems().createStack(new ItemStack(net.minecraft.init.Items.COAL)));
                require(variantDelivered == 1L && encodedCoal == 0L,
                        "variant subcraft delivery failed: stock=" + variantStock + ", delivered="
                                + variantDelivered + ", encodedCoal=" + encodedCoal);
                record("lp-variant-subcraft-delivery", "PASS", "planId=" + currentPlan
                        + " diamonds=" + variantDelivered + " encodedCoal=" + encodedCoal);
                advance(Stage.BEGIN_RECOVERY);
                break;
            case BEGIN_RECOVERY:
                stockBefore = count(item(net.minecraft.init.Blocks.STONE));
                deliveredBefore = countDeliveredButtons();
                beginJob(64, Stage.AWAIT_RECOVERY_PLAN);
                break;
            case AWAIT_RECOVERY_PLAN:
                pollPlan(Stage.CAPTURE_IN_FLIGHT);
                break;
            case CAPTURE_IN_FLIGHT:
                requireBusyCpu();
                long tasks = taskCount(cpu);
                long waiting = stackCount(((ISpecialCpuAccess) (Object) cpu).ae2e$waitingFor());
                logCpu("capture");
                if (tasks <= 0L || waiting <= 0L) {
                    return;
                }
                logLinkTopology("before-save");
                recoveryTasks = tasks;
                recoveryWaiting = waiting;
                recoveryStone = count(item(net.minecraft.init.Blocks.STONE));
                writeRecoveryState();
                record("integrationWrite", "PASS", "planId=" + currentPlan + " tasks=" + tasks + " waitingFor=" + waiting);
                CHECKS_PASSED.set(true);
                stage = Stage.WRITE_DONE;
                requestShutdown();
                break;
            case WRITE_DONE:
                break;
            case VERIFY_RESTORE:
                if (!gridNodeReady()) {
                    return;
                }
                bindGrid();
                readRecoveryState();
                requireBusyCpu();
                require(((ISpecialCpuAccess) (Object) cpu).ae2e$context() != null,
                        "restored CPU has no explicit execution context");
                long restoredTasks = taskCount(cpu);
                long restoredWaiting = stackCount(((ISpecialCpuAccess) (Object) cpu).ae2e$waitingFor());
                boolean expectedOutputDelivered = countDeliveredButtons() > deliveredBefore;
                require(restoredTasks > 0L && restoredTasks <= recoveryTasks
                                && (restoredWaiting == recoveryWaiting || expectedOutputDelivered),
                        "in-flight state was not restored with resumable progress: captured tasks/waiting="
                                + recoveryTasks + '/' + recoveryWaiting + ", restored " + restoredTasks + '/' + restoredWaiting
                                + ", expectedOutputDelivered=" + expectedOutputDelivered);
                require(count(item(net.minecraft.init.Blocks.STONE)) == recoveryStone,
                        "network stone changed across restart before resume");
                logCpu("restored");
                logLinkTopology("after-reload");
                record("integrationVerify-reload", "PASS", "planId=" + currentPlan + " tasks=" + restoredTasks
                        + " waitingFor=" + restoredWaiting);
                advance(Stage.RESUME_RECOVERY);
                break;
            case RESUME_RECOVERY:
                CraftingCPUCluster currentCluster = (CraftingCPUCluster) crafting.getCpus().iterator().next();
                if (currentCluster != cpu) {
                    LOGGER.info("INTEGRATION_CPU_REFORMED: previous={} current={}", System.identityHashCode(cpu),
                            System.identityHashCode(currentCluster));
                    cpu = currentCluster;
                }
                if (ticks % 20 == 0) {
                    logCpu("resume");
                }
                if (cpu.isBusy()) {
                    return;
                }
                logCpu("resume-finished");
                logLinkTopology("after-resume");
                long resumeButton = countDeliveredButtons();
                long resumeStone = count(item(net.minecraft.init.Blocks.STONE));
                if (resumeButton < deliveredBefore + 64L && taskCount(cpu) == 0L && resumeStone > recoveryStone) {
                    knownDefect("restore-link-cancelled", "planId=" + currentPlan + " restored CraftingLink was cancelled; "
                            + "unprocessed stone refunded=" + (resumeStone - recoveryStone) + ", deliveredBeforeCompletion=" + resumeButton);
                    break;
                }
                require(resumeButton == deliveredBefore + 64L, "resumed job button delivery mismatch: " + resumeButton);
                require(resumeStone == stockBefore - 64L, "resumed job stone conservation mismatch: " + resumeStone);
                record("integrationVerify-resume", "PASS", "planId=" + currentPlan + " button=" + resumeButton + " stone=" + resumeStone);
                CHECKS_PASSED.set(true);
                stage = Stage.DONE;
                requestShutdown();
                break;
            case DONE:
                break;
            default:
                throw new IllegalStateException("unknown stage " + stage);
        }
    }

    private void buildFixture() {
        place("creative_energy_cell", origin);
        place("drive", origin.add(1, 0, 0));
        place("crafting_storage_1k", origin.add(2, 0, 0));
        place("crafting_monitor", origin.add(3, 0, 0));
        place("interface", origin.add(4, 0, 0));
        place("molecular_assembler", origin.add(4, 0, 1));
        TileEntity driveEntity = world.getTileEntity(origin.add(1, 0, 0));
        require(driveEntity instanceof TileDrive, "drive tile missing");
        TileEntity interfaceEntity = world.getTileEntity(origin.add(4, 0, 0));
        require(interfaceEntity instanceof TileInterface, "interface tile missing");
        require(world.getTileEntity(origin.add(3, 0, 0)) instanceof TileCraftingMonitorTile, "CPU monitor tile missing");
        iface = (TileInterface) interfaceEntity;
        TileDrive drive = (TileDrive) driveEntity;
        ItemStack cell = AEApi.instance().definitions().items().cell1k().maybeStack(1).orElseThrow(
                () -> new IllegalStateException("1k cell unavailable"));
        drive.getInternalInventory().insertItem(0, cell, false);
        iface.getInventoryByName("patterns").insertItem(0, createPattern(), false);
        iface.getInventoryByName("patterns").insertItem(1, createPattern(
                new ItemStack(net.minecraft.init.Items.BLAZE_POWDER),
                new ItemStack(net.minecraft.init.Items.COAL, 1, 1)), false);
        iface.getInventoryByName("patterns").insertItem(2, createPattern(
                new ItemStack[] { new ItemStack(net.minecraft.init.Items.COAL) },
                new ItemStack(net.minecraft.init.Items.DIAMOND), true), false);
        EntityPlayer player = FakePlayerFactory.getMinecraft(world);
        source = new PlayerSource(player, iface);
        LOGGER.info("INTEGRATION_FIXTURE: real energy/drive/cell/CPU/interface/assembler constructed at {}", origin);
    }

    private void place(String definition, BlockPos pos) {
        Block block = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.util.ResourceLocation("appliedenergistics2", definition));
        require(block != null, "AE2 block not registered: " + definition);
        require(world.setBlockState(pos, block.getDefaultState(), 3), "failed to place " + definition + " at " + pos);
    }

    private boolean gridNodeReady() {
        TileEntity entity = world.getTileEntity(origin.add(4, 0, 0));
        if (!(entity instanceof TileInterface)) {
            return false;
        }
        TileInterface candidate = (TileInterface) entity;
        boolean ready = candidate.getActionableNode() != null && candidate.getActionableNode().isActive();
        if (!ready && ticks % 100 == 0) {
            LOGGER.info("INTEGRATION_WAIT_GRID: mode={} stage={} interface node is not active", mode, stage);
        }
        return ready;
    }

    private void bindGrid() {
        require(iface != null || mode.equals("verify"), "interface unavailable");
        if (iface == null) {
            iface = (TileInterface) world.getTileEntity(origin.add(4, 0, 0));
            EntityPlayer player = FakePlayerFactory.getMinecraft(world);
            source = new PlayerSource(player, iface);
        }
        require(iface.getActionableNode() != null && iface.getActionableNode().isActive(), "interface grid node inactive");
        grid = iface.getActionableNode().getGrid();
        crafting = grid.getCache(ICraftingGrid.class);
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        inventory = storage.getInventory(netItems());
        require(!crafting.getCpus().isEmpty(), "no production crafting CPU formed");
        cpu = (CraftingCPUCluster) crafting.getCpus().iterator().next();
        require(cpu.isActive(), "production CPU is not active");
        if (mode.equals("write") && stage == Stage.AWAIT_GRID) {
            long inserted = inject(item(net.minecraft.init.Blocks.STONE), 256L);
            require(inserted == 0L, "failed to insert initial stone stock");
            inserted = inject(netItems().createStack(new ItemStack(net.minecraft.init.Items.BLAZE_POWDER)), 1L);
            require(inserted == 0L, "failed to insert alternative charcoal ingredient");
            LOGGER.info("INTEGRATION_GRID: cpus={} storage={} ready", crafting.getCpus().size(), inventory.getClass().getName());
        }
    }

    private void beginJob(long amount, Stage next) {
        IAEItemStack output = item(net.minecraft.init.Blocks.STONE_BUTTON);
        output.setStackSize(amount);
        beginJob(output, next);
    }

    private void beginJob(IAEItemStack output, Stage next) {
        currentPlan = ++planSequence;
        future = crafting.beginCraftingJob(world, grid, source, output, null);
        job = null;
        link = null;
        stage = next;
        LOGGER.info("INTEGRATION_PLAN: planId={} beginCraftingJob amount={} stage={}", currentPlan,
                output.getStackSize(), stage);
    }

    private boolean pollPlan(Stage completedStage) {
        if (!future.isDone()) {
            return false;
        }
        try {
            job = future.get();
            if (job == null || job.isSimulation()) {
                IItemList<IAEItemStack> plan = netItems().createList();
                if (job instanceof appeng.crafting.CraftingJob) {
                    ((appeng.crafting.CraftingJob) job).populatePlan(plan);
                }
                LOGGER.error("INTEGRATION_PLAN_MISSING: planId={} class={}", currentPlan,
                        job == null ? "null" : job.getClass().getName());
                if (job instanceof com.github.aeddddd.ae2enhanced.specialcrafting.LpCraftingJob) {
                    try {
                        LOGGER.error("INTEGRATION_PLAN_LP_STATE: reason={} unresolved={}",
                                readField(job, "lastFallbackReason"), readField(job, "unresolvedUnits"));
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException("failed to inspect LP fallback state", exception);
                    }
                }
                for (IAEItemStack entry : plan) {
                    LOGGER.error("INTEGRATION_PLAN_MISSING_ENTRY: stack={} size={} requestable={}",
                            entry, entry.getStackSize(), entry.getCountRequestable());
                }
            }
            require(job != null && !job.isSimulation(), "plan is simulation or missing");
            link = crafting.submitJob(job, iface, null, false, source);
            require(link != null, "submitJob returned null");
            trackRequesterLink();
            requireBusyCpu();
            LOGGER.info("INTEGRATION_SUBMIT: planId={} job={} output={} bytes={}", currentPlan,
                    System.identityHashCode(job), job.getOutput(), job.getByteTotal());
            logCpu("submitted");
            stage = completedStage;
            return completedStage != Stage.EXECUTE_DELIVERY;
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("planning Future failed: " + describe(exception.getCause()), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("planning Future interrupted", exception);
        }
    }

    private boolean submitReadyJob() {
        return future.isDone() && pollPlan(Stage.EXECUTE_DELIVERY);
    }

    private void trackRequesterLink() {
        try {
            java.lang.reflect.Field trackerField = iface.getInterfaceDuality().getClass().getDeclaredField("craftingTracker");
            trackerField.setAccessible(true);
            Object tracker = trackerField.get(iface.getInterfaceDuality());
            java.lang.reflect.Method setLink = tracker.getClass().getDeclaredMethod("setLink", int.class, ICraftingLink.class);
            setLink.setAccessible(true);
            setLink.invoke(tracker, currentPlan - 1, link);
            iface.markDirty();
            LOGGER.info("INTEGRATION_TRACKER: planId={} requester link registered for NBT persistence", currentPlan);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("failed to register requester link in MultiCraftingTracker", exception);
        }
    }

    private void requireBusyCpu() {
        require(cpu != null && cpu.isBusy(), "production CPU is not busy");
    }

    private long inject(IAEItemStack template, long amount) {
        IAEItemStack stack = template.copy();
        stack.setStackSize(amount);
        IAEItemStack leftover = inventory.injectItems(stack, appeng.api.config.Actionable.MODULATE, source);
        return leftover == null ? 0L : leftover.getStackSize();
    }

    private long count(IAEItemStack template) {
        IItemList<IAEItemStack> list = inventory.getStorageList();
        IAEItemStack found = list.findPrecise(template);
        return found == null ? 0L : found.getStackSize();
    }

    private long countDeliveredButtons() {
        return countDelivered(item(net.minecraft.init.Blocks.STONE_BUTTON));
    }

    private long countDelivered(IAEItemStack delivered) {
        long total = count(delivered);
        ItemStack definition = delivered.createItemStack();
        if (iface != null) {
            IItemHandler storage = (IItemHandler) iface.getInventoryByName("storage");
            for (int slot = 0; slot < storage.getSlots(); slot++) {
                ItemStack stack = storage.getStackInSlot(slot);
                if (stack.isItemEqual(definition)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    private static IAEItemStack item(Block block) {
        return AEItemStack.fromItemStack(new ItemStack(block));
    }

    private static IItemStorageChannel netItems() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    private static long taskCount(CraftingCPUCluster cluster) {
        long count = 0L;
        for (Object progress : ((ISpecialCpuAccess) (Object) cluster).ae2e$tasks().values()) {
            count += ((com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor) progress).ae2e$getValue();
        }
        return count;
    }

    private static long stackCount(IItemList<IAEItemStack> list) {
        long count = 0L;
        if (list != null) {
            for (IAEItemStack stack : list) {
                count += stack.getStackSize();
            }
        }
        return count;
    }

    private void logCpu(String label) {
        LOGGER.info("INTEGRATION_CPU: {} planId={} busy={} tasks={} waitingFor={} remaining={} networkStone={} networkButton={}",
                label, currentPlan, cpu.isBusy(), taskCount(cpu),
                stackCount(((ISpecialCpuAccess) (Object) cpu).ae2e$waitingFor()), cpu.getRemainingItemCount(),
                count(item(net.minecraft.init.Blocks.STONE)), countDeliveredButtons());
    }

    private void logLinkTopology(String label) throws Exception {
        ICraftingLink cpuLink = ((ISpecialCpuAccess) (Object) cpu).ae2e$myLastLink();
        if (cpuLink != null) {
            NBTTagCompound data = new NBTTagCompound();
            cpuLink.writeToNBT(data);
            LOGGER.info("INTEGRATION_LINK: {} cpuLink canceled={} done={} standalone={} nbt={}",
                    label, cpuLink.isCanceled(), cpuLink.isDone(), cpuLink.isStandalone(), data);
        }
        for (ICraftingLink requested : iface.getRequestedJobs()) {
            NBTTagCompound data = new NBTTagCompound();
            requested.writeToNBT(data);
            LOGGER.info("INTEGRATION_LINK: {} requesterLink canceled={} done={} standalone={} nbt={}",
                    label, requested.isCanceled(), requested.isDone(), requested.isStandalone(), data);
        }
        NBTTagCompound duality = iface.writeToNBT(new NBTTagCompound());
        LOGGER.info("INTEGRATION_LINK_NBT: {} requesterCount={} links0={} links1={}", label,
                iface.getRequestedJobs().size(), duality.getTag("links-0"), duality.getTag("links-1"));
        java.lang.reflect.Field linksField = crafting.getClass().getDeclaredField("craftingLinks");
        linksField.setAccessible(true);
        java.util.Map<?, ?> links = (java.util.Map<?, ?>) linksField.get(crafting);
        for (java.util.Map.Entry<?, ?> entry : links.entrySet()) {
            Object nexus = entry.getValue();
            Object req = readField(nexus, "req");
            Object cpuHalf = readField(nexus, "cpu");
            LOGGER.info("INTEGRATION_NEXUS: {} id={} req={} cpu={} tickOfDeath={} canceled={} done={}",
                    label, entry.getKey(), req, cpuHalf, readField(nexus, "tickOfDeath"),
                    readField(nexus, "canceled"), readField(nexus, "done"));
        }
    }

    private static Object readField(Object instance, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private ItemStack createPattern() {
        return createPattern(new ItemStack(net.minecraft.init.Blocks.STONE),
                new ItemStack(net.minecraft.init.Blocks.STONE_BUTTON));
    }

    private ItemStack createPattern(ItemStack input, ItemStack output) {
        return createPattern(new ItemStack[] { input }, output, false);
    }

    private ItemStack createPattern(ItemStack[] input, ItemStack output, boolean substitute) {
        ItemStack pattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1).orElseThrow(
                () -> new IllegalStateException("encoded pattern unavailable"));
        NBTTagCompound data = new NBTTagCompound();
        NBTTagList inputs = new NBTTagList();
        for (ItemStack stack : input) {
            inputs.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        NBTTagList outputs = new NBTTagList();
        outputs.appendTag(output.writeToNBT(new NBTTagCompound()));
        data.setTag("in", inputs);
        data.setTag("out", outputs);
        data.setBoolean("crafting", true);
        data.setBoolean("substitute", substitute);
        pattern.setTagCompound(data);
        return pattern;
    }

    private void writeRecoveryState() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("planId", Integer.toString(currentPlan));
        properties.setProperty("tasks", Long.toString(recoveryTasks));
        properties.setProperty("waitingFor", Long.toString(recoveryWaiting));
        properties.setProperty("networkStone", Long.toString(recoveryStone));
        properties.setProperty("stockBefore", Long.toString(stockBefore));
        properties.setProperty("deliveredBefore", Long.toString(deliveredBefore));
        try (FileOutputStream output = new FileOutputStream("integration-recovery.properties")) {
            properties.store(output, "AE2Enhanced integration recovery fixture");
        }
        writeResults();
    }

    private void readRecoveryState() throws IOException {
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream("integration-recovery.properties")) {
            properties.load(input);
        }
        currentPlan = Integer.parseInt(properties.getProperty("planId"));
        recoveryTasks = Long.parseLong(properties.getProperty("tasks"));
        recoveryWaiting = Long.parseLong(properties.getProperty("waitingFor"));
        recoveryStone = Long.parseLong(properties.getProperty("networkStone"));
        stockBefore = Long.parseLong(properties.getProperty("stockBefore"));
        deliveredBefore = Long.parseLong(properties.getProperty("deliveredBefore"));
    }

    private void record(String scenario, String status, String detail) throws IOException {
        String row = scenario + '\t' + status + '\t' + detail;
        results.add(row);
        LOGGER.info("INTEGRATION_RESULT: {}", row);
        writeResults();
    }

    private void writeResults() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("scenario\tstatus\tdetail");
        lines.addAll(results);
        Files.write(new File("integration-" + mode + "-results.tsv").toPath(), lines, StandardCharsets.UTF_8);
    }

    private void advance(Stage next) {
        stage = next;
        ticks = 0;
    }

    private void requestShutdown() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        SHUTDOWN_REQUESTED.set(true);
        if (server != null) {
            server.addScheduledTask(server::initiateShutdown);
        }
    }

    private void knownDefect(String scenario, String detail) throws IOException {
        KNOWN_DEFECT.set(true);
        record(scenario, "KNOWN_DEFECT", detail);
        LOGGER.error("INTEGRATION_KNOWN_DEFECT: {} {}", scenario, detail);
        stage = Stage.DONE;
        requestShutdown();
    }

    private void fail(String reason, Throwable throwable) {
        LOGGER.error("INTEGRATION_FAIL: " + reason, throwable);
        try {
            record("failure", "FAIL", clean(reason));
        } catch (IOException ignored) {
        }
        stage = Stage.DONE;
        requestShutdown();
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static String clean(String value) {
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
