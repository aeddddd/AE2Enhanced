package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.dimension.lighting.DimensionLightingFixer;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import com.github.aeddddd.ae2enhanced.dimension.teleport.PersonalTeleporter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
                // 重新分配新 ID
                entry.dimensionId = Integer.MIN_VALUE;
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
            // 防御性检查：分配到的 ID 已被注册时重试一次
            if (DimensionManager.isDimensionRegistered(dimId)) {
                dimId = DimensionManager.getNextFreeDimId();
                if (DimensionManager.isDimensionRegistered(dimId)) {
                    AE2Enhanced.LOGGER.error("[AE2E] No free dimension ID available for personal dimension");
                    return Integer.MIN_VALUE;
                }
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

    public static int getDimensionId(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return Integer.MIN_VALUE;
        // 只读查询不创建空条目
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).peekEntry(playerId);
        return entry != null ? entry.dimensionId : Integer.MIN_VALUE;
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
            PlayerAbilityApplier.resetAbilities(player);
            return;
        }
        // 权限校验：返回点若位于他人的个人维度，玩家必须仍在白名单且拥有 ENTER 权限，
        // 否则被 kick 的玩家可通过"埋点-返回"反复重进他人维度
        if (isPersonalDimension(entry.returnDim)) {
            PlayerDimEntry ownerEntry = getEntryByDimension(entry.returnDim);
            boolean allowed = ownerEntry != null
                    && (ownerEntry.playerId.equals(player.getUniqueID())
                        || (ownerEntry.allowedPlayers.contains(player.getUniqueID())
                            && ownerEntry.hasPermission(player.getUniqueID(), PersonalDimPermission.ENTER)));
            if (!allowed) {
                WorldServer ow = getOverworld();
                if (ow != null) {
                    PersonalDimensionData.get(ow).clearReturnPoint(player.getUniqueID());
                }
                player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.no_permission_enter"));
                teleportToOverworldSpawn(player);
                PlayerAbilityApplier.resetAbilities(player);
                return;
            }
        }
        if (!teleportTo(player, entry.returnDim, entry.returnX, entry.returnY, entry.returnZ, entry.returnYaw, entry.returnPitch)) {
            // 目标维度不可用时回退主世界出生点
            player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.teleport_failed"));
            teleportToOverworldSpawn(player);
        }
        PlayerAbilityApplier.resetAbilities(player);
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

    public static void teleportToDimension(EntityPlayerMP player, int dimId) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        BlockPos entryPos = entry != null ? entry.entryPoint : new BlockPos(0, AE2EnhancedConfig.personalDimension.entryY, 0);
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        if (teleportTo(player, dimId, tx, ty, tz, player.rotationYaw, player.rotationPitch)) {
            DimensionLightingFixer.scheduleRelight(player.getServerWorld().getMinecraftServer(), dimId, new BlockPos(tx, ty, tz));
        }
    }

    /**
     * 将指定玩家传送到目标所有者的个人维度，并校验访问权限。
     *
     * @param player  要传送的玩家
     * @param ownerId 维度所有者
     * @return 是否成功传送
     */
    public static boolean teleportPlayerToDimension(EntityPlayerMP player, UUID ownerId) {
        if (player.getUniqueID().equals(ownerId)) {
            int dimId = getOrCreateDimension(player);
            if (dimId != Integer.MIN_VALUE) {
                teleportToDimension(player, dimId);
                return true;
            }
            return false;
        }

        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(ownerId);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return false;
        }
        if (!entry.allowedPlayers.contains(player.getUniqueID())
                || !entry.hasPermission(player.getUniqueID(), PersonalDimPermission.ENTER)) {
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
     * 邀请玩家进入指定所有者的个人维度。
     */
    public static boolean invitePlayer(UUID ownerId, UUID targetId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(ownerId);
        if (entry == null) return false;
        entry.allowedPlayers.add(targetId);
        Set<PersonalDimPermission> perms = entry.permissions.computeIfAbsent(targetId, k -> EnumSet.noneOf(PersonalDimPermission.class));
        perms.add(PersonalDimPermission.ENTER);
        perms.add(PersonalDimPermission.BUILD);
        perms.add(PersonalDimPermission.INTERACT);
        PersonalDimensionData.get(overworld).markDirty();
        return true;
    }

    /**
     * 将玩家从指定所有者的个人维度白名单移除。
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
     * 设置某玩家对指定所有者维度的某项权限。
     */
    public static boolean setPermission(UUID ownerId, UUID targetId, PersonalDimPermission permission, boolean value) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(ownerId);
        if (entry == null) return false;
        if (value) {
            entry.grantPermission(targetId, permission);
        } else {
            entry.revokePermission(targetId, permission);
        }
        PersonalDimensionData.get(overworld).markDirty();
        return true;
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
            player.setPositionAndUpdate(x, y, z);
            return true;
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

        player.changeDimension(dimId, new PersonalTeleporter(targetWorld, x, y, z, yaw, pitch));
        return true;
    }

    public static void setRules(UUID playerId, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        data.setRules(playerId, rules);
        // 规则变更后立即同步给玩家，确保客户端 GUI 与维度状态一致
        sendRulesToPlayer(playerId);
    }

    /**
     * 计算玩家当前可编辑规则的维度所有者。
     *
     * <p>玩家位于他人个人维度内，且拥有 {@link PersonalDimPermission#MANAGE_RULES}
     * 权限（或为 OP）时，编辑所在维度所有者的规则；其余情况编辑自己的规则。</p>
     */
    public static UUID getRuleEditTarget(EntityPlayer player) {
        UUID self = player.getUniqueID();
        int dimId = player.dimension;
        if (!isPersonalDimension(dimId)) return self;
        PlayerDimEntry entry = getEntryByDimension(dimId);
        if (entry == null || entry.playerId.equals(self)) return self;
        if (player.canUseCommand(2, "") || entry.hasPermission(self, PersonalDimPermission.MANAGE_RULES)) {
            return entry.playerId;
        }
        return self;
    }

    /**
     * 检查玩家是否有权管理指定所有者维度的规则（所有者本人与 OP 恒为 true）。
     */
    public static boolean canManageRules(EntityPlayer player, UUID ownerId) {
        if (player.getUniqueID().equals(ownerId)) return true;
        if (player.canUseCommand(2, "")) return true;
        PlayerDimEntry entry = getEntry(ownerId);
        return entry != null && entry.hasPermission(player.getUniqueID(), PersonalDimPermission.MANAGE_RULES);
    }

    /**
     * 将指定所有者的规则同步给指定玩家（用于委托编辑场景）。
     */
    public static void sendRulesToPlayer(UUID rulesOwnerId, EntityPlayerMP target) {
        if (AE2Enhanced.network == null) return;
        PlayerDimEntry entry = getEntry(rulesOwnerId);
        if (entry == null) return;
        AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync(entry.rules), target);
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
                entry.dimensionId = Integer.MIN_VALUE;
                data.markDirty();
            }
        }
        // 若某些旧维度 ID 因被占用而重置，需要同步最新的注册表给已登录玩家。
        broadcastDimensionRegistrySync();
    }

    /**
     * 阻止个人维度内的生物自然生成。
     */
    @SubscribeEvent
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getWorld().isRemote) return;
        int dim = event.getWorld().provider.getDimension();
        if (!isPersonalDimension(dim)) return;
        PlayerDimEntry entry = getEntryByDimension(dim);
        if (entry != null && entry.rules.disableMobSpawning) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    /**
     * 每 tick 应用天气/时间规则。
     */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        int dim = event.world.provider.getDimension();
        if (!isPersonalDimension(dim)) return;
        PlayerDimEntry entry = getEntryByDimension(dim);
        if (entry == null) return;

        World world = event.world;
        PersonalDimensionRules rules = entry.rules;
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
            PlayerAbilityApplier.resetAbilities(player);
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
        if (isPersonalDimension(player.dimension)) {
            BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
            DimensionLightingFixer.relightDimensionChunks(player.dimension, pos);
            // 按所在维度所有者的条目应用规则，而非玩家自己的条目，
            // 否则进入他人个人维度时会错误应用访客自己的规则
            PlayerDimEntry entry = getEntryByDimension(player.dimension);
            if (entry != null) {
                PlayerAbilityApplier.applyCapabilities(player, entry.rules);
                sendRulesToPlayer(entry.playerId, player);
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
        if (isPersonalDimension(event.toDim)) {
            // 按目标维度所有者的条目应用规则
            PlayerDimEntry entry = getEntryByDimension(event.toDim);
            if (entry != null) {
                PlayerAbilityApplier.applyCapabilities(player, entry.rules);
                sendRulesToPlayer(entry.playerId, player);
            }
            // 通过指令或其他 mod 进入个人维度时，校正光照
            BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
            DimensionLightingFixer.relightDimensionChunks(event.toDim, pos);
        } else if (isPersonalDimension(event.fromDim)) {
            PlayerAbilityApplier.resetAbilities(player);
            // 与 PersonalWorlds 一致：不强制 saveAllChunks，依赖 Minecraft 正常的 chunk 保存机制。
        }
    }

    /**
     * 玩家 tick 中持续应用个人维度能力，防止进出维度时状态不同步。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (!isPersonalDimension(event.player.dimension)) return;
        // 按所在维度所有者的条目应用规则
        PlayerDimEntry entry = getEntryByDimension(event.player.dimension);
        if (entry != null) {
            PlayerAbilityApplier.tickNoFlightInertia((EntityPlayerMP) event.player, entry.rules);
        }
    }
}
