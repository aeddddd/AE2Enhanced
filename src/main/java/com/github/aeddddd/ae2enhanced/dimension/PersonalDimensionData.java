package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 个人与团队私域维度的主世界持久化权威，数据名与旧个人 entries 布局保持不变。 */
public class PersonalDimensionData extends WorldSavedData {
    private static final String DATA_NAME = AE2Enhanced.MOD_ID + "_personal_dimensions";
    private static final String LEGACY_DATA_NAME = AE2Enhanced.MOD_ID + ":personal_dimensions";
    private static final int CURRENT_VERSION = 2;

    private final Map<UUID, PlayerDimEntry> entries = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> dimToPlayer = new ConcurrentHashMap<>();
    private final Map<String, TeamDimEntry> teamEntries = new ConcurrentHashMap<>();
    private final Map<Integer, String> dimToTeam = new ConcurrentHashMap<>();
    private final Map<String, TeamDimEntry> orphanedTeamEntries = new ConcurrentHashMap<>();
    private final Map<UUID, DimensionPreference> preferences = new ConcurrentHashMap<>();

    public PersonalDimensionData() {
        super(DATA_NAME);
    }

    public PersonalDimensionData(String name) {
        super(name);
    }

    public PlayerDimEntry getEntry(UUID playerId) {
        return entries.computeIfAbsent(playerId, PlayerDimEntry::new);
    }

    @Nullable
    public PlayerDimEntry peekEntry(UUID playerId) {
        return entries.get(playerId);
    }

    private static NBTTagList writeTeams(java.util.List<TeamDimEntry> entries) {
        entries.sort(Comparator.comparing((TeamDimEntry entry) -> entry.teamId).thenComparing(entry -> entry.bindingId));
        NBTTagList list = new NBTTagList();
        for (TeamDimEntry entry : entries) {
            list.appendTag(entry.writeToNBT());
        }
        return list;
    }

    @Nullable
    public PlayerDimEntry getEntryByDimensionId(int dimId) {
        UUID id = dimToPlayer.get(dimId);
        return id != null ? entries.get(id) : null;
    }

    private static String orphanKey(String teamId, String bindingId) {
        return teamId + "#" + bindingId;
    }

    public void setRules(UUID playerId, PersonalDimensionRules rules) {
        getEntry(playerId).rules = rules.copy();
        markDirty();
    }

    public void setEntryPoint(UUID playerId, net.minecraft.util.math.BlockPos pos) {
        getEntry(playerId).entryPoint = pos;
        markDirty();
    }

    public void setReturnPoint(UUID playerId, int dim, double x, double y, double z, float yaw, float pitch) {
        PlayerDimEntry entry = getEntry(playerId);
        entry.returnDim = dim;
        entry.returnX = x;
        entry.returnY = y;
        entry.returnZ = z;
        entry.returnYaw = yaw;
        entry.returnPitch = pitch;
        entry.hasReturnPoint = true;
        markDirty();
    }

    public void clearReturnPoint(UUID playerId) {
        PlayerDimEntry entry = peekEntry(playerId);
        if (entry != null && entry.hasReturnPoint) {
            entry.hasReturnPoint = false;
            markDirty();
        }
    }

    public void clearReturnPointIfInDimension(UUID playerId, int dimId) {
        PlayerDimEntry entry = peekEntry(playerId);
        if (entry != null && entry.hasReturnPoint && entry.returnDim == dimId) {
            entry.hasReturnPoint = false;
            markDirty();
        }
    }

    public void removeEntry(UUID playerId) {
        PlayerDimEntry entry = entries.remove(playerId);
        if (entry != null && entry.dimensionId != Integer.MIN_VALUE) {
            dimToPlayer.remove(entry.dimensionId);
        }
        markDirty();
    }

