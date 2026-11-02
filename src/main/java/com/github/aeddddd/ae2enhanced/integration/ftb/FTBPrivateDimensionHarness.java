package com.github.aeddddd.ae2enhanced.integration.ftb;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.TeamType;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.FloorRepaintTask;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData;
import com.github.aeddddd.ae2enhanced.dimension.TeamDimEntry;
import com.github.aeddddd.ae2enhanced.dimension.dev.PrivateDimensionHarness;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;

/** 双客户端隔离环境专用 FTB 团队驱动，系统属性未开启时不注册。 */
public final class FTBPrivateDimensionHarness {
    private static final String TEAM_ID = "ae2e_harness";
    private UUID ownerId;
    private UUID memberId;
    private boolean bedrockInjected;
    private boolean passed;
    private boolean resumeChecked;
    private boolean restartArmed;
    private int removeCountdown = -1;

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!PrivateDimensionHarness.enabled() || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        for (String node : new String[]{"ae2e", "ae2enhanced", "ae2enhanced.ae2e", "ae2enhanced.admin"}) {
            AE2Enhanced.LOGGER.info("PRIVATE_DIM_PERMISSION user={} node={} ranks={} forge={}",
                    event.player.getName(), node,
                    com.feed_the_beast.ftbutilities.ranks.Ranks.INSTANCE.getPermissionResult(
                            (EntityPlayerMP) event.player, node, true),
                    net.minecraftforge.server.permission.PermissionAPI.hasPermission(event.player, node));
        }
        net.minecraft.command.ICommand command = event.player.getServer().getCommandManager().getCommands().get("ae2e");
        if (command != null) {
            AE2Enhanced.LOGGER.info("PRIVATE_DIM_COMMAND_CHECK user={} type={} allowed={} level={}",
                    event.player.getName(), command.getClass().getName(), command.checkPermission(
                            event.player.getServer(), (EntityPlayerMP) event.player),
                    ((net.minecraft.command.CommandBase) command).getRequiredPermissionLevel());
            if (command instanceof com.feed_the_beast.ftbutilities.ranks.CommandOverride) {
                AE2Enhanced.LOGGER.info("PRIVATE_DIM_COMMAND_MIRROR user={} type={} allowed={}",
                        event.player.getName(),
                        ((com.feed_the_beast.ftbutilities.ranks.CommandOverride) command).mirrored.getClass().getName(),
                        ((com.feed_the_beast.ftbutilities.ranks.CommandOverride) command).mirrored.checkPermission(
                                event.player.getServer(), (EntityPlayerMP) event.player));
            }
        }
        ForgePlayer player = Universe.get().getPlayer(event.player.getUniqueID());
        ForgeTeam team = Universe.get().getTeam(TEAM_ID);
        if (team == null || !team.isValid()) {
            team = new ForgeTeam(Universe.get(), (short) 31999, TEAM_ID, TeamType.PLAYER);
            Universe.get().addTeam(team);
        }
        boolean ownerLogin = "DeveloperPDA".equals(event.player.getName());
        if (ownerLogin && ownerId == null) {
            ownerId = event.player.getUniqueID();
            team.owner = player;
            player.team = team;
            team.players.put(player, com.feed_the_beast.ftblib.lib.EnumTeamStatus.OWNER);
            team.markDirty();
        } else if (!ownerLogin && !team.isMember(player)) {
            memberId = event.player.getUniqueID();
            team.setStatus(player, com.feed_the_beast.ftblib.lib.EnumTeamStatus.INVITED);
            team.addMember(player, false);
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!PrivateDimensionHarness.enabled() || event.phase != TickEvent.Phase.END
                || event.world.provider.getDimension() != 0) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(event.world);
        TeamDimEntry entry = data.peekTeamEntry(TEAM_ID);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return;
        }
        WorldServer world = DimensionManager.getWorld(entry.dimensionId);
        if (world == null) {
            return;
        }
        if (Boolean.getBoolean("ae2e.harness.privateRestart") && !resumeChecked) {
            FloorRepaintTask resumed = entry.floorTask.hasKey("taskId", 8)
                    ? FloorRepaintTask.read(entry.floorTask) : null;
            if (resumed != null && resumed.getRevision() == 3L) {
                if (event.world.getTotalWorldTime() % 100L == 0L) {
                    AE2Enhanced.LOGGER.info(
                            "PRIVATE_DIM_RESUME_STATUS status={} pending={} region={},{} center={} colorRole1={} target={} entry={}",
                            resumed.getStatus(), resumed.getPendingChunks().size(), resumed.getNextRegionX(),
                            resumed.getNextRegionZ(), world.getBlockState(entry.entryPoint.down()),
                            entry.floor.getColorScheme()[1],
                            com.github.aeddddd.ae2enhanced.dimension.FloorStyleRegistry.resolve(entry.floor)
                                    .getState(entry.entryPoint.getX(), entry.entryPoint.getZ()), entry.entryPoint);
                }
                if (resumed.getRadiusChunks() == 64
                        && resumed.getStatus() == FloorRepaintTask.Status.COMPLETE
                        && resumed.getPendingChunks().isEmpty()) {
                    BlockPos pos = entry.entryPoint.down();
                    net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                    if (state.getBlock() == Blocks.CONCRETE && state.getBlock().getMetaFromState(state) == 14) {
                        resumeChecked = true;
                        bedrockInjected = true;
                        passed = true;
                        AE2Enhanced.LOGGER.info(
                                "PRIVATE_DIM_RESUME_PASS dim={} revision={} status={} pending=0 sample={},{},{}",
                                entry.dimensionId, resumed.getRevision(), resumed.getStatus(),
                                pos.getX(), pos.getY(), pos.getZ());
                    }
                } else if (resumed.getRadiusChunks() == 64
                        && resumed.getStatus() == FloorRepaintTask.Status.WAITING_FOR_LOAD
                        && !resumed.getPendingChunks().isEmpty()) {
                    java.util.List<Long> pending = resumed.getPendingChunks();
                    long sample = pending.get(pending.size() - 1);
                    int chunkX = FloorRepaintTask.chunkX(sample);
                    int chunkZ = FloorRepaintTask.chunkZ(sample);
                    BlockPos pos = new BlockPos((chunkX << 4) + 8, entry.entryPoint.getY() - 1,
                            (chunkZ << 4) + 8);
                    world.getChunk(pos);
                    net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
                    FloorRepaintTask current = FloorRepaintTask.read(entry.floorTask);
                    if (state.getBlock() == Blocks.CONCRETE && state.getBlock().getMetaFromState(state) == 14
                            && !current.containsChunk(chunkX, chunkZ)) {
                        resumeChecked = true;
                        bedrockInjected = true;
                        passed = true;
                        AE2Enhanced.LOGGER.info(
                                "PRIVATE_DIM_RESUME_PASS dim={} revision={} status={} pending={} sample={},{},{}",
                                entry.dimensionId, current.getRevision(), current.getStatus(),
                                current.getPendingChunks().size(), pos.getX(), pos.getY(), pos.getZ());
                    }
                }
                return;
            }
        }
        if (!bedrockInjected) {
            BlockPos floor = entry.entryPoint.down();
            world.setBlockState(floor.down(), Blocks.BEDROCK.getDefaultState());
            world.setBlockState(floor.down(2), Blocks.BEDROCK.getDefaultState());
            bedrockInjected = true;
        }
        if (event.world.getTotalWorldTime() % 20L != 0L) {
            return;
        }
        FloorRepaintTask task = entry.floorTask.hasKey("taskId", 8)
                ? FloorRepaintTask.read(entry.floorTask) : null;
        if (!passed && task != null && task.getStatus() == FloorRepaintTask.Status.COMPLETE
                && task.getRevision() == entry.floor.getRevision()
                && entry.floor.getColorScheme()[1] == 11) {
            MinecraftServerPlayers players = new MinecraftServerPlayers((WorldServer) event.world);
            if (players.bothInside(entry.dimensionId, ownerId, memberId)) {
                passed = true;
                removeCountdown = 100;
                AE2Enhanced.LOGGER.info("PRIVATE_DIM_SERVER_PASS dim={} revision={} style={} resolved={} center={} asyncRepaint=COMPLETE",
                        entry.dimensionId, entry.floor.getRevision(), entry.floor.getFloorStyleId(),
                        com.github.aeddddd.ae2enhanced.dimension.FloorStyleRegistry.resolve(entry.floor).getState(0, 0),
                        world.getBlockState(entry.entryPoint.down()));
                onServerStopping();
            }
        }
        if (passed && removeCountdown > 0 && --removeCountdown == 0 && memberId != null && Universe.loaded()) {
            ForgeTeam team = Universe.get().getTeam(TEAM_ID);
            ForgePlayer member = Universe.get().getPlayer(memberId);
            if (team != null && member != null && team.isMember(member)) {
                team.removeMember(member);
                AE2Enhanced.LOGGER.info("PRIVATE_DIM_LEFT_SERVER member={}", memberId);
            }
        }
    }

    public void onServerStopping() {
        if (restartArmed || !Boolean.getBoolean("ae2e.harness.privateRestart")) {
            return;
        }
        restartArmed = true;
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        TeamDimEntry entry = data.peekTeamEntry(TEAM_ID);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return;
        }
        int[] colors = entry.floor.getColorScheme();
        colors[1] = 14;
        entry.floor.setStyle(PrivateDimensionHarness.STYLE_ID, colors);
        FloorRepaintTask task = FloorRepaintTask.create(entry.floor.getRevision(),
                entry.entryPoint.getX() >> 4, entry.entryPoint.getZ() >> 4, 64);
        entry.floorTask = task.writeToNBT();
        data.markDirty();
        AE2Enhanced.LOGGER.info("PRIVATE_DIM_RESTART_ARMED dim={} revision={} radius=64",
                entry.dimensionId, entry.floor.getRevision());
    }

    private static final class MinecraftServerPlayers {
        private final WorldServer world;

        private MinecraftServerPlayers(WorldServer world) {
            this.world = world;
        }

        private boolean bothInside(int dimensionId, UUID owner, UUID member) {
            if (owner == null || member == null || world.getMinecraftServer() == null) {
                return false;
            }
            EntityPlayerMP first = world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
            EntityPlayerMP second = world.getMinecraftServer().getPlayerList().getPlayerByUUID(member);
            return first != null && second != null && first.dimension == dimensionId && second.dimension == dimensionId;
        }
    }
}
