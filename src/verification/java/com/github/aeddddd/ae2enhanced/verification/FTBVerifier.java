package com.github.aeddddd.ae2enhanced.verification;

import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.TeamType;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.TeamDimEntry;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamDimensionAccess;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;

import java.util.UUID;

/** 使用真实 FTBLib Universe 的团队绑定与事件验收。 */
final class FTBVerifier {
    private static boolean pending;

    private FTBVerifier() {
    }

    static boolean awaiting() {
        return pending;
    }

    static void start(WorldServer world) {
        pending = true;
        java.util.concurrent.ScheduledExecutorService delay = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AE2E-FTB-Verify-Delay");
            thread.setDaemon(true);
            return thread;
        });
        delay.schedule(() -> {
            world.getMinecraftServer().addScheduledTask(() -> {
                try {
                    verify(world);
                    pending = false;
                    ServerSmokeMod.requestShutdown();
                } catch (Throwable throwable) {
                    pending = false;
                    ServerSmokeMod.fail("FTB verification failed: " + throwable, throwable);
                }
            });
            delay.shutdown();
        }, 2L, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void verify(WorldServer world) {
        Universe universe = Universe.get();
        require(Universe.loaded(), "FTB Universe is not loaded");
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String teamId = "ae2e_verify_" + UUID.randomUUID().toString().substring(0, 8);
        ForgePlayer owner = new ForgePlayer(universe, new GameProfile(ownerId, "AE2EOwner"));
        ForgePlayer member = new ForgePlayer(universe, new GameProfile(memberId, "AE2EMember"));
        universe.players.put(ownerId, owner);
        universe.players.put(memberId, member);
        ForgeTeam team = new ForgeTeam(universe, (short) 32000, teamId, TeamType.PLAYER);
        universe.addTeam(team);
        try {
            team.owner = owner;
            owner.team = team;
            team.players.put(owner, com.feed_the_beast.ftblib.lib.EnumTeamStatus.OWNER);
            team.setStatus(member, com.feed_the_beast.ftblib.lib.EnumTeamStatus.INVITED);
            require(team.addMember(member, false), "FTB invited member could not join");
            require(TeamDimensionAccess.currentTeam(ownerId).isPresent(), "FTB owner team was not resolved");
            require(TeamDimensionAccess.currentTeam(ownerId).get().getTeamId().equals(teamId),
                    "FTB team id mismatch");
            require(TeamDimensionAccess.isMember(teamId, memberId), "FTB member was not recognized");
            require(TeamDimensionAccess.canManage(teamId, ownerId), "FTB owner cannot manage");
            require(!TeamDimensionAccess.canManage(teamId, memberId), "Ordinary FTB member can manage");

            String binding = UUID.randomUUID().toString();
            TeamDimensionAccess.bind(teamId, binding);
            require(TeamDimensionAccess.hasValidBinding(teamId, binding), "FTB binding mirror was not saved");
            PersonalDimensionData data = PersonalDimensionData.get(world);
            TeamDimEntry entry = data.getTeamEntry(teamId, binding);
            entry.entryPoint = new net.minecraft.util.math.BlockPos(4, 65, 4);
            data.quarantineTeamEntry(teamId);
            require(data.peekTeamEntry(teamId) == null, "Old team entry was not quarantined");
            require(data.getOrphanedTeamEntries().stream().anyMatch(old -> old.bindingId.equals(binding)),
                    "Old binding was not retained in quarantine");
            TeamDimEntry replacement = data.getTeamEntry(teamId, "different-binding");
            require(replacement.dimensionId == Integer.MIN_VALUE, "Replacement inherited old dimension id");

            PersonalDimensionManager.onTeamLeft(teamId, memberId);
            PersonalDimensionManager.onTeamLeft(teamId, memberId);
            team.delete();
            require(data.peekTeamEntry(teamId) == null, "Deleted team entry remained active");
            LogManager.getLogger("ae2enhancedsmoke").info(
                    "FTB_PASS version=5.4.7.2 teamType=PLAYER bindingGuard=true repeatedLeft=true");
        } finally {
            if (team.isValid()) {
                team.delete();
            }
            universe.players.remove(ownerId);
            universe.players.remove(memberId);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
