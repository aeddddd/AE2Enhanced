package com.github.aeddddd.ae2enhanced.verification;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(
        modid = ServerSmokeMod.MOD_ID,
        name = "AE2Enhanced Server Smoke Verification",
        version = "1.0",
        dependencies = "required-after:ae2enhanced",
        acceptableRemoteVersions = "*"
)
public final class ServerSmokeMod {
    public static final String MOD_ID = "ae2enhancedsmoke";

    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final AtomicBoolean PREREQUISITES_PASSED = new AtomicBoolean();
    private static final AtomicBoolean SHUTDOWN_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean STOPPING_OBSERVED = new AtomicBoolean();
    private static final AtomicBoolean FAILED = new AtomicBoolean();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();

    private ScheduledExecutorService watchdog;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!Boolean.getBoolean("ae2e.smoke.enabled")) {
            return;
        }
        try {
            long timeoutSeconds = longProperty("ae2e.smoke.watchdogSeconds", 300L);
            if (timeoutSeconds <= 0L) {
                throw new IllegalArgumentException("ae2e.smoke.watchdogSeconds must be positive");
            }
            watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "AE2E smoke watchdog");
                thread.setDaemon(true);
                return thread;
            });
            watchdog.schedule(() -> {
                if (FINISHED.compareAndSet(false, true)) {
                    LOGGER.error("SMOKE_FAIL: watchdog timed out after {} seconds", timeoutSeconds);
                    FMLCommonHandler.instance().exitJava(2, true);
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        } catch (Throwable throwable) {
            fail("preInit failed: " + describe(throwable), throwable);
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("ae2e.smoke.enabled")) {
            return;
        }
        try {
            verifyModVersion();
            verifyRegistrationAndNetwork();
            verifyWorlds();
            verifySmartPatternRepository();
            verifyOptionalIntegrations();
            if (ScopeChainVerifier.enabled()) {
                ScopeChainVerifier.started();
            }
            PREREQUISITES_PASSED.set(true);
            if (Boolean.getBoolean("ae2e.smoke.holdForClient")) {
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                LOGGER.info("SMOKE_HOLD_READY: checks passed; connect to 127.0.0.1:{}", findBoundPort(server));
            } else if (!MMCEDispatchVerifier.awaiting() && !FTBVerifier.awaiting()) {
                requestShutdown();
            }
        } catch (Throwable throwable) {
            fail("serverStarted checks failed: " + describe(throwable), throwable);
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (Boolean.getBoolean("ae2e.smoke.enabled")) {
            STOPPING_OBSERVED.set(true);
            try {
                ScopeChainVerifier.stopping();
            } catch (Throwable throwable) {
                fail("serverStopping scope checks failed: " + describe(throwable), throwable);
            }
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        if (!Boolean.getBoolean("ae2e.smoke.enabled")) {
            return;
        }
        if (!FINISHED.compareAndSet(false, true)) {
            return;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
        }
        boolean holdForClient = Boolean.getBoolean("ae2e.smoke.holdForClient");
        if (FAILED.get()) {
            return;
        }
        if (!PREREQUISITES_PASSED.get()) {
            LOGGER.error("SMOKE_FAIL: server stopped before all prerequisites passed");
            return;
        }
        if (!STOPPING_OBSERVED.get()) {
            LOGGER.error("SMOKE_FAIL: ServerStopping was not observed");
            return;
        }
        if (!holdForClient && !SHUTDOWN_REQUESTED.get()) {
            LOGGER.error("SMOKE_FAIL: server stopped without the smoke mod requesting shutdown (EOF is not PASS)");
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            LOGGER.error("SMOKE_FAIL: ServerStopped arrived without a server instance");
            return;
        }
        try {
            ScopeChainVerifier.stopped();
        } catch (Throwable throwable) {
            LOGGER.error("SMOKE_FAIL: serverStopped scope checks failed: " + describe(throwable), throwable);
            return;
        }
        LOGGER.info("SMOKE_PASS");
    }

    private static void verifyModVersion() {
        ModContainer container = Loader.instance().getIndexedModList().get(AE2Enhanced.MOD_ID);
        require(container != null, "ae2enhanced is absent from the indexed mod list");
        String expected = System.getProperty("ae2e.smoke.expectedVersion");
        require(expected != null && !expected.trim().isEmpty(), "missing ae2e.smoke.expectedVersion");
        require(expected.equals(container.getVersion()),
                "ae2enhanced version mismatch: expected " + expected + ", got " + container.getVersion());
        require(expected.equals(AE2Enhanced.VERSION),
                "AE2Enhanced.VERSION mismatch: expected " + expected + ", got " + AE2Enhanced.VERSION);
        require(AE2Enhanced.instance != null, "AE2Enhanced mod instance is not initialized");
    }

    private static void verifyRegistrationAndNetwork() {
        require(AE2Enhanced.network != null, "AE2Enhanced network wrapper is not initialized");
        require(ItemRegistry.CONFORMAL_CHARGE != null, "conformal charge field is not initialized");
        ResourceLocation itemId = new ResourceLocation(AE2Enhanced.MOD_ID, "conformal_invariant_charge");
        ResourceLocation blockId = new ResourceLocation(AE2Enhanced.MOD_ID, "assembly_controller");
        require(ForgeRegistries.ITEMS.getValue(itemId) == ItemRegistry.CONFORMAL_CHARGE,
                "conformal_invariant_charge is absent from the item registry");
        require(ForgeRegistries.BLOCKS.getValue(blockId) != null,
                "assembly_controller is absent from the block registry");
    }

    private static void verifyWorlds() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        require(server != null, "dedicated server instance is unavailable");
        WorldServer[] worlds = server.worlds;
        require(worlds != null && worlds.length > 0, "no server worlds are loaded");
        require(worlds[0] != null && !worlds[0].isRemote, "overworld is unavailable or remote");
        require(worlds[0].getWorldInfo() != null, "overworld metadata is unavailable");
    }

    private static void verifyOptionalIntegrations() throws Exception {
        if (Boolean.getBoolean("ae2e.smoke.mmce")) {
            WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0];
            MMCEVerifier.verify(world);
            MMCEDispatchVerifier.start(world);
        }
        if (Boolean.getBoolean("ae2e.smoke.guide")) {
            GuideVerifier.verify();
        }
        if (Boolean.getBoolean("ae2e.smoke.ftb")) {
            FTBVerifier.start(FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0]);
        }
        String storagePhase = System.getProperty("ae2e.smoke.storagePhase", "");
        if (!storagePhase.isEmpty()) {
            StorageChannelVerifier.verify(FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0], storagePhase);
        }
    }

    private static void verifySmartPatternRepository() {
        WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0];
        java.util.UUID id = java.util.UUID.randomUUID();
        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository repository =
                com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository.get(world);
        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData initial =
                new com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData(id,
                        "minecraft:furnace", java.util.Collections.emptyList());
        require(repository.migrate(initial, 0L).committed(), "SmartPattern initial CAS failed");
        long revision = repository.get(id).revision();
        java.util.concurrent.atomic.AtomicInteger invalidations = new java.util.concurrent.atomic.AtomicInteger();
        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository.ListenerRegistration listener =
                repository.listen(id, (changedId, changedRevision) -> invalidations.incrementAndGet());
        require(repository.edit(id, revision, data -> {
            data.addEmptyRecipe();
            return data;
        }).committed(), "SmartPattern revision increment failed");
        require(invalidations.get() == 1, "SmartPattern deployed invalidation was not delivered");
        require(repository.edit(id, revision, data -> data).status()
                        == com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository.Status.STALE,
                "SmartPattern stale write was accepted");
        listener.close();
        net.minecraft.item.ItemStack staleCopy = com.github.aeddddd.ae2enhanced.item.ItemSmartPattern.createPattern(
                id, new java.util.BitSet(), 1, "minecraft:furnace", 0L);
        require(com.github.aeddddd.ae2enhanced.item.ItemSmartPattern.expandPatterns(staleCopy, world).size() == 1,
                "SmartPattern stale copy did not reload repository content");
    }

    static void requestShutdown() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        require(server != null, "cannot request shutdown without a server");
        SHUTDOWN_REQUESTED.set(true);
        server.addScheduledTask(server::initiateShutdown);
    }

    private static int findBoundPort(MinecraftServer server) throws Exception {
        require(server != null && server.getNetworkSystem() != null, "network system is unavailable");
        Object networkSystem = server.getNetworkSystem();
        java.util.List<?> endpoints = null;
        for (java.lang.reflect.Field field : networkSystem.getClass().getDeclaredFields()) {
            if (!java.util.List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            java.util.List<?> candidate = (java.util.List<?>) field.get(networkSystem);
            if (!candidate.isEmpty()) {
                endpoints = candidate;
                break;
            }
        }
        require(endpoints != null, "server has no bound endpoint");
        Object channel = endpoints.get(0).getClass().getMethod("channel").invoke(endpoints.get(0));
        Object address = channel.getClass().getMethod("localAddress").invoke(channel);
        require(address instanceof java.net.InetSocketAddress, "server endpoint is not an internet socket");
        return ((java.net.InetSocketAddress) address).getPort();
    }

    static void fail(String reason, Throwable throwable) {
        if (FAILED.compareAndSet(false, true)) {
            LOGGER.error("SMOKE_FAIL: " + reason.replace('\n', ' ').replace('\r', ' '), throwable);
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && !server.isServerStopped()) {
            SHUTDOWN_REQUESTED.set(true);
            server.addScheduledTask(server::initiateShutdown);
        } else {
            FINISHED.set(true);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static long longProperty(String name, long defaultValue) {
        String value = System.getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
