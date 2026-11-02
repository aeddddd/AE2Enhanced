package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.dimension.lighting.DimensionLightingFixer;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamDimensionAccess;
import com.github.aeddddd.ae2enhanced.dimension.team.TeamInfo;
import com.github.aeddddd.ae2enhanced.dimension.teleport.PersonalTeleporter;
import com.github.aeddddd.ae2enhanced.util.TeleportWhitelist;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * 个人维度管理器：维度类型注册、ID 分配、传送、规则同步与事件分发。
 *
 * <p>具体能力应用与光照修复已拆分到 {@link PlayerAbilityApplier} 与
 * {@link DimensionLightingFixer}，避免本类过度膨胀。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class PersonalDimensionManager {

    private PersonalDimensionManager() {}

    private static DimensionType PERSONAL_DIM_TYPE;
    private static boolean typeRegistered = false;

    /**
     * 在 preInit 阶段注册维度类型。
     */
    public static void registerDimensionType() {
        if (typeRegistered) return;
        // 从 5290 开始找一个未使用的 DimensionType id，避免与常见模组冲突
        int typeId = 5290;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (DimensionType dt : DimensionType.values()) {
            used.add(dt.getId());
        }
        while (used.contains(typeId)) {
            typeId++;
        }
        PERSONAL_DIM_TYPE = DimensionType.register(
                AE2Enhanced.MOD_ID + ":personal_dim",
                "_pdim",
                typeId,
                WorldProviderPersonalDim.class,
                false
        );
        typeRegistered = true;
        com.github.aeddddd.ae2enhanced.dimension.dev.PrivateDimensionHarness.registerFloorStyle();
        AE2Enhanced.LOGGER.info("[AE2E] Registered personal dimension type with id {}", typeId);
    }

    @Nullable
    public static DimensionType getDimensionType() {
        return PERSONAL_DIM_TYPE;
    }

    public static boolean isPersonalDimension(int dimId) {
        if (PERSONAL_DIM_TYPE == null) return false;
        // getProviderType 对未注册维度会抛 IllegalArgumentException，
        // 先检查是否注册可避免世界 tick 阶段因其他 mod/状态异常而崩溃。
        if (!DimensionManager.isDimensionRegistered(dimId)) return false;
        return DimensionManager.getProviderType(dimId) == PERSONAL_DIM_TYPE;
    }

    /**
     * 获取或创建玩家个人维度，返回维度 ID。
     *
     * <p>本方法使用同步锁避免并发调用时重复创建维度。对于已保存的维度 ID，
     * 若发现该 ID 在 DimensionManager 中未注册（如上次崩溃、数据不一致），
     * 会尝试重新注册；若注册失败则重新分配新 ID。</p>
     */
    public static synchronized int getOrCreateDimension(EntityPlayerMP player) {
        if (PERSONAL_DIM_TYPE == null) return Integer.MIN_VALUE;
        World world = player.getServerWorld();
        PersonalDimensionData data = PersonalDimensionData.get(world);
        PlayerDimEntry entry = data.getEntry(player.getUniqueID());

        if (entry.dimensionId != Integer.MIN_VALUE) {
            if (!ensureDimensionRegistered(entry.dimensionId)) {
                data.updateDimensionMapping(player.getUniqueID(), Integer.MIN_VALUE);
            }
        }

        if (entry.dimensionId == Integer.MIN_VALUE) {
            int dimId = createPersonalDimension(player);
            if (dimId != Integer.MIN_VALUE) {
                data.updateDimensionMapping(player.getUniqueID(), dimId);
                AE2Enhanced.LOGGER.info("[AE2E] Created personal dimension {} for player {}", dimId, player.getName());
                broadcastDimensionRegistrySync();
            }
            return dimId;
        }

        return entry.dimensionId;
    }

    /**
     * 尝试将指定的维度 ID 注册为个人维度。若 ID 已被占用或无效，返回 false。
     */
    private static synchronized boolean ensureDimensionRegistered(int dimId) {
        if (PERSONAL_DIM_TYPE == null) return false;
        if (DimensionManager.isDimensionRegistered(dimId)) {
            return DimensionManager.getProviderType(dimId) == PERSONAL_DIM_TYPE;
        }
        try {
            DimensionManager.registerDimension(dimId, PERSONAL_DIM_TYPE);
            AE2Enhanced.LOGGER.info("[AE2E] Re-registered personal dimension {}", dimId);
            return true;
        } catch (IllegalArgumentException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to re-register personal dimension {}: {}", dimId, e.getMessage());
            return false;
        }
    }

    /**
     * 创建一个新的个人维度，返回维度 ID。失败返回 Integer.MIN_VALUE。
     * 使用 DimensionManager.class 作为锁，防止其它并发路径在 getNextFreeDimId
     * 与 registerDimension 之间占用同一 ID。
     */
    private static int createPersonalDimension(EntityPlayerMP player) {
        if (PERSONAL_DIM_TYPE == null) return Integer.MIN_VALUE;
        synchronized (DimensionManager.class) {
            int dimId = DimensionManager.getNextFreeDimId();
            while (DimensionManager.isDimensionRegistered(dimId) || isPrivateDimensionIdReserved(dimId)) {
                dimId++;
            }
            try {
                DimensionManager.registerDimension(dimId, PERSONAL_DIM_TYPE);
                return dimId;
            } catch (IllegalArgumentException e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to register personal dimension", e);
                return Integer.MIN_VALUE;
            }
        }
    }

    private static boolean isPrivateDimensionIdReserved(int dimId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) {
            return false;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        if (data.getEntryByDimensionId(dimId) != null || data.getTeamEntryByDimensionId(dimId) != null) {
            return true;
        }
        for (TeamDimEntry orphaned : data.getOrphanedTeamEntries()) {
            if (orphaned.dimensionId == dimId) {
                return true;
            }
        }
        return false;
    }

    /**
     * 只读查询指定玩家的条目，不存在时返回 null（不会创建空条目）。
     */
    @Nullable
    public static PlayerDimEntry getEntry(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return null;
        return PersonalDimensionData.get(overworld).peekEntry(playerId);
    }

    @Nullable
    public static PlayerDimEntry getEntryByDimension(int dimId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return null;
        return PersonalDimensionData.get(overworld).getEntryByDimensionId(dimId);
    }

    @Nullable
    public static DimensionOwner getOwnerByDimension(int dimId) {
        WorldServer overworld = getOverworld();
        return overworld == null ? null : PersonalDimensionData.get(overworld).getOwnerByDimensionId(dimId);
    }

    public static PersonalDimensionRules getDimensionRules(int dimId) {
        DimensionOwner owner = getOwnerByDimension(dimId);
        return owner != null ? owner.rules : new PersonalDimensionRules();
    }

    public static FloorPreset getFloorPreset(int dimId) {
        DimensionOwner owner = getOwnerByDimension(dimId);
        return FloorStyleRegistry.resolve(owner != null ? owner.floor : new FloorDefinition());
    }

    public static boolean enterDimension(EntityPlayerMP player, DimensionPreference preference) {
        DimensionPreference requested = preference == null ? DimensionPreference.AUTO : preference;
        Optional<TeamInfo> team = TeamDimensionAccess.currentTeam(player.getUniqueID());
        if (requested == DimensionPreference.TEAM && !team.isPresent()) {
            player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.no_team"));
            return false;
        }
        if (requested == DimensionPreference.PERSONAL || requested == DimensionPreference.AUTO && !team.isPresent()) {
            int dimId = getOrCreateDimension(player);
            return dimId != Integer.MIN_VALUE && teleportToDimension(player, dimId);
        }
        return enterTeamDimension(player, team.get().getTeamId());
    }

    public static boolean enterTeamDimension(EntityPlayerMP player, String teamId) {
        Optional<TeamInfo> current = TeamDimensionAccess.currentTeam(player.getUniqueID());
        if (!current.isPresent() || !current.get().getTeamId().equals(teamId)
                || !TeamDimensionAccess.isMember(teamId, player.getUniqueID())) {
            return false;
        }
        WorldServer overworld = getOverworld();
        if (overworld == null) {
            return false;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        TeamDimEntry entry = data.peekTeamEntry(teamId);
        if (entry != null && !TeamDimensionAccess.hasValidBinding(teamId, entry.bindingId)) {
            quarantineTeamWorld(data, teamId);
            entry = null;
        }
        if (entry == null) {
            String bindingId = UUID.randomUUID().toString();
            TeamDimensionAccess.bind(teamId, bindingId);
            entry = data.getTeamEntry(teamId, bindingId);
        }
        DimensionOwner owner = DimensionOwner.team(entry);
        if (!PrivateDimensionPermissions.canEnter(player, data, owner)) {
            return false;
        }
        if (entry.dimensionId != Integer.MIN_VALUE && !ensureDimensionRegistered(entry.dimensionId)) {
            return false;
        }
        if (entry.dimensionId == Integer.MIN_VALUE) {
            int dimId = createPersonalDimension(player);
            if (dimId == Integer.MIN_VALUE) {
                return false;
            }
            data.updateTeamDimensionMapping(teamId, dimId);
            broadcastDimensionRegistrySync();
        }
        return teleportToOwnedDimension(player, DimensionOwner.team(entry));
    }

    private static boolean teleportToOwnedDimension(EntityPlayerMP player, DimensionOwner owner) {
        BlockPos entryPos = owner.entryPoint;
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        if (!teleportTo(player, owner.dimensionId, tx, ty, tz, player.rotationYaw, player.rotationPitch)) {
            return false;
        }
        DimensionLightingFixer.scheduleRelight(player.getServerWorld().getMinecraftServer(), owner.dimensionId, entryPos);
        return true;
    }

    public static boolean bindEntryPoint(EntityPlayerMP player, BlockPos pos) {
        WorldServer overworld = getOverworld();
        if (overworld == null || !isPersonalDimension(player.dimension)) {
            return false;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        DimensionOwner owner = data.getOwnerByDimensionId(player.dimension);
        if (!PrivateDimensionPermissions.canModifyRules(player, data, owner)) {
            return false;
        }
        if (owner.isTeam()) {
            TeamDimEntry entry = data.peekTeamEntry(owner.ownerId);
            if (entry != null) {
                entry.entryPoint = pos;
            }
        } else {
            data.setEntryPoint(player.getUniqueID(), pos);
        }
        data.markDirty();
        return true;
    }

    public static void setEntryPoint(EntityPlayer player, BlockPos pos) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData.get(overworld).setEntryPoint(player.getUniqueID(), pos);
    }

    public static void setReturnPoint(EntityPlayerMP player) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData.get(overworld).setReturnPoint(
                player.getUniqueID(),
                player.dimension,
                player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch
        );
    }

    public static void teleportToReturnPoint(EntityPlayerMP player) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        if (entry == null || !entry.hasReturnPoint) {
            // 没有记录则返回主世界出生点
            teleportToOverworldSpawn(player);
            refreshPlayerAbilities(player);
            return;
        }
        // 返回点每次进入都重新走统一权限入口，团队离队与旧白名单移除均不能绕权
        if (isPersonalDimension(entry.returnDim)) {
            WorldServer ow = getOverworld();
            PersonalDimensionData data = ow != null ? PersonalDimensionData.get(ow) : null;
            DimensionOwner owner = data != null ? data.getOwnerByDimensionId(entry.returnDim) : null;
            if (data == null || !PrivateDimensionPermissions.canEnter(player, data, owner)) {
                if (ow != null) {
                    data.clearReturnPoint(player.getUniqueID());
                }
                player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.no_permission_enter"));
                teleportToOverworldSpawn(player);
                refreshPlayerAbilities(player);
                return;
            }
        }
        if (!teleportTo(player, entry.returnDim, entry.returnX, entry.returnY, entry.returnZ, entry.returnYaw, entry.returnPitch)) {
            // 目标维度不可用时回退主世界出生点
            player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.teleport_failed"));
            teleportToOverworldSpawn(player);
        }
        refreshPlayerAbilities(player);
    }

    /**
     * 将玩家传送回主世界出生点。
     */
    private static void teleportToOverworldSpawn(EntityPlayerMP player) {
        MinecraftServer server = player.getServerWorld().getMinecraftServer();
        WorldServer target = server != null ? server.getWorld(0) : null;
        if (target == null) return;
        BlockPos spawn = target.getSpawnPoint();
        teleportTo(player, 0, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
    }

    public static boolean teleportToDimension(EntityPlayerMP player, int dimId) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        BlockPos entryPos = entry != null ? entry.entryPoint : new BlockPos(0, ServerConfig.current().personalDimension.entryY, 0);
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        if (!teleportTo(player, dimId, tx, ty, tz, player.rotationYaw, player.rotationPitch)) return false;
        DimensionLightingFixer.scheduleRelight(player.getServerWorld().getMinecraftServer(), dimId, new BlockPos(tx, ty, tz));
        return true;
    }

    /**
     * 将指定玩家传送到目标所有者的个人维度，并校验其是否在组队白名单中。
     *
     * @param player  要传送的玩家
     * @param ownerId 维度所有者
     * @return 是否成功传送
     */
    public static boolean teleportPlayerToDimension(EntityPlayerMP player, UUID ownerId) {
        if (player.getUniqueID().equals(ownerId)) {
            int dimId = getOrCreateDimension(player);
            return dimId != Integer.MIN_VALUE && teleportToDimension(player, dimId);
        }

        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(ownerId);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return false;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        if (!PrivateDimensionPermissions.canEnter(player, data, DimensionOwner.personal(entry))) {
            return false;
        }
        int dimId = entry.dimensionId;
        // 确保受邀访问的维度仍然有效注册
        if (!ensureDimensionRegistered(dimId)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Owner {} personal dimension {} is not registered, cannot teleport player {}",
                    ownerId, dimId, player.getName());
            return false;
        }
        DimensionManager.initDimension(dimId);
        BlockPos entryPos = entry.entryPoint;
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        if (!teleportTo(player, dimId, tx, ty, tz, player.rotationYaw, player.rotationPitch)) {
            return false;
        }
        DimensionLightingFixer.scheduleRelight(player.getServerWorld().getMinecraftServer(), dimId, new BlockPos(tx, ty, tz));
        return true;
    }

    /**
     * 邀请玩家加入指定所有者个人维度的组队白名单。
     * 受邀玩家在该维度内拥有完整的进入/建造/交互能力，不再细分权限。
     */
    public static boolean invitePlayer(UUID ownerId, UUID targetId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(ownerId);
        if (entry == null) return false;
        entry.allowedPlayers.add(targetId);
        PersonalDimensionData.get(overworld).markDirty();
        return true;
    }

    /**
     * 将玩家从指定所有者个人维度的组队白名单移除。
     */
    public static boolean kickPlayer(UUID ownerId, UUID targetId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(ownerId);
        if (entry == null) return false;
        entry.removePlayer(targetId);
        // 被移出白名单后，清除其指向本维度的返回点，防止"埋点-返回"重进
        if (entry.dimensionId != Integer.MIN_VALUE) {
            clearReturnPointIfInDimension(targetId, entry.dimensionId);
        }
        PersonalDimensionData.get(overworld).markDirty();
        return true;
    }

    /**
     * 若指定玩家的返回点位于指定维度，则清除该返回点。
     *
     * <p>供 pd kick/remove 命令调用：被移出白名单的玩家不应再通过
     * 返回点重新进入其已无权限的个人维度。</p>
     */
    public static void clearReturnPointIfInDimension(UUID playerId, int dimId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData.get(overworld).clearReturnPointIfInDimension(playerId, dimId);
    }

    /**
     * 删除指定玩家的个人维度数据，下次进入时会重新创建。
     * 删除前会将仍位于该维度内的所有玩家传送至安全位置（所有者优先返回其记录点）。
     */
    public static boolean deleteDimension(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(playerId);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return false;
        }
        int dimId = entry.dimensionId;
        FloorRepaintManager.cancel(dimId);
        MinecraftServer server = overworld.getMinecraftServer();
        WorldServer dimWorld = DimensionManager.getWorld(dimId);
        if (dimWorld != null) {
            WorldServer targetWorld = server != null ? server.getWorld(0) : null;
            BlockPos spawn = targetWorld != null ? targetWorld.getSpawnPoint() : new BlockPos(0, 64, 0);
            for (EntityPlayerMP player : new ArrayList<>(dimWorld.getPlayers(EntityPlayerMP.class, p -> true))) {
                boolean teleported = false;
                if (player.getUniqueID().equals(playerId) && entry.hasReturnPoint && entry.returnDim != dimId) {
                    teleported = teleportTo(player, entry.returnDim, entry.returnX, entry.returnY, entry.returnZ, entry.returnYaw, entry.returnPitch);
                }
                if (!teleported) {
                    // 返回点传送失败或无返回点时回退主世界出生点
                    teleported = teleportTo(player, 0, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
                }
                if (!teleported) {
                    player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.teleport_failed"));
                }
            }
        }
        if (DimensionManager.isDimensionRegistered(dimId)) {
            DimensionManager.unregisterDimension(dimId);
        }
        // 维度 ID 会被复用：必须卸载维度世界并删除其存档目录，
        // 否则新主人会看到旧主人的全部建筑
        if (dimWorld != null && server != null) {
            DimensionManager.setWorld(dimId, null, server);
        }
        deleteDimensionSaveFolder(server, dimId);
        PersonalDimensionData.get(overworld).removeEntry(playerId);
        broadcastDimensionRegistrySync();
        return true;
    }

    public static void onTeamChanged() {
        broadcastDimensionRegistrySync();
        broadcastInfo();
    }

    public static void onTeamLoaded(String teamId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        // TeamData 尚未恢复时保持延期，就绪后才允许恢复或按 bindingId 隔离。
        restoreReadyTeamDimensions(data);
        broadcastDimensionRegistrySync();
        broadcastInfo();
    }

    public static void onTeamLeft(String teamId, UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) {
            return;
        }
        TeamDimEntry entry = PersonalDimensionData.get(overworld).peekTeamEntry(teamId);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return;
        }
        EntityPlayerMP player = overworld.getMinecraftServer().getPlayerList().getPlayerByUUID(playerId);
        if (player != null && player.dimension == entry.dimensionId) {
            clearReturnPointIfInDimension(playerId, entry.dimensionId);
            teleportToOverworldSpawn(player);
            refreshPlayerAbilities(player);
        }
    }

    public static void onTeamDeleted(String teamId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        TeamDimEntry entry = data.peekTeamEntry(teamId);
        if (entry == null) {
            return;
        }
        if (entry.dimensionId != Integer.MIN_VALUE) {
            WorldServer world = DimensionManager.getWorld(entry.dimensionId);
            if (world != null) {
                for (EntityPlayerMP player : new ArrayList<>(world.getPlayers(EntityPlayerMP.class, p -> true))) {
                    clearReturnPointIfInDimension(player.getUniqueID(), entry.dimensionId);
                    teleportToOverworldSpawn(player);
                    refreshPlayerAbilities(player);
                }
            }
        }
        quarantineTeamWorld(data, teamId);
        broadcastDimensionRegistrySync();
    }

    private static void quarantineTeamWorld(PersonalDimensionData data, String teamId) {
        TeamDimEntry entry = data.peekTeamEntry(teamId);
        if (entry == null) {
            return;
        }
        int dimId = entry.dimensionId;
        FloorRepaintManager.cancel(dimId);
        MinecraftServer server = getOverworld() != null ? getOverworld().getMinecraftServer() : null;
        if (dimId != Integer.MIN_VALUE && DimensionManager.isDimensionRegistered(dimId)) {
            DimensionManager.unregisterDimension(dimId);
        }
        if (dimId != Integer.MIN_VALUE && server != null) {
            DimensionManager.setWorld(dimId, null, server);
        }
        data.quarantineTeamEntry(teamId);
        AE2Enhanced.LOGGER.info("[AE2E] Quarantined team dimension {} for team {}; world files retained", dimId, teamId);
    }

    /**
     * 删除个人维度的存档目录（存档根下的 AE2E_PersonalDim_&lt;id&gt;）。
     * 删除失败仅记录错误日志，不影响维度注销结果。
     */
    private static void deleteDimensionSaveFolder(@Nullable MinecraftServer server, int dimId) {
        if (server == null) return;
        WorldServer overworld = server.getWorld(0);
        if (overworld == null) return;
        File saveRoot = overworld.getSaveHandler().getWorldDirectory();
        if (saveRoot == null) return;
        File dimFolder = new File(saveRoot, "AE2E_PersonalDim_" + dimId);
        if (!dimFolder.exists()) return;
        if (!deleteRecursively(dimFolder)) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to fully delete personal dimension save folder: {}", dimFolder.getAbsolutePath());
        }
    }

    /**
     * 递归删除文件/目录，全部成功返回 true。
     */
    private static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (!deleteRecursively(child)) {
                    return false;
                }
            }
        }
        return file.delete();
    }

    /**
     * 将玩家传送到指定维度的指定坐标。
     *
     * @return 传送是否执行成功；目标维度未注册或世界无法加载时返回 false，玩家保持原位
     */
    public static boolean teleportTo(EntityPlayerMP player, int dimId, double x, double y, double z, float yaw, float pitch) {
        if (player.dimension == dimId) {
            try (TeleportWhitelist.Scope ignored = TeleportWhitelist.open(player.getUniqueID())) {
                player.setPositionAndUpdate(x, y, z);
            }
            return player.getDistanceSq(x, y, z) <= 1.0e-6;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        // 与 PersonalWorlds 一致：确保目标世界已加载，然后走原版/Forge 的
        // EntityPlayerMP.changeDimension，不直接调用 PlayerList.transferPlayerToDimension，
        // 也不做任何 entityId / tracker 的手动清理。
        if (!DimensionManager.isDimensionRegistered(dimId)) return false;
        WorldServer targetWorld = server.getWorld(dimId);
        if (targetWorld == null) {
            DimensionManager.initDimension(dimId);
            targetWorld = server.getWorld(dimId);
        }
        if (targetWorld == null) return false;

        try (TeleportWhitelist.Scope ignored = TeleportWhitelist.open(player.getUniqueID())) {
            player.changeDimension(dimId, new PersonalTeleporter(targetWorld, x, y, z, yaw, pitch));
        }
        return player.dimension == dimId && player.getDistanceSq(x, y, z) <= 1.0e-6;
    }

    public static void refreshPlayerAbilities(EntityPlayerMP player) {
        DimensionOwner owner = isPersonalDimension(player.dimension) ? getOwnerByDimension(player.dimension) : null;
        if (owner != null) {
            PlayerAbilityApplier.applyCapabilities(player, owner.rules);
        } else {
            PlayerAbilityApplier.resetAbilities(player);
        }
    }

    public static void setRules(UUID playerId, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        data.setRules(playerId, rules);
        PlayerDimEntry entry = data.peekEntry(playerId);
        if (entry != null) {
            applyRulesToWorld(DimensionOwner.personal(entry));
        }
    }

    public static void submitRules(EntityPlayerMP player, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null || !isPersonalDimension(player.dimension)) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        DimensionOwner owner = data.getOwnerByDimensionId(player.dimension);
        if (!PrivateDimensionPermissions.canModifyRules(player, data, owner)) {
            player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.no_permission_enter"));
            return;
        }
        if (owner.isTeam()) {
            TeamDimEntry entry = data.peekTeamEntry(owner.ownerId);
            if (entry != null) {
                entry.rules = rules.copy();
                data.markDirty();
                applyRulesToWorld(DimensionOwner.team(entry));
            }
        } else {
            setRules(player.getUniqueID(), rules);
        }
    }

    private static void applyRulesToWorld(DimensionOwner owner) {
        WorldServer overworld = getOverworld();
        MinecraftServer server = overworld != null ? overworld.getMinecraftServer() : null;
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player.dimension == owner.dimensionId) {
                sendRulesToPlayer(owner.rules, player);
                sendInfoToPlayer(player);
                PlayerAbilityApplier.applyCapabilities(player, owner.rules);
            }
        }
    }

    /**
     * 将指定所有者的规则同步给指定玩家（用于进入他人维度时按所有者规则渲染/应用）。
     */
    public static void sendRulesToPlayer(UUID rulesOwnerId, EntityPlayerMP target) {
        if (AE2Enhanced.network == null) return;
        PlayerDimEntry entry = getEntry(rulesOwnerId);
        if (entry == null) return;
        AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync(entry.rules), target);
    }

    public static void sendInfoToPlayer(EntityPlayerMP player) {
        if (AE2Enhanced.network == null) {
            return;
        }
        WorldServer overworld = getOverworld();
        if (overworld == null) {
            return;
        }
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        DimensionOwner owner = data.getOwnerByDimensionId(player.dimension);
        DimensionPreference preference = data.getPreference(player.getUniqueID());
        PrivateDimensionInfo info;
        FloorDefinition floor;
        if (owner == null) {
            PlayerDimEntry entry = data.peekEntry(player.getUniqueID());
            floor = entry != null ? entry.floor : new FloorDefinition();
            info = new PrivateDimensionInfo(DimensionOwner.Scope.PERSONAL, player.getUniqueID().toString(),
                    player.getName(), preference, floor.getFloorStyleId(), floor.getRevision(), true);
        } else {
            floor = owner.floor;
            String display = owner.ownerId;
            if (owner.isTeam()) {
                Optional<TeamInfo> team = TeamDimensionAccess.currentTeam(player.getUniqueID());
                if (team.isPresent() && team.get().getTeamId().equals(owner.ownerId)) {
                    display = team.get().getDisplayName();
                }
            }
            info = new PrivateDimensionInfo(owner.scope, owner.ownerId, display, preference,
                    floor.getFloorStyleId(), floor.getRevision(),
                    PrivateDimensionPermissions.canModifyRules(player, data, owner));
        }
        AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPrivateDimensionInfo(info), player);
        AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionFloorSync(
                floor, FloorRepaintManager.getRepaintPending(owner)), player);
    }

    public static void sendInfoToPlayersInDimension(int dimensionId) {
        WorldServer overworld = getOverworld();
        MinecraftServer server = overworld != null ? overworld.getMinecraftServer() : null;
        if (server != null) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                if (player.dimension == dimensionId) {
                    sendInfoToPlayer(player);
                }
            }
        }
    }

    private static void broadcastInfo() {
        WorldServer overworld = getOverworld();
        MinecraftServer server = overworld != null ? overworld.getMinecraftServer() : null;
        if (server != null) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                sendInfoToPlayer(player);
            }
        }
    }

    private static void sendRulesToPlayer(PersonalDimensionRules rules, EntityPlayerMP target) {
        if (AE2Enhanced.network != null && rules != null) {
            AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync(rules), target);
        }
    }

    private static void sendRulesToPlayer(UUID playerId) {
        MinecraftServer server = getOverworld() != null ? getOverworld().getMinecraftServer() : null;
        if (server == null) return;
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) {
            sendRulesToPlayer(playerId, player);
        }
    }

    /**
     * 向指定玩家同步当前所有个人维度 ID，确保客户端在进入维度前已注册。
     */
    public static void sendRegistrySync(EntityPlayerMP player) {
        if (AE2Enhanced.network == null || PERSONAL_DIM_TYPE == null) return;
        List<Integer> ids = collectPersonalDimensionIds();
        AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRegistrySync(ids), player);
    }

    /**
     * 向所有在线玩家广播个人维度注册表变化。
     */
    private static void broadcastDimensionRegistrySync() {
        if (AE2Enhanced.network == null) return;
        MinecraftServer server = getOverworld() != null ? getOverworld().getMinecraftServer() : null;
        if (server == null) return;
        List<Integer> ids = collectPersonalDimensionIds();
        AE2Enhanced.network.sendToAll(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRegistrySync(ids));
    }

    private static List<Integer> collectPersonalDimensionIds() {
        List<Integer> ids = new ArrayList<>();
        WorldServer overworld = getOverworld();
        if (overworld == null) return ids;
        for (PlayerDimEntry entry : PersonalDimensionData.get(overworld).getAllEntries()) {
            if (entry.dimensionId != Integer.MIN_VALUE) {
                ids.add(entry.dimensionId);
            }
        }
        for (TeamDimEntry entry : PersonalDimensionData.get(overworld).getAllTeamEntries()) {
            if (entry.dimensionId != Integer.MIN_VALUE) {
                ids.add(entry.dimensionId);
            }
        }
        Collections.sort(ids);
        return ids;
    }

    @Nullable
    private static WorldServer getOverworld() {
        return net.minecraftforge.common.DimensionManager.getWorld(0);
    }

    /**
     * 服务端启动时重新注册已保存的个人维度。
     * 注意：FML 生命周期事件不在 MinecraftForge.EVENT_BUS 上，需要由 @Mod 主类调用。
     */
    public static synchronized void onServerStarted(FMLServerStartedEvent event) {
        if (PERSONAL_DIM_TYPE == null) return;
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        for (PlayerDimEntry entry : data.getAllEntries()) {
            if (entry.dimensionId == Integer.MIN_VALUE) continue;
            if (ensureDimensionRegistered(entry.dimensionId)) {
                // 立即初始化 WorldServer，避免后续首次访问时因懒加载产生异常
                DimensionManager.initDimension(entry.dimensionId);
            } else {
                AE2Enhanced.LOGGER.error("[AE2E] Saved personal dimension ID {} for player {} could not be re-registered. " +
                        "It may have been taken by another mod. Player will get a new dimension on next entry.",
                        entry.dimensionId, entry.playerId);
                data.updateDimensionMapping(entry.playerId, Integer.MIN_VALUE);
            }
        }
        restoreReadyTeamDimensions(data);
        // 若某些旧维度 ID 因被占用而重置，需要同步最新的注册表给已登录玩家。
        broadcastDimensionRegistrySync();
    }

    private static void restoreReadyTeamDimensions(PersonalDimensionData data) {
        if (!TeamDimensionAccess.isAvailable()) {
            return;
        }
        for (TeamDimEntry entry : new ArrayList<>(data.getAllTeamEntries())) {
            if (entry.dimensionId == Integer.MIN_VALUE || !TeamDimensionAccess.isBindingReady(entry.teamId)) {
                continue;
            }
            if (!TeamDimensionAccess.hasValidBinding(entry.teamId, entry.bindingId)) {
                quarantineTeamWorld(data, entry.teamId);
                continue;
            }
            if (DimensionManager.getWorld(entry.dimensionId) != null) {
                continue;
            }
            if (ensureDimensionRegistered(entry.dimensionId)) {
                DimensionManager.initDimension(entry.dimensionId);
            } else {
                AE2Enhanced.LOGGER.error("[AE2E] Saved team dimension {} for {} could not be re-registered; entry frozen",
                        entry.dimensionId, entry.teamId);
                quarantineTeamWorld(data, entry.teamId);
            }
        }
    }

    /**
     * 阻止个人维度内的生物自然生成。
     */
    @SubscribeEvent
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getWorld().isRemote) return;
        int dim = event.getWorld().provider.getDimension();
        if (!isPersonalDimension(dim)) return;
        DimensionOwner owner = getOwnerByDimension(dim);
        if (owner != null && owner.rules.disableMobSpawning) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    /**
     * 个人维度是玩家的私人建造空间：维度内爆炸不破坏任何方块（实体伤害不受影响）。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) return;
        if (!isPersonalDimension(event.getWorld().provider.getDimension())) return;
        event.getAffectedBlocks().clear();
    }

    /**
     * 每 tick 应用天气/时间规则。
     */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        int dim = event.world.provider.getDimension();
        if (dim == 0) {
            restoreReadyTeamDimensions(PersonalDimensionData.get(event.world));
        }
        if (!isPersonalDimension(dim)) return;
        DimensionOwner owner = getOwnerByDimension(dim);
        if (owner == null) return;

        World world = event.world;
        PersonalDimensionRules rules = owner.rules;
        if (rules.lockWeather) {
            if (world.isRaining()) world.getWorldInfo().setRaining(false);
            if (world.isThundering()) world.getWorldInfo().setThundering(false);
        }
        if (rules.lockTime || !rules.daylightCycle) {
            world.setWorldTime(rules.timeValue);
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        // 与 PersonalWorlds 一致：不强制 saveAllChunks，依赖 Minecraft 正常的 chunk 保存机制。
    }

    /**
     * 玩家死亡时所在的维度。PlayerRespawnEvent 触发时玩家已处于重生维度，
     * 1.12.2 的 recreatePlayerEntity 也不触发 PlayerChangedDimensionEvent，
     * 因此在 LivingDeathEvent 中先行记录死亡维度作为重生判定的依据。
     */
    private static final Map<UUID, Integer> DEATH_DIMENSIONS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        DEATH_DIMENSIONS.put(event.getEntityLiving().getUniqueID(), event.getEntityLiving().dimension);
    }

    /**
     * 玩家在个人维度死亡并重生后恢复能力。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // 按死亡时所在维度判定（重生事件触发时玩家已在主世界，
        // 直接用 event.player.dimension 恒为主世界），避免带着维度内飞行能力重生
        Integer deathDim = DEATH_DIMENSIONS.remove(player.getUniqueID());
        if (deathDim != null && isPersonalDimension(deathDim)) {
            refreshPlayerAbilities(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 清理运行时状态，防止离线玩家 UUID 在 map 中累积泄漏
        DEATH_DIMENSIONS.remove(event.player.getUniqueID());
        PlayerAbilityApplier.discardSnapshot(event.player.getUniqueID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // 同步个人维度注册表到客户端，防止进入维度时客户端未注册而崩溃
        sendRegistrySync(player);
        sendInfoToPlayer(player);
        if (isPersonalDimension(player.dimension)) {
            BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
            DimensionLightingFixer.relightDimensionChunks(player.dimension, pos);
            // 按当前个人或团队所有者应用规则，不读取访客自己的条目
            DimensionOwner owner = getOwnerByDimension(player.dimension);
            if (owner != null) {
                PlayerAbilityApplier.applyCapabilities(player, owner.rules);
                sendRulesToPlayer(owner.rules, player);
            }
        }
    }

    /**
     * 客户端断开连接后清理客户端缓存。
     *
     * <p>注意：不能在此处调用 {@link DimensionManager#unregisterDimension(int)}。
     * 在单人游戏中客户端与服务端共享 JVM 和 DimensionManager 注册表，
     * 退出游戏时服务端可能仍在 tick；若此时注销个人维度 ID，
     * {@link #onWorldTick} 中调用 {@link #isPersonalDimension(int)} 会读到未注册的 ID
     * 并触发 {@code Could not get provider type for dimension X, does not exist} 崩溃。</p>
     */
    @SubscribeEvent
    public static void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        com.github.aeddddd.ae2enhanced.client.ClientPersonalDimensionRules.update(null);
    }

    /**
     * 玩家切换维度时应用/重置个人维度能力，并同步规则。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        refreshPlayerAbilities(player);
        sendInfoToPlayer(player);
        if (isPersonalDimension(event.toDim)) {
            DimensionOwner owner = getOwnerByDimension(event.toDim);
            if (owner != null) sendRulesToPlayer(owner.rules, player);
            BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
            DimensionLightingFixer.relightDimensionChunks(event.toDim, pos);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        refreshPlayerAbilities(player);
        if (isPersonalDimension(player.dimension)) {
            PersonalDimensionData data = PersonalDimensionData.get(player.world);
            DimensionOwner owner = data.getOwnerByDimensionId(player.dimension);
            if (!PrivateDimensionPermissions.canEnter(player, data, owner)) {
                clearReturnPointIfInDimension(player.getUniqueID(), player.dimension);
                teleportToOverworldSpawn(player);
                refreshPlayerAbilities(player);
                return;
            }
            if (owner != null) PlayerAbilityApplier.tickNoFlightInertia(player, owner.rules);
        }
    }
}