    public static PersonalDimensionData get(World world) {
        if (world.isRemote) {
            throw new IllegalStateException("PersonalDimensionData cannot be loaded on the client");
        }
        World overworld = world.getMinecraftServer() != null ? world.getMinecraftServer().getWorld(0) : world;
        if (overworld == null) {
            overworld = world;
        }
        MapStorage storage = overworld.getMapStorage();
        PersonalDimensionData data = (PersonalDimensionData) storage.getOrLoadData(PersonalDimensionData.class, DATA_NAME);
        if (data == null) {
            PersonalDimensionData legacy = (PersonalDimensionData) storage.getOrLoadData(
                    PersonalDimensionData.class, LEGACY_DATA_NAME);
            if (legacy != null) {
                data = new PersonalDimensionData();
                data.copyFrom(legacy);
                storage.setData(DATA_NAME, data);
                data.markDirty();
                AE2Enhanced.LOGGER.info("[AE2E] Migrated personal dimension data from legacy name '{}' to '{}'",
                        LEGACY_DATA_NAME, DATA_NAME);
            } else {
                data = new PersonalDimensionData();
                storage.setData(DATA_NAME, data);
            }
        }
        return data;
    }

    public Collection<PlayerDimEntry> getAllEntries() {
        return entries.values();
    }

    public void updateDimensionMapping(UUID playerId, int dimId) {
        PlayerDimEntry entry = getEntry(playerId);
        if (entry.dimensionId != Integer.MIN_VALUE) {
            dimToPlayer.remove(entry.dimensionId, playerId);
        }
        entry.dimensionId = dimId;
        if (dimId != Integer.MIN_VALUE) {
            dimToPlayer.put(dimId, playerId);
        }
        markDirty();
    }

    public TeamDimEntry getTeamEntry(String teamId, String bindingId) {
        TeamDimEntry existing = teamEntries.get(teamId);
        if (existing != null && !existing.bindingId.equals(bindingId)) {
            throw new IllegalStateException("Team binding mismatch for " + teamId);
        }
        return teamEntries.computeIfAbsent(teamId, id -> new TeamDimEntry(id, bindingId));
    }

    @Nullable
    public TeamDimEntry peekTeamEntry(String teamId) {
        return teamEntries.get(teamId);
    }

    @Nullable
    public TeamDimEntry getTeamEntryByDimensionId(int dimId) {
        String id = dimToTeam.get(dimId);
        return id != null ? teamEntries.get(id) : null;
    }

    public Collection<TeamDimEntry> getAllTeamEntries() {
        return teamEntries.values();
    }

    public Collection<TeamDimEntry> getOrphanedTeamEntries() {
        return orphanedTeamEntries.values();
    }

    public void updateTeamDimensionMapping(String teamId, int dimId) {
        TeamDimEntry entry = teamEntries.get(teamId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown team dimension " + teamId);
        }
        if (entry.dimensionId != Integer.MIN_VALUE) {
            dimToTeam.remove(entry.dimensionId, teamId);
        }
        entry.dimensionId = dimId;
        if (dimId != Integer.MIN_VALUE) {
            dimToTeam.put(dimId, teamId);
        }
        markDirty();
    }

    /** 将团队条目及其世界引用隔离，world 目录不由本方法删除。 */
    public void quarantineTeamEntry(String teamId) {
        TeamDimEntry entry = teamEntries.remove(teamId);
        if (entry == null) {
            return;
        }
        entry.frozen = true;
        if (entry.dimensionId != Integer.MIN_VALUE) {
            dimToTeam.remove(entry.dimensionId, teamId);
        }
        orphanedTeamEntries.put(orphanKey(entry.teamId, entry.bindingId), entry);
        markDirty();
    }

    public DimensionPreference getPreference(UUID playerId) {
        return preferences.getOrDefault(playerId, DimensionPreference.AUTO);
    }

    public void setPreference(UUID playerId, DimensionPreference preference) {
        DimensionPreference actual = preference == null ? DimensionPreference.AUTO : preference;
        if (actual == DimensionPreference.AUTO) {
            preferences.remove(playerId);
        } else {
            preferences.put(playerId, actual);
        }
        markDirty();
    }

