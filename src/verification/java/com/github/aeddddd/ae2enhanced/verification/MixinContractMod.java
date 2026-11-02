package com.github.aeddddd.ae2enhanced.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.container.slot.SlotFake;
import appeng.crafting.MECraftingInventory;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.misc.TileCellWorkbench;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.crafting.access.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IMeInventoryVersionAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.crafting.access.mixin.ICraftingCPUClusterAccessor;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(
        modid = MixinContractMod.MOD_ID,
        name = "AE2Enhanced Mixin Contract Verification",
        version = "1.0",
        dependencies = "required-after:ae2enhanced",
        acceptableRemoteVersions = "*"
)
public final class MixinContractMod {
    public static final String MOD_ID = "ae2enhancedmixincontract";

    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final AtomicBoolean CHECKS_PASSED = new AtomicBoolean();
    private static final AtomicBoolean SHUTDOWN_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean STOPPING_OBSERVED = new AtomicBoolean();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();

    private final List<Contract> contracts = new ArrayList<>();
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final List<String> results = new ArrayList<>();
    private int requiredFailures;
    private int knownDefects;
    private ScheduledExecutorService watchdog;
    private boolean clientMode;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!Boolean.getBoolean("ae2e.mixinContract.enabled")
                && !Boolean.getBoolean("ae2e.mixinContract.client.enabled")) {
            return;
        }
        long timeout = Long.getLong("ae2e.mixinContract.watchdogSeconds", 300L);
        watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AE2E mixin contract watchdog");
            thread.setDaemon(true);
            return thread;
        });
        watchdog.schedule(() -> {
            if (FINISHED.compareAndSet(false, true)) {
                LOGGER.error("MIXIN_CONTRACT_FAIL: watchdog timed out after {} seconds", timeout);
                FMLCommonHandler.instance().exitJava(2, true);
            }
        }, timeout, TimeUnit.SECONDS);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (!Boolean.getBoolean("ae2e.mixinContract.client.enabled")) {
            return;
        }
        clientMode = true;
        runContracts(false);
        if (FINISHED.compareAndSet(false, true)) {
            watchdog.shutdownNow();
            if (requiredFailures == 0 && knownDefects == 0) {
                LOGGER.info("MIXIN_CLIENT_CONTRACT_PASS");
                FMLCommonHandler.instance().exitJava(0, true);
            } else {
                LOGGER.error("MIXIN_CLIENT_CONTRACT_FAIL: requiredFailures={}, knownDefects={}",
                        requiredFailures, knownDefects);
                FMLCommonHandler.instance().exitJava(2, true);
            }
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("ae2e.mixinContract.enabled")) {
            return;
        }
        clientMode = false;
        runContracts(true);
        if (requiredFailures == 0 && knownDefects == 0) {
            CHECKS_PASSED.set(true);
        }
        requestShutdown();
    }

    private void runContracts(boolean behaviorProbes) {
        try {
            loadManifest();
            loadTransformedClasses();
            for (Contract contract : contracts) {
                checkContract(contract);
            }
            checkInjectionOrder();
            checkWrapperOrder();
            if (behaviorProbes) {
                runBehaviorProbes();
            }
            writeResults();
            if (requiredFailures != 0 || knownDefects != 0) {
                LOGGER.error("MIXIN_CONTRACT_FAIL: requiredFailures={}, knownDefects={}", requiredFailures, knownDefects);
            }
        } catch (Throwable throwable) {
            requiredFailures++;
            LOGGER.error("MIXIN_CONTRACT_FAIL: unrecoverable verification error: " + describe(throwable), throwable);
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (Boolean.getBoolean("ae2e.mixinContract.enabled")) {
            STOPPING_OBSERVED.set(true);
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        if (!Boolean.getBoolean("ae2e.mixinContract.enabled") || !FINISHED.compareAndSet(false, true)) {
            return;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
        }
        if (CHECKS_PASSED.get() && SHUTDOWN_REQUESTED.get() && STOPPING_OBSERVED.get()
                && requiredFailures == 0 && knownDefects == 0) {
            LOGGER.info("MIXIN_CONTRACT_PASS");
        } else {
            LOGGER.error("MIXIN_CONTRACT_FAIL: server stopped without a complete successful contract run");
        }
    }

    private void loadManifest() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                MixinContractMod.class.getResourceAsStream("/mixin-contracts.tsv"), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            require(header != null && header.startsWith("profile\tconfig\tmixin\ttargetClass\ttargetMethod+desc"),
                    "invalid mixin-contracts.tsv header");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] columns = line.split("\\t", -1);
                require(columns.length == 10, "manifest row must have 10 columns: " + line);
                if (clientMode != "client".equals(columns[8])) {
                    continue;
                }
                contracts.add(new Contract(columns));
            }
        }
        require(!contracts.isEmpty(), "empty mixin contract manifest");
        LOGGER.info("MIXIN_CONTRACT_MANIFEST: {} injections", contracts.size());
    }

    private void loadTransformedClasses() throws Exception {
        File exportRoot = new File(System.getProperty("ae2e.mixinContract.exportDir", ".mixin.out"));
        Map<String, List<Contract>> byTarget = new LinkedHashMap<>();
        for (Contract contract : contracts) {
            byTarget.computeIfAbsent(contract.targetClass, ignored -> new ArrayList<>()).add(contract);
        }
        for (Map.Entry<String, List<Contract>> targetEntry : byTarget.entrySet()) {
            String target = targetEntry.getKey();
            String binaryName = target.replace('/', '.');
            Class<?> loaded = Launch.classLoader.loadClass(binaryName);
            require(loaded.getClassLoader() == Launch.classLoader,
                    target + " was not defined by Launch.classLoader");
            File exported = new File(exportRoot, target + ".class");
            byte[] bytes;
            if (exported.isFile()) {
                bytes = Files.readAllBytes(exported.toPath());
            } else {
                require(targetEntry.getValue().stream().allMatch(contract -> "KNOWN_DEFECT".equals(contract.required)),
                        "missing current transformed export for " + target + ": " + exported);
                bytes = Launch.classLoader.getClassBytes(binaryName);
                LOGGER.warn("MIXIN_CONTRACT_KNOWN_DEFECT: {} has no transformed export; inspecting original bytes", target);
            }
            require(bytes != null, "class bytes unavailable for " + target);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            require(target.equals(node.name), "export class identity mismatch for " + target);
            classes.put(target, node);
        }
        LOGGER.info("MIXIN_CONTRACT_CLASSLOADER: {} target classes loaded through Launch.classLoader; transformed exports resolved", classes.size());
    }

    private void checkContract(Contract contract) {
        String failure = null;
        try {
            ClassNode node = classes.get(contract.targetClass);
            require(node != null, "target class was not loaded: " + contract.targetClass);
            if (contract.isBridgeLike()) {
                require(findMethods(node, contract.handler).stream()
                                .filter(method -> (method.access & Opcodes.ACC_BRIDGE) == 0).count() == 1,
                        "bridge/accessor method is absent: " + contract.handler);
            } else if (contract.isWrap()) {
                checkWrap(contract, node);
            } else {
                MethodNode target = findTargetMethod(node, contract);
                int handlerCalls = countCalls(target, contract.handler);
                boolean multiReturn = "RETURN".equals(contract.at) || "TAIL".equals(contract.at);
                require(multiReturn ? handlerCalls >= 1 : handlerCalls == 1,
                        "target method handler call count mismatch for " + contract.handler + ": " + handlerCalls);
                if ("REDIRECT".equals(contract.injectionType)) {
                    InvokeTarget invocation = contract.invokeTarget();
                    require(countCalls(target, invocation) == 0,
                            "redirect left original invocation in target method: " + invocation);
                }
            }
        } catch (Throwable throwable) {
            failure = describe(throwable);
        }
        record(contract, failure);
    }

    private void checkWrap(Contract contract, ClassNode node) {
        List<MethodNode> handlers = findMethods(node, contract.handler);
        require(handlers.size() == 1, "wrapper handler is absent or duplicated: " + contract.handler);
        MethodNode handler = handlers.get(0);
        require(countCalls(node, contract.handler) >= 1, "wrapper handler is never called: " + contract.handler);
        int expectedOriginalCalls = contract.expectedOriginalCalls();
        int actualOriginalCalls = countOperationCalls(node, contract.handler);
        require(actualOriginalCalls == expectedOriginalCalls,
                "Operation.call count for " + contract.handler + ": expected " + expectedOriginalCalls
                        + ", got " + actualOriginalCalls);
        if ("WRAP_METHOD".equals(contract.injectionType)) {
            return;
        }
        if (contract.at.startsWith("NEW:")) {
            String type = contract.at.substring("NEW:".length());
            int actualNew = countNewBridgeCalls(node, type);
            require(actualNew == contract.expectedOriginalInvocations(),
                    "wrapped NEW count mismatch for " + contract.handler + ": expected "
                            + contract.expectedOriginalInvocations() + ", got " + actualNew + " for " + type);
            return;
        }
        InvokeTarget invocation = contract.invokeTarget();
        int actualOriginalInvocations = countBridgeCalls(node, invocation);
        require(actualOriginalInvocations == contract.expectedOriginalInvocations(),
                "wrapped original invocation count mismatch for " + contract.handler + ": expected "
                        + contract.expectedOriginalInvocations() + ", got " + actualOriginalInvocations
                        + " in MixinExtras bridge structure: " + invocation);
    }

    private void checkInjectionOrder() {
        Map<String, List<Contract>> groups = new LinkedHashMap<>();
        for (Contract contract : contracts) {
            if (contract.isWrap() || !"INJECT".equals(contract.injectionType)
                    || "KNOWN_DEFECT".equals(contract.required)) {
                continue;
            }
            groups.computeIfAbsent(contract.targetClass + '#' + contract.targetMethod + '#' + contract.at,
                    ignored -> new ArrayList<>()).add(contract);
        }
        for (List<Contract> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            Contract first = group.get(0);
            MethodNode method = findTargetMethod(classes.get(first.targetClass), first);
            List<String> actual = new ArrayList<>();
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    String name = ((MethodInsnNode) instruction).name;
                    group.stream().filter(contract -> isHandlerMethod(name, contract.handler)).findFirst()
                            .ifPresent(contract -> actual.add(contract.handler));
                }
            }
            group.sort(Comparator.comparingInt(Contract::order));
            List<String> expected = new ArrayList<>();
            group.forEach(contract -> expected.add(contract.handler));
            boolean matches = !actual.isEmpty() && actual.size() % expected.size() == 0;
            for (int i = 0; matches && i < actual.size(); i++) {
                matches = expected.get(i % expected.size()).equals(actual.get(i));
            }
            if (!matches) {
                record(group.get(0), "injection order mismatch: expected repeating sequence " + expected + ", got " + actual);
            }
        }
    }

    private void checkWrapperOrder() {
        Map<String, List<Contract>> groups = new LinkedHashMap<>();
        for (Contract contract : contracts) {
            if (contract.isWrap() && !"KNOWN_DEFECT".equals(contract.required)) {
                groups.computeIfAbsent(contract.targetClass + '#' + contract.targetMethod + '#' + contract.at,
                        ignored -> new ArrayList<>()).add(contract);
            }
        }
        for (List<Contract> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            group.sort(Comparator.comparingInt(Contract::order));
            Contract first = group.get(0);
            MethodNode target = findTargetMethod(classes.get(first.targetClass), first);
            List<String> chain = new ArrayList<>();
            List<String> directHandlers = new ArrayList<>();
            for (AbstractInsnNode instruction = target.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    String name = ((MethodInsnNode) instruction).name;
                    group.stream().filter(contract -> isHandlerMethod(name, contract.handler)).findFirst()
                            .ifPresent(contract -> directHandlers.add(contract.handler));
                } else if (instruction instanceof InvokeDynamicInsnNode) {
                    for (Object argument : ((InvokeDynamicInsnNode) instruction).bsmArgs) {
                        if (!(argument instanceof Handle)) {
                            continue;
                        }
                        String name = ((Handle) argument).getName();
                        group.stream().filter(contract -> name.contains("$" + contract.handler)).findFirst()
                                .ifPresent(contract -> chain.add(contract.handler));
                    }
                }
            }
            Collections.reverse(chain);
            List<String> actual = new ArrayList<>(directHandlers);
            actual.addAll(chain);
            List<String> expected = new ArrayList<>();
            group.forEach(contract -> expected.add(contract.handler));
            if (!expected.equals(actual) || directHandlers.size() != 1 || !expected.get(0).equals(directHandlers.get(0))) {
                record(first, "wrapper order mismatch: expected outer-to-inner " + expected
                        + ", actual chain " + actual + ", outer handler " + directHandlers);
            } else {
                results.add(first.id() + "\tORDER_PASS\t" + String.join(" -> ", actual));
                LOGGER.info("MIXIN_CONTRACT_ORDER: {} outer-to-inner {}", first.id(), actual);
            }
        }
    }

    private void runBehaviorProbes() {
        probe("ae2enhanced$setComputationCore", () -> {
            Object cpu = new CraftingCPUCluster(new appeng.api.util.WorldCoord(0, 0, 0),
                    new appeng.api.util.WorldCoord(0, 0, 0));
            IComputationCoreAccess computation = (IComputationCoreAccess) cpu;
            computation.ae2enhanced$setComputationCore(null);
            require(computation.ae2enhanced$getComputationCore() == null, "computation core bridge failed");

            ISpecialClusterMarkAccess mark = (ISpecialClusterMarkAccess) cpu;
            mark.ae2e$setSpecialMarked(true);
            require(mark.ae2e$isSpecialMarked(), "special mark was not stored");
            mark.ae2e$setSpecialMarked(false);
            require(!mark.ae2e$isSpecialMarked(), "special mark was not cleared");

            ISpecialCpuAccess special = (ISpecialCpuAccess) cpu;
            require(special.ae2e$context() != null, "CPU execution context bridge returned null");
            require(special.ae2e$tasks() != null, "special tasks bridge returned null");
            require(special.ae2e$inventory() != null, "CPU inventory bridge returned null");
            require(special.ae2e$remainingSnapshot() == null, "initial remaining snapshot must be null");
            special.ae2e$setRemainingOperations(7);
            require(special.ae2e$remainingOperations() == 7, "remainingOperations bridge failed");

            ICraftingCPUClusterAccessor accessor = (ICraftingCPUClusterAccessor) cpu;
            accessor.ae2e$setAvailableStorage(1234L);
            accessor.ae2e$setAccelerator(7);
            accessor.ae2e$setMyName("contract-probe");
            accessor.ae2e$setMachineSrc(null);
            require(readField(cpu, "availableStorage").equals(1234L), "availableStorage accessor failed");
            require(readField(cpu, "accelerator").equals(7), "accelerator accessor failed");
            require(readField(cpu, "myName").equals("contract-probe"), "myName accessor failed");

            Constructor<?> constructor = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress")
                    .getDeclaredConstructor();
            constructor.setAccessible(true);
            Object progress = constructor.newInstance();
            ITaskProgressAccessor progressAccessor = (ITaskProgressAccessor) progress;
            progressAccessor.ae2e$setValue(987654321L);
            require(progressAccessor.ae2e$getValue() == 987654321L, "TaskProgress accessor failed");
            special.ae2e$setTaskValue(progress, 123456789L);
            require(special.ae2e$taskValue(progress) == 123456789L, "CPU task-value bridge failed");

            Method markDirty = CraftingCPUCluster.class.getDeclaredMethod("markDirty");
            markDirty.setAccessible(true);
            try {
                markDirty.invoke(cpu);
            } catch (InvocationTargetException expectedOriginalWithoutTiles) {
                // The first HEAD still runs; the second call must be cancelled by the coalescing injection.
            }
            require(special.ae2e$context().dirtyMarkedThisTick(),
                    "markDirty HEAD did not set coalescing state");
            markDirty.invoke(cpu);
            return null;
        });

        probe("ae2enhanced$hasAssemblyHub", () -> {
            Object cache = new CraftingGridCache(null);
            ICraftingGridCacheAccess access = (ICraftingGridCacheAccess) cache;
            require(!access.ae2enhanced$hasAssemblyHub(), "new grid cache must not have an assembly hub");
            require(access.ae2enhanced$craftableKeys().isEmpty(), "new grid cache craftable keys must be empty");
            ICraftingPatternDetails details = (ICraftingPatternDetails) Proxy.newProxyInstance(
                    Launch.classLoader, new Class<?>[]{ICraftingPatternDetails.class},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
            Object first = access.ae2enhanced$getMediumsMemo(details);
            Object second = access.ae2enhanced$getMediumsMemo(details);
            require(first == second, "medium memo did not reuse its value");
            return null;
        });

        probe("ae2e$onInject", () -> {
            Object inventory = new MECraftingInventory();
            IMeInventoryVersionAccess version = (IMeInventoryVersionAccess) inventory;
            long initial = version.ae2e$getInjectVersion();
            version.ae2e$bumpInjectVersion();
            require(version.ae2e$getInjectVersion() == initial + 1L, "explicit inventory version bump failed");
            IAEItemStack input = AEItemStack.fromItemStack(new ItemStack(Blocks.STONE));
            long beforeInject = version.ae2e$getInjectVersion();
            ((MECraftingInventory) inventory).injectItems(input, Actionable.MODULATE, null);
            require(version.ae2e$getInjectVersion() == beforeInject + 1L,
                    "injectItems RETURN did not increment inventory version");
            return null;
        });

        probe("ae2enhanced$onSlotClick", () -> {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            require(server != null && server.worlds.length > 0, "cell workbench probe requires a running server world");
            WorldServer world = server.worlds[0];
            EntityPlayerMP player = new EntityPlayerMP(server, world,
                    new GameProfile(null, "cell-workbench-contract"), new PlayerInteractionManager(world));
            TileCellWorkbench tile = new TileCellWorkbench();
            ContainerCellWorkbench container = new ContainerCellWorkbench(player.inventory, tile);
            require(container.inventorySlots.get(1) instanceof SlotFake, "slot 1 is not the workbench view-cell slot");

            ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
            IFluidHandlerItem fluids = bucket.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            require(fluids != null, "vanilla water bucket has no item fluid capability in the contract environment");
            FluidStack available = fluids.drain(Integer.MAX_VALUE, false);
            require(available != null && available.getFluid() == FluidRegistry.WATER && available.amount == 1000,
                    "unexpected water bucket fluid contents");
            player.inventory.setItemStack(bucket);

            ItemStack result = container.slotClick(1, 0, ClickType.PICKUP, player);
            require(result.isEmpty(), "fluid insertion must preserve the native empty slotClick return value");
            require(player.inventory.getItemStack().getItem() == Items.WATER_BUCKET
                            && player.inventory.getItemStack().getCount() == 1,
                    "fluid insertion consumed or duplicated the held container");
            FluidStack inserted = ItemFluidDrop.getFluidStack(container.inventorySlots.get(1).getStack());
            require(inserted != null && inserted.getFluid() == FluidRegistry.WATER && inserted.amount == 1,
                    "left click did not insert water with SlotFakeTypeOnly's native count clamp");

            result = container.slotClick(1, 1, ClickType.PICKUP, player);
            inserted = ItemFluidDrop.getFluidStack(container.inventorySlots.get(1).getStack());
            require(result.isEmpty() && inserted != null && inserted.getFluid() == FluidRegistry.WATER
                            && inserted.amount == 1,
                    "right click did not preserve water with the native return value and count clamp");
            require(player.inventory.getItemStack().getItem() == Items.WATER_BUCKET
                            && player.inventory.getItemStack().getCount() == 1,
                    "right click consumed or duplicated the held container");
            return null;
        });
    }

    private void probe(String handler, Probe probe) {
        Contract contract = contracts.stream().filter(candidate -> candidate.handler.equals(handler)).findFirst().orElse(null);
        if (contract == null) {
            throw new IllegalStateException("probe has no manifest contract: " + handler);
        }
        try {
            probe.run();
            results.add(contract.id() + "\tPROBE_PASS\t-");
            LOGGER.info("MIXIN_CONTRACT_PROBE: {} behavior OK", contract.id());
        } catch (Throwable throwable) {
            record(contract, "behavior probe failed: " + describe(throwable));
        }
    }

    private void record(Contract contract, String failure) {
        if (failure == null) {
            if ("KNOWN_DEFECT".equals(contract.required)) {
                knownDefects++;
                results.add(contract.id() + "\tKNOWN_DEFECT\tbaseline remains blocked even though ASM shape changed");
                LOGGER.error("MIXIN_CONTRACT_KNOWN_DEFECT: {} baseline remains blocked", contract.id());
            } else if ("optional".equals(contract.required)) {
                results.add(contract.id() + "\tOPTIONAL_PASS\t-");
            } else {
                results.add(contract.id() + "\tPASS\t-");
                LOGGER.info("MIXIN_CONTRACT_OK: {}", contract.id());
            }
            return;
        }
        if ("KNOWN_DEFECT".equals(contract.required)) {
            knownDefects++;
            results.add(contract.id() + "\tKNOWN_DEFECT\t" + clean(failure));
            LOGGER.error("MIXIN_CONTRACT_KNOWN_DEFECT: {} -> {}", contract.id(), failure);
        } else if ("optional".equals(contract.required)) {
            results.add(contract.id() + "\tOPTIONAL_FAIL\t" + clean(failure));
            LOGGER.warn("MIXIN_CONTRACT_OPTIONAL: {} -> {}", contract.id(), failure);
        } else {
            requiredFailures++;
            results.add(contract.id() + "\tFAIL\t" + clean(failure));
            LOGGER.error("MIXIN_CONTRACT_FAIL: {} -> {}", contract.id(), failure);
        }
    }

    private void writeResults() throws IOException {
        List<String> output = new ArrayList<>();
        output.add("contract\tstatus\tdetail");
        output.addAll(results);
        Files.write(new File("mixin-contract-results.tsv").toPath(), output, StandardCharsets.UTF_8);
    }

    private void requestShutdown() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        SHUTDOWN_REQUESTED.set(true);
        if (server != null) {
            server.addScheduledTask(server::initiateShutdown);
        }
    }

    private static MethodNode findTargetMethod(ClassNode node, Contract contract) {
        int descriptorStart = contract.targetMethod.indexOf('(');
        String name = contract.targetMethod.substring(0, descriptorStart);
        String descriptor = contract.targetMethod.substring(descriptorStart);
        for (MethodNode method : node.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        throw new IllegalStateException("target method absent: " + contract.targetClass + '.' + contract.targetMethod);
    }

    private static List<MethodNode> findMethods(ClassNode node, String name) {
        List<MethodNode> found = new ArrayList<>();
        for (MethodNode method : node.methods) {
            if (isHandlerMethod(method.name, name)) {
                found.add(method);
            }
        }
        return found;
    }

    private static boolean isHandlerMethod(String actualName, String contractName) {
        return actualName.equals(contractName) || actualName.endsWith("$" + contractName);
    }

    private static int countCalls(MethodNode method, String handler) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode && isHandlerMethod(((MethodInsnNode) instruction).name, handler)) {
                count++;
            }
        }
        return count;
    }

    private static int countCalls(ClassNode node, String handler) {
        int count = 0;
        for (MethodNode method : node.methods) {
            count += countCalls(method, handler);
        }
        return count;
    }

    private static int countCalls(MethodNode method, InvokeTarget target) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(target.owner) && call.name.equals(target.name) && call.desc.equals(target.descriptor)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countBridgeCalls(ClassNode node, InvokeTarget target) {
        int count = 0;
        for (MethodNode method : node.methods) {
            if (method.name.startsWith("mixinextras$bridge$")) {
                count += countCalls(method, target);
            }
        }
        return count;
    }

    private static int countNewBridgeCalls(ClassNode node, String type) {
        int count = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.startsWith("mixinextras$bridge$")) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction.getOpcode() == Opcodes.NEW && ((TypeInsnNode) instruction).desc.equals(type)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countOperationCalls(ClassNode node, String handler) {
        int count = 0;
        for (MethodNode method : node.methods) {
            if (isHandlerMethod(method.name, handler) || method.name.contains("lambda$" + handler)) {
                count += countOperationCalls(method);
            }
        }
        return count;
    }

    private static int countOperationCalls(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.name.equals("call") && call.owner.endsWith("/Operation")) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Object readField(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        return 0;
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

    private interface Probe {
        Object run() throws Exception;
    }

    private static final class Contract {
        final String profile;
        final String config;
        final String mixin;
        final String targetClass;
        final String targetMethod;
        final String injectionType;
        final String at;
        final String handler;
        final String required;
        final String order;

        Contract(String[] columns) {
            profile = columns[0];
            config = columns[1];
            mixin = columns[2];
            targetClass = columns[3];
            targetMethod = columns[4];
            injectionType = columns[5];
            at = columns[6];
            handler = columns[7];
            required = columns[8];
            order = columns[9];
        }

        String id() {
            return profile + '/' + order + '/' + mixin + '/' + handler;
        }

        int order() {
            String value = order.contains(":") ? order.substring(0, order.indexOf(':')) : order;
            return Integer.parseInt(value);
        }

        int expectedOriginalCalls() {
            require(isWrap(), "original call count is only valid for wrappers");
            String[] parts = order.split(":", -1);
            require(parts.length >= 2, "wrapper order must include expected original.call count: " + id());
            return Integer.parseInt(parts[1]);
        }

        int expectedOriginalInvocations() {
            String[] parts = order.split(":", -1);
            return parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
        }

        boolean isBridgeLike() {
            return "BRIDGE".equals(injectionType) || "ACCESSOR".equals(injectionType) || "INVOKER".equals(injectionType);
        }

        boolean isWrap() {
            return "WRAP_OPERATION".equals(injectionType) || "WRAP_METHOD".equals(injectionType);
        }

        InvokeTarget invokeTarget() {
            require(at.startsWith("INVOKE:"), "not an INVOKE contract: " + id());
            String value = at.substring("INVOKE:".length());
            int semicolon = value.indexOf(';');
            int descriptorStart = value.indexOf('(', semicolon);
            String owner = value.substring(0, semicolon);
            if (owner.startsWith("L")) {
                owner = owner.substring(1);
            }
            return new InvokeTarget(owner, value.substring(semicolon + 1, descriptorStart),
                    value.substring(descriptorStart));
        }
    }

    private static final class InvokeTarget {
        final String owner;
        final String name;
        final String descriptor;

        InvokeTarget(String owner, String name, String descriptor) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return owner + '.' + name + descriptor;
        }
    }
}
