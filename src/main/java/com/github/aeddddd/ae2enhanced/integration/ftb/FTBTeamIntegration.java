package com.github.aeddddd.ae2enhanced.integration.ftb;

import com.feed_the_beast.ftblib.events.team.ForgeTeamDataEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamDeletedEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamLoadedEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamOwnerChangedEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamPlayerJoinedEvent;
import com.feed_the_beast.ftblib.events.team.ForgeTeamPlayerLeftEvent;
import com.feed_the_beast.ftblib.lib.data.ForgePlayer;
import com.feed_the_beast.ftblib.lib.data.ForgeTeam;
import com.feed_the_beast.ftblib.lib.data.NBTDataStorage;
import com.feed_the_beast.ftblib.lib.data.TeamData;
import com.feed_the_beast.ftblib.lib.data.TeamType;
import com.feed_the_beast.ftblib.lib.data.Universe;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamDimensionAccess;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamInfo;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

/** 仅在 FTBLib 存在且联动开启时加载的 FTB 团队适配器。 */
public final class FTBTeamIntegration {
    private static final String DATA_ID = "ae2enhanced";
    private static FTBPrivateDimensionHarness privateHarness;

    private FTBTeamIntegration() {
    }

    public static void onServerStopping() {
        if (privateHarness != null) {
            privateHarness.onServerStopping();
        }
    }

    public static void init() {
        registerRankDefaults();
        TeamDimensionAccess.setProvider(new FTBProvider());
        MinecraftForge.EVENT_BUS.register(new Listener());
        if (com.github.aeddddd.ae2enhanced.dimension.dev.PrivateDimensionHarness.enabled()) {
            privateHarness = new FTBPrivateDimensionHarness();
            MinecraftForge.EVENT_BUS.register(privateHarness);
        }
        AE2Enhanced.LOGGER.info("[AE2E] FTB player-team dimensions enabled");
    }

    private static void registerRankDefaults() {
        com.feed_the_beast.ftblib.lib.config.ConfigBoolean allow = new com.feed_the_beast.ftblib.lib.config.ConfigBoolean(true);
        com.feed_the_beast.ftblib.lib.config.ConfigBoolean deny = new com.feed_the_beast.ftblib.lib.config.ConfigBoolean(false);
        for (String node : new String[]{"ae2e", "ae2enhanced", "ae2enhanced.ae2e",
                "ae2enhanced.personal_dimension.enter", "ae2enhanced.personal_dimension.build",
                "ae2enhanced.personal_dimension.interact"}) {
            com.feed_the_beast.ftblib.lib.config.RankConfigAPI.getHandler().registerRankConfig(
                    new com.feed_the_beast.ftblib.lib.config.RankConfigValueInfo(node, allow, allow));
        }
        for (String node : new String[]{"ae2enhanced.admin", "ae2enhanced.personal_dimension.manage",
                "ae2enhanced.personal_dimension.bypass"}) {
            com.feed_the_beast.ftblib.lib.config.RankConfigAPI.getHandler().registerRankConfig(
                    new com.feed_the_beast.ftblib.lib.config.RankConfigValueInfo(node, deny, allow));
        }
    }

    private static ForgeTeam team(String teamId) {
        if (!Universe.loaded()) {
            return null;
        }
        ForgeTeam team = Universe.get().getTeam(teamId);
        return team != null && team.isValid() && team.type == TeamType.PLAYER ? team : null;
    }

    private static ForgePlayer player(UUID playerId) {
        return Universe.loaded() ? Universe.get().getPlayer(playerId) : null;
    }

    private static final class FTBProvider implements TeamDimensionAccess.Provider {
        @Override
        public boolean isAvailable() {
            return Universe.loaded();
        }

        @Override
        public Optional<TeamInfo> currentTeam(UUID playerId) {
            ForgePlayer player = player(playerId);
            if (player == null || !player.hasTeam()) {
                return Optional.empty();
            }
            ForgeTeam team = team(player.team.getId());
            return team == null ? Optional.empty()
                    : Optional.of(new TeamInfo(team.getId(), team.getTitle().getUnformattedText()));
        }

        @Override
        public boolean isMember(String teamId, UUID playerId) {
            ForgeTeam team = team(teamId);
            ForgePlayer player = player(playerId);
            return team != null && player != null && team.isMember(player);
        }

        @Override
        public boolean canManage(String teamId, UUID playerId) {
            ForgeTeam team = team(teamId);
            ForgePlayer player = player(playerId);
            return team != null && player != null && (team.isModerator(player) || team.isOwner(player));
        }

        @Override
        public boolean isBindingReady(String teamId) {
            ForgeTeam team = team(teamId);
            return team != null && team.getData().get(DATA_ID) != null;
        }

        @Override
        public boolean hasValidBinding(String teamId, String bindingId) {
            ForgeTeam team = team(teamId);
            if (team == null || bindingId == null || bindingId.isEmpty()) {
                return false;
            }
            BindingData data = team.getData().get(DATA_ID);
            return data != null && bindingId.equals(data.bindingId);
        }

        @Override
        public void bind(String teamId, String bindingId) {
            ForgeTeam team = team(teamId);
            if (team == null) {
                throw new IllegalStateException("Unknown or non-player FTB team " + teamId);
            }
            BindingData data = team.getData().get(DATA_ID);
            if (data == null) {
                data = new BindingData(team);
                team.getData().add(data);
            }
            if (data.bindingId.isEmpty()) {
                data.bindingId = bindingId;
                team.markDirty();
            } else if (!data.bindingId.equals(bindingId)) {
                throw new IllegalStateException("FTB team binding mismatch for " + teamId);
            }
        }
    }

    public static final class BindingData extends TeamData {
        private String bindingId = "";

        public BindingData(ForgeTeam team) {
            super(team);
        }

        @Override
        public String getId() {
            return DATA_ID;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("bindingId", bindingId);
            return tag;
        }

        @Override
        public void deserializeNBT(NBTTagCompound tag) {
            bindingId = tag.getString("bindingId");
        }

        @Override
        public void clearCache() {
        }
    }

    public static final class Listener {
        private static void isolate(Runnable action) {
            try {
                action.run();
            } catch (Throwable throwable) {
                AE2Enhanced.LOGGER.error("[AE2E] Isolated FTB team listener failure", throwable);
            }
        }

        @SubscribeEvent
        public void onData(ForgeTeamDataEvent event) {
            event.register(new BindingData(event.getTeam()));
        }

        @SubscribeEvent
        public void onJoined(ForgeTeamPlayerJoinedEvent event) {
            isolate(() -> PersonalDimensionManager.onTeamChanged());
        }

        @SubscribeEvent
        public void onLeft(ForgeTeamPlayerLeftEvent event) {
            isolate(() -> PersonalDimensionManager.onTeamLeft(
                    event.getTeam().getId(), event.getPlayer().getId()));
        }

        @SubscribeEvent
        public void onDeleted(ForgeTeamDeletedEvent event) {
            isolate(() -> PersonalDimensionManager.onTeamDeleted(event.getTeam().getId()));
        }

        @SubscribeEvent
        public void onOwnerChanged(ForgeTeamOwnerChangedEvent event) {
            isolate(PersonalDimensionManager::onTeamChanged);
        }

        @SubscribeEvent
        public void onLoaded(ForgeTeamLoadedEvent event) {
            isolate(() -> PersonalDimensionManager.onTeamLoaded(event.getTeam().getId()));
        }
    }
}
