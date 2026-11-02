package com.github.aeddddd.ae2enhanced.verification;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import com.github.aeddddd.ae2enhanced.centralinterface.DualityCentralInterface;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetOwnershipTracker;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.PhysicalSessionRecord;
import com.github.aeddddd.ae2enhanced.pathing.GridValidationBatcher;
import com.github.aeddddd.ae2enhanced.block.BlockRegistry;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import com.github.aeddddd.ae2enhanced.tile.network.WirelessChannelConnectionRegistry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

final class ScopeChainVerifier {
    private static final Logger LOGGER = LogManager.getLogger("ae2enhancedsmoke.scope");

    private static String caseId;
    private static Path saveRoot;
    private static ServerScope scope;
    private static TargetOwnershipTracker tracker;
    private static TargetBinding binding;
    private static DualityCentralInterface owner;
    private static WorldServer world;
    private static TileCentralMEInterface tile;
    private static GridValidationBatcher gridBatcher;
    private static WirelessChannelConnectionRegistry wirelessRegistry;
    private static IGridNode wirelessNode;
    private static IGridConnection wirelessConnection;
    private static AtomicInteger wirelessDestroyCalls;

    private ScopeChainVerifier() {
    }

    static boolean enabled() {
        return System.getProperty("ae2e.smoke.scopeCase") != null;
    }