    @Nullable
    public DimensionOwner getOwnerByDimensionId(int dimId) {
        PlayerDimEntry personal = getEntryByDimensionId(dimId);
        if (personal != null) {
            return DimensionOwner.personal(personal);
        }
        TeamDimEntry team = getTeamEntryByDimensionId(dimId);
        return team == null ? null : DimensionOwner.team(team);
    }

    public void copyFrom(PersonalDimensionData other) {
        entries.clear();
        entries.putAll(other.entries);
        dimToPlayer.clear();
        dimToPlayer.putAll(other.dimToPlayer);
        teamEntries.clear();
        teamEntries.putAll(other.teamEntries);
        dimToTeam.clear();
        dimToTeam.putAll(other.dimToTeam);
        orphanedTeamEntries.clear();
        orphanedTeamEntries.putAll(other.orphanedTeamEntries);
        preferences.clear();
        preferences.putAll(other.preferences);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        dimToPlayer.clear();
        teamEntries.clear();
        dimToTeam.clear();
        orphanedTeamEntries.clear();
        preferences.clear();
        int version = nbt.hasKey("version", 99) ? nbt.getInteger("version") : 0;
        NBTTagList list = nbt.getTagList("entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            UUID id;
            try {
                id = UUID.fromString(tag.getString("playerUUID"));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            PlayerDimEntry entry = new PlayerDimEntry(id);
            entry.readFromNBT(tag, version);
            entries.put(id, entry);
            if (entry.dimensionId != Integer.MIN_VALUE) {
                dimToPlayer.put(entry.dimensionId, id);
            }
        }
        readTeams(nbt.getTagList("teamEntries", 10), false);
        readTeams(nbt.getTagList("orphanedTeamEntries", 10), true);
        NBTTagList preferenceList = nbt.getTagList("preferences", 10);
        for (int i = 0; i < preferenceList.tagCount(); i++) {
            NBTTagCompound tag = preferenceList.getCompoundTagAt(i);
            try {
                preferences.put(UUID.fromString(tag.getString("playerUUID")),
                        DimensionPreference.parse(tag.getString("preference")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void readTeams(NBTTagList list, boolean orphaned) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            String teamId = tag.getString("teamId");
            String bindingId = tag.getString("bindingId");
            if (teamId.isEmpty() || bindingId.isEmpty()) {
                continue;
            }
            TeamDimEntry entry = new TeamDimEntry(teamId, bindingId);
            entry.readFromNBT(tag);
            if (orphaned) {
                entry.frozen = true;
                orphanedTeamEntries.put(orphanKey(teamId, bindingId), entry);
            } else {
                teamEntries.put(teamId, entry);
                if (entry.dimensionId != Integer.MIN_VALUE) {
                    dimToTeam.put(entry.dimensionId, teamId);
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("version", CURRENT_VERSION);
        NBTTagList list = new NBTTagList();
        java.util.List<PlayerDimEntry> personal = new ArrayList<>(entries.values());
        personal.sort(Comparator.comparing(entry -> entry.playerId));
        for (PlayerDimEntry entry : personal) {
            if (!entry.isEmpty()) {
                list.appendTag(entry.writeToNBT());
            }
        }
        compound.setTag("entries", list);
        compound.setTag("teamEntries", writeTeams(new ArrayList<>(teamEntries.values())));
        compound.setTag("orphanedTeamEntries", writeTeams(new ArrayList<>(orphanedTeamEntries.values())));
        NBTTagList preferenceList = new NBTTagList();
        java.util.List<UUID> players = new ArrayList<>(preferences.keySet());
        players.sort(UUID::compareTo);
        for (UUID playerId : players) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("playerUUID", playerId.toString());
            tag.setString("preference", preferences.get(playerId).name());
            preferenceList.appendTag(tag);
        }
        compound.setTag("preferences", preferenceList);
        return compound;
    }
}
