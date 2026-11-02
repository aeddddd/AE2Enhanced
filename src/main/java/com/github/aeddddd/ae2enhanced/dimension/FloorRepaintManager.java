package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 每服务器生命周期的异步扫描与主线程分批重铺编排。 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class FloorRepaintManager implements AutoCloseable {
    private final ExecutorService scanner = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AE2E-Floor-Region-Scanner");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ScanRequest> scans = new HashMap<>();

    public static boolean updateFloor(EntityPlayerMP player, String styleId, int[] colors) {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null || !PersonalDimensionManager.isPersonalDimension(player.dimension)) {
            return false;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        DimensionOwner owner = data.getOwnerByDimensionId(player.dimension);
        if (!PrivateDimensionPermissions.canModifyRules(player, data, owner)) {
            return false;
        }
        owner.floor.setStyle(styleId, colors);
        FloorRepaintTask previous = readTask(owner);
        if (previous != null) {
            previous.cancel();
        }
        writeTask(data, owner, previous);
        PersonalDimensionManager.sendInfoToPlayersInDimension(owner.dimensionId);
        return true;
    }

    public static boolean startRepaint(EntityPlayerMP player) {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null || !PersonalDimensionManager.isPersonalDimension(player.dimension)) {
            return false;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        DimensionOwner owner = data.getOwnerByDimensionId(player.dimension);
        if (!PrivateDimensionPermissions.canModifyRules(player, data, owner)) {
            return false;
        }
        FloorRepaintTask previous = readTask(owner);
        if (previous != null) {
            previous.cancel();
        }
        FloorRepaintTask task = FloorRepaintTask.create(owner.floor.getRevision(),
                owner.entryPoint.getX() >> 4, owner.entryPoint.getZ() >> 4,
                ServerConfig.current().personalDimension.repaintRadiusChunks);
        writeTask(data, owner, task);
        PersonalDimensionManager.sendInfoToPlayersInDimension(owner.dimensionId);
        return true;
    }

    public static int getRepaintPending(DimensionOwner owner) {
        FloorRepaintTask task = readTask(owner);
        if (task == null || task.getStatus() == FloorRepaintTask.Status.COMPLETE
                || task.getStatus() == FloorRepaintTask.Status.CANCELLED) {
            return -1;
        }
        return task.getPendingChunks().size();
    }

    public static void cancel(int dimensionId) {
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        DimensionOwner owner = data.getOwnerByDimensionId(dimensionId);
        FloorRepaintTask task = readTask(owner);
        if (task != null) {
            task.cancel();
            writeTask(data, owner, task);
        }
    }

    private static FloorRepaintManager instance() {
        return RuntimeScopes.requireServer().service(FloorRepaintManager.class, FloorRepaintManager::new);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || event.world.provider.getDimension() != 0 || !RuntimeScopes.currentServer().isPresent()
                || !RuntimeScopes.currentServer().get().acceptingWork()) {
            return;
        }
        instance().tick(PersonalDimensionData.get(event.world), event.world.getTotalWorldTime());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getWorld().isRemote || !PersonalDimensionManager.isPersonalDimension(
                event.getWorld().provider.getDimension())) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(event.getWorld());
        DimensionOwner owner = data.getOwnerByDimensionId(event.getWorld().provider.getDimension());
        FloorRepaintTask task = readTask(owner);
        if (task == null || task.getRevision() != owner.floor.getRevision()
                || !task.containsChunk(event.getChunk().x, event.getChunk().z)) {
            return;
        }
        repaintChunk((WorldServer) event.getWorld(), owner, event.getChunk().x, event.getChunk().z);
        task.removeChunk(event.getChunk().x, event.getChunk().z);
        task.updateWaitingStatus();
        writeTask(data, owner, task);
    }

    private static List<TaskState> allTasks(PersonalDimensionData data) {
        List<TaskState> result = new ArrayList<>();
        for (PlayerDimEntry entry : data.getAllEntries()) {
            if (entry.dimensionId == Integer.MIN_VALUE || !entry.floorTask.hasKey("taskId", 8)) {
                continue;
            }
            FloorRepaintTask task = FloorRepaintTask.read(entry.floorTask);
            result.add(new TaskState(DimensionOwner.personal(entry), task, () -> {
                entry.floorTask = task.writeToNBT();
                data.markDirty();
            }));
        }
        for (TeamDimEntry entry : data.getAllTeamEntries()) {
            if (entry.dimensionId == Integer.MIN_VALUE || !entry.floorTask.hasKey("taskId", 8)) {
                continue;
            }
            FloorRepaintTask task = FloorRepaintTask.read(entry.floorTask);
            result.add(new TaskState(DimensionOwner.team(entry), task, () -> {
                entry.floorTask = task.writeToNBT();
                data.markDirty();
            }));
        }
        return result;
    }

    private static TaskState findTask(PersonalDimensionData data, int dimensionId, String taskId) {
        for (TaskState state : allTasks(data)) {
            if (state.owner.dimensionId == dimensionId && state.task.getTaskId().equals(taskId)) {
                return state;
            }
        }
        return null;
    }

    private static FloorRepaintTask readTask(DimensionOwner owner) {
        if (owner == null) {
            return null;
        }
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return null;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        if (owner.isTeam()) {
            TeamDimEntry entry = data.peekTeamEntry(owner.ownerId);
            return entry == null || !entry.floorTask.hasKey("taskId", 8)
                    ? null : FloorRepaintTask.read(entry.floorTask);
        }
        try {
            PlayerDimEntry entry = data.peekEntry(java.util.UUID.fromString(owner.ownerId));
            return entry == null || !entry.floorTask.hasKey("taskId", 8)
                    ? null : FloorRepaintTask.read(entry.floorTask);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void writeTask(PersonalDimensionData data, DimensionOwner owner, FloorRepaintTask task) {
        net.minecraft.nbt.NBTTagCompound tag = task == null ? new net.minecraft.nbt.NBTTagCompound() : task.writeToNBT();
        if (owner.isTeam()) {
            TeamDimEntry entry = data.peekTeamEntry(owner.ownerId);
            if (entry != null) {
                entry.floorTask = tag;
            }
        } else {
            try {
                PlayerDimEntry entry = data.peekEntry(java.util.UUID.fromString(owner.ownerId));
                if (entry != null) {
                    entry.floorTask = tag;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        data.markDirty();
    }

    private static void repaintChunk(WorldServer world, DimensionOwner owner, int chunkX, int chunkZ) {
        FloorPreset preset = FloorStyleRegistry.resolve(owner.floor);
        int floorY = ServerConfig.current().personalDimension.floorY;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        Chunk chunk = world.getChunk(chunkX, chunkZ);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                IBlockState target = preset.getState(x, z);
                BlockPos floorPos = new BlockPos(x, floorY, z);
                if (target != null && world.getBlockState(floorPos) != target) {
                    world.setBlockState(floorPos, target, 1);
                    world.checkLight(floorPos);
                }
                for (int depth = 1; depth <= 2 && floorY - depth >= 0; depth++) {
                    BlockPos below = floorPos.down(depth);
                    if (world.getBlockState(below).getBlock() == Blocks.BEDROCK) {
                        world.setBlockState(below, Blocks.AIR.getDefaultState(), 1);
                        world.checkLight(below);
                    }
                }
            }
        }
        chunk.markDirty();
        PlayerChunkMapEntry entry = world.getPlayerChunkMap().getEntry(chunkX, chunkZ);
        if (entry != null) {
            entry.sendPacket(new SPacketChunkData(chunk, 65535));
        }
    }

    private void tick(PersonalDimensionData data, long worldTime) {
        collectFinishedScans(data);
        for (TaskState state : allTasks(data)) {
            FloorRepaintTask task = state.task;
            if (task.getRevision() != state.owner.floor.getRevision()
                    || task.getStatus() == FloorRepaintTask.Status.CANCELLED
                    || task.getStatus() == FloorRepaintTask.Status.COMPLETE) {
                continue;
            }
            if (task.getStatus() == FloorRepaintTask.Status.SCANNING) {
                scheduleScan(state);
                continue;
            }
            WorldServer world = DimensionManager.getWorld(state.owner.dimensionId);
            if (world == null) {
                continue;
            }
            int budget = ServerConfig.current().personalDimension.repaintChunksPerTick;
            for (long key : task.getPendingChunks()) {
                if (budget <= 0) {
                    break;
                }
                int x = FloorRepaintTask.chunkX(key);
                int z = FloorRepaintTask.chunkZ(key);
                if (!world.getChunkProvider().chunkExists(x, z)) {
                    continue;
                }
                repaintChunk(world, state.owner, x, z);
                task.removeChunk(x, z);
                budget--;
            }
            task.updateWaitingStatus();
            state.save.run();
        }
        if (worldTime % 20L == 0L) {
            for (TaskState state : allTasks(data)) {
                if (state.task.getStatus() != FloorRepaintTask.Status.COMPLETE
                        && state.task.getStatus() != FloorRepaintTask.Status.CANCELLED) {
                    PersonalDimensionManager.sendInfoToPlayersInDimension(state.owner.dimensionId);
                }
            }
        }
    }

    private void scheduleScan(TaskState state) {
        FloorRepaintTask task = state.task;
        String key = task.getTaskId();
        if (scans.containsKey(key)) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(state.owner.dimensionId);
        if (world == null) {
            return;
        }
        long token = task.nextScanToken();
        int regionX = task.getNextRegionX();
        int regionZ = task.getNextRegionZ();
        int radius = task.getRadiusChunks();
        for (int chunkX = task.getCenterChunkX() - radius; chunkX <= task.getCenterChunkX() + radius; chunkX++) {
            for (int chunkZ = task.getCenterChunkZ() - radius; chunkZ <= task.getCenterChunkZ() + radius; chunkZ++) {
                if (world.getChunkProvider().chunkExists(chunkX, chunkZ)) {
                    task.addScannedChunks(java.util.Collections.singletonList(
                            FloorRepaintTask.chunkKey(chunkX, chunkZ)));
                }
            }
        }
        state.save.run();
        Path regionDirectory = world.getChunkSaveLocation().toPath().resolve("region");
        CompletableFuture<List<Long>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return FloorRegionScanner.scan(regionDirectory, regionX, regionZ,
                        task.getCenterChunkX() - radius, task.getCenterChunkX() + radius,
                        task.getCenterChunkZ() - radius, task.getCenterChunkZ() + radius);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }, scanner);
        scans.put(key, new ScanRequest(state.owner.dimensionId, task.getTaskId(), token,
                task.getRevision(), future));
        state.save.run();
    }

    private void collectFinishedScans(PersonalDimensionData data) {
        Iterator<ScanRequest> iterator = scans.values().iterator();
        while (iterator.hasNext()) {
            ScanRequest request = iterator.next();
            if (!request.future.isDone()) {
                continue;
            }
            iterator.remove();
            TaskState state = findTask(data, request.dimensionId, request.taskId);
            if (state == null || state.task.getScanToken() != request.token
                    || state.task.getRevision() != request.revision) {
                continue;
            }
            try {
                state.task.addScannedChunks(request.future.join());
            } catch (Exception exception) {
                AE2Enhanced.LOGGER.warn("[AE2E] Floor region scan failed for dimension {} revision {}; region skipped",
                        request.dimensionId, request.revision, exception);
            }
            state.task.advanceRegion();
            state.task.updateWaitingStatus();
            state.save.run();
        }
    }

    @Override
    public void close() {
        for (ScanRequest request : scans.values()) {
            request.future.cancel(true);
        }
        scans.clear();
        scanner.shutdownNow();
    }

    private static final class TaskState {
        private final DimensionOwner owner;
        private final FloorRepaintTask task;
        private final Runnable save;

        private TaskState(DimensionOwner owner, FloorRepaintTask task, Runnable save) {
            this.owner = owner;
            this.task = task;
            this.save = save;
        }
    }

    private static final class ScanRequest {
        private final int dimensionId;
        private final String taskId;
        private final long token;
        private final long revision;
        private final CompletableFuture<List<Long>> future;

        private ScanRequest(int dimensionId, String taskId, long token, long revision,
                CompletableFuture<List<Long>> future) {
            this.dimensionId = dimensionId;
            this.taskId = taskId;
            this.token = token;
            this.revision = revision;
            this.future = future;
        }
    }
}