    static void started() throws Exception {
        caseId = requiredProperty("ae2e.smoke.scopeCase");
        File expectedRoot = new File(requiredProperty("ae2e.smoke.scopeRoot"));
        world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        saveRoot = world.getSaveHandler().getWorldDirectory().getCanonicalFile().toPath();
        require(saveRoot.equals(expectedRoot.getCanonicalFile().toPath()),
                "scope root mismatch: " + saveRoot + " vs " + expectedRoot);
        scope = RuntimeScopes.requireServer();
        require(scope.saveRoot().isPresent() && scope.saveRoot().get().equals(saveRoot),
                "ServerScope was not bound to the real world directory");
        require(scope.acceptingWork() && !scope.closed(), "new ServerScope is not active");
        tracker = TargetOwnershipTracker.instance();
        require(tracker.trackedTargetCount() == 0, "ownership leaked into " + caseId);

        BlockPos pos = new BlockPos(0, world.getHeight(0, 0), 0);
        TileEntity candidate = world.getTileEntity(pos);
        if (!(candidate instanceof TileCentralMEInterface)) {
            net.minecraft.block.Block centralBlock = BlockRegistry.CENTRAL_ME_INTERFACE;
            require(world.setBlockState(pos, centralBlock.getDefaultState()),
                    "could not place Central pilot block");
            candidate = world.getTileEntity(pos);
        }
        require(candidate instanceof TileCentralMEInterface, "Central pilot Tile is absent");
        tile = (TileCentralMEInterface) candidate;
        owner = tile.getInterfaceDuality();
        binding = new TargetBinding(pos, world.provider.getDimension(),
                BlockRegistry.CENTRAL_ME_INTERFACE.getRegistryName().toString());
        require(tracker.tryAcquire(binding, owner), "could not acquire Central pilot ownership");
        require(tracker.trackedTargetCount() == 1, "pilot ownership was not recorded");
        int baselineServiceCount = scope.serviceCount();
        gridBatcher = scope.service(GridValidationBatcher.class, GridValidationBatcher::new);
        require(gridBatcher.batchDepth() == 0 && gridBatcher.pendingNodeCount() == 0,
                "Grid validation state leaked into " + caseId);
        wirelessRegistry = scope.service(
                WirelessChannelConnectionRegistry.class, WirelessChannelConnectionRegistry::new);
        require(wirelessRegistry.connectionCount() == 0, "wireless connection leaked into " + caseId);
        wirelessNode = (IGridNode) Proxy.newProxyInstance(ScopeChainVerifier.class.getClassLoader(),
                new Class<?>[]{IGridNode.class}, ScopeChainVerifier::stubProxy);
        wirelessDestroyCalls = new AtomicInteger();
        wirelessConnection = (IGridConnection) Proxy.newProxyInstance(ScopeChainVerifier.class.getClassLoader(),
                new Class<?>[]{IGridConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("destroy")) {
                        wirelessDestroyCalls.incrementAndGet();
                    }
                    return stubProxy(proxy, method, args);
                });
        wirelessRegistry.register(wirelessNode, wirelessConnection);
        require(wirelessRegistry.connectionCount() == 1, "wireless lifecycle pilot was not registered");
        record("started", 1, baselineServiceCount + 2, true, false, true);
        if ("A1".equals(caseId)) {
            TargetSession session = new TargetSession(binding, owner);
            require(session.beginPush(Collections.emptyList()), "could not begin Central recovery pilot");
            session.commitPush(null, Collections.emptyList(), Collections.emptyList(), world.getTotalWorldTime());
            require(session.getSessionId() != null, "Central recovery pilot has no session ID");
            Files.write(saveRoot.resolve("central-recovery-id.txt"),
                    Collections.singletonList(session.getSessionId().toString()), StandardCharsets.UTF_8);
            LOGGER.info("CENTRAL_RECOVERY_COMMITTED case=A1 id={}", session.getSessionId());
        } else if ("A2".equals(caseId)) {
            String expected = new String(Files.readAllBytes(saveRoot.resolve("central-recovery-id.txt")),
                    StandardCharsets.UTF_8).trim();
            net.minecraft.nbt.NBTTagList records = writeTileNbt(tile)
                    .getTagList("inflightSessions", 10);
            require(records.tagCount() == 1, "Central recovery record count changed across restart");
            PhysicalSessionRecord restored = PhysicalSessionRecord.readFromNBT(records.getCompoundTagAt(0));
            require(restored.id.toString().equals(expected),
                    "Central recovery session ID changed across real server restart");
            LOGGER.info("CENTRAL_RECOVERY_RESTORED case=A2 id={}", restored.id);
        }
    }

    private static net.minecraft.nbt.NBTTagCompound writeTileNbt(TileEntity tile) throws Exception {
        net.minecraft.nbt.NBTTagCompound compound = new net.minecraft.nbt.NBTTagCompound();
        for (String name : new String[]{"func_189515_b", "writeToNBT"}) {
            try {
                TileEntity.class.getMethod(name, net.minecraft.nbt.NBTTagCompound.class)
                        .invoke(tile, compound);
                return compound;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("TileEntity NBT writer unavailable");
    }

    static void stopping() throws Exception {
        if (!enabled()) {
            return;
        }
        require(scope != null && tracker != null, "stopping arrived without started scope capture");
        require(!scope.acceptingWork(), "serverStopping did not reject new work");
        require(scope.closed(), "serverStopping did not close the scope");
        require(tracker.trackedTargetCount() == 0, "ownership survived serverStopping");
        require(!tracker.isOwner(binding, owner), "closed tracker still references the Central owner");
        require(gridBatcher.batchDepth() == 0 && gridBatcher.pendingNodeCount() == 0,
                "Grid validation state survived serverStopping");
        require(wirelessRegistry.connectionCount() == 0 && wirelessDestroyCalls.get() == 1,
                "wireless connection was not destroyed exactly once during serverStopping");
        require(scope.serviceCount() == 0 && !scope.saveRoot().isPresent(),
                "closed scope retains services or save root");
        record("stopping", 0, 0, false, true, true);
        binding = null;
        owner = null;
        tile = null;
        world = null;
        wirelessNode = null;
        wirelessConnection = null;
    }

    static void stopped() throws Exception {
        if (!enabled()) {
            return;
        }
        require(!RuntimeScopes.currentServer().isPresent(), "serverStopped retained the active scope");
        require(scope.closed() && scope.serviceCount() == 0 && !scope.saveRoot().isPresent(),
                "released scope still has live state");
        require(tracker.trackedTargetCount() == 0, "released tracker still has owners");
        require(world == null && tile == null && owner == null && wirelessNode == null && wirelessConnection == null,
                "verification retained a World/Tile/owner/Grid reference");
        record("stopped", 0, 0, false, true, false);
        LOGGER.info("SCOPE_CHAIN_PASS case={} root={}", caseId, saveRoot);
        scope = null;
        tracker = null;
        gridBatcher = null;
        wirelessRegistry = null;
        wirelessDestroyCalls = null;
    }

    private static Object stubProxy(Object proxy, java.lang.reflect.Method method, Object[] args) {
        if (method.getName().equals("hashCode")) {
            return System.identityHashCode(proxy);
        }
        if (method.getName().equals("equals")) {
            return proxy == args[0];
        }
        if (method.getName().equals("toString")) {
            return "M6d lifecycle pilot " + proxy.getClass().getInterfaces()[0].getSimpleName();
        }
        return null;
    }

    private static void record(String event, int owners, int services,
                               boolean accepting, boolean closed, boolean active) throws Exception {
        Path journal = new File(requiredProperty("ae2e.smoke.scopeJournal")).getCanonicalFile().toPath();
        Files.createDirectories(journal.getParent());
        String line = caseId + '\t' + event + '\t' + saveRoot + '\t' + scope.scopeId() + '\t'
                + tracker.trackedTargetCount() + '\t' + scope.serviceCount() + '\t'
                + scope.acceptingWork() + '\t' + scope.closed() + '\t'
                + RuntimeScopes.currentServer().isPresent();
        require(tracker.trackedTargetCount() == owners, "ownership count evidence mismatch");
        require(scope.serviceCount() == services, "service count evidence mismatch");
        require(scope.acceptingWork() == accepting && scope.closed() == closed,
                "scope state evidence mismatch");
        require(RuntimeScopes.currentServer().isPresent() == active, "active scope evidence mismatch");
        Files.write(journal, Collections.singletonList(line), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        LOGGER.info("SCOPE_CHAIN {}", line);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        require(value != null && !value.trim().isEmpty(), "missing " + name);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
