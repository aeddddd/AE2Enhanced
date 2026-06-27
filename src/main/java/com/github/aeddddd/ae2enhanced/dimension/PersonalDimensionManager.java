package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.dimension.lighting.DimensionLightingFixer;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import com.github.aeddddd.ae2enhanced.dimension.teleport.PersonalTeleporter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
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
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
     */
    public static int getOrCreateDimension(EntityPlayerMP player) {
        if (PERSONAL_DIM_TYPE == null) return Integer.MIN_VALUE;
        World world = player.getServerWorld();
        PersonalDimensionData data = PersonalDimensionData.get(world);
        PlayerDimEntry entry = data.getEntry(player.getUniqueID());
        if (entry.dimensionId == Integer.MIN_VALUE) {
            int dimId = DimensionManager.getNextFreeDimId();
            DimensionManager.registerDimension(dimId, PERSONAL_DIM_TYPE);
            data.updateDimensionMapping(player.getUniqueID(), dimId);
            AE2Enhanced.LOGGER.info("[AE2E] Created personal dimension {} for player {}", dimId, player.getName());
            broadcastDimensionRegistrySync();
        }
        return entry.dimensionId;
    }

    public static int getDimensionId(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return Integer.MIN_VALUE;
        return PersonalDimensionData.get(overworld).getEntry(playerId).dimensionId;
    }

    @Nullable
    public static PlayerDimEntry getEntry(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return null;
        return PersonalDimensionData.get(overworld).getEntry(playerId);
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
            MinecraftServer server = player.getServerWorld().getMinecraftServer();
            WorldServer target = server != null ? server.getWorld(0) : null;
            if (target == null) return;
            BlockPos spawn = target.getSpawnPoint();
            teleportTo(player, 0, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
            PlayerAbilityApplier.resetAbilities(player);
            return;
        }
        teleportTo(player, entry.returnDim, entry.returnX, entry.returnY, entry.returnZ, entry.returnYaw, entry.returnPitch);
        PlayerAbilityApplier.resetAbilities(player);
    }

    public static void teleportToDimension(EntityPlayerMP player, int dimId) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        BlockPos entryPos = entry != null ? entry.entryPoint : new BlockPos(0, AE2EnhancedConfig.personalDimension.entryY, 0);
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        teleportTo(player, dimId, tx, ty, tz, player.rotationYaw, player.rotationPitch);
        DimensionLightingFixer.scheduleRelight(player.getServerWorld().getMinecraftServer(), dimId, new BlockPos(tx, ty, tz));
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
        DimensionManager.initDimension(dimId);
        BlockPos entryPos = entry.entryPoint;
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        teleportTo(player, dimId, tx, ty, tz, player.rotationYaw, player.rotationPitch);
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
        PersonalDimensionData.get(overworld).markDirty();
        return true;
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
     */
    public static boolean deleteDimension(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return false;
        PlayerDimEntry entry = PersonalDimensionData.get(overworld).getEntry(playerId);
        if (entry == null || entry.dimensionId == Integer.MIN_VALUE) {
            return false;
        }
        int dimId = entry.dimensionId;
        if (DimensionManager.isDimensionRegistered(dimId)) {
            DimensionManager.unregisterDimension(dimId);
        }
        PersonalDimensionData.get(overworld).removeEntry(playerId);
        broadcastDimensionRegistrySync();
        return true;
    }

    public static void teleportTo(EntityPlayerMP player, int dimId, double x, double y, double z, float yaw, float pitch) {
        if (player.dimension == dimId) {
            player.setPositionAndUpdate(x, y, z);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 与 PersonalWorlds 一致：确保目标世界已加载，然后走原版/Forge 的
        // EntityPlayerMP.changeDimension，不直接调用 PlayerList.transferPlayerToDimension，
        // 也不做任何 entityId / tracker 的手动清理。
        if (!DimensionManager.isDimensionRegistered(dimId)) return;
        WorldServer targetWorld = server.getWorld(dimId);
        if (targetWorld == null) {
            DimensionManager.initDimension(dimId);
            targetWorld = server.getWorld(dimId);
        }
        if (targetWorld == null) return;

        player.changeDimension(dimId, new PersonalTeleporter(targetWorld, x, y, z, yaw, pitch));
    }

    public static void setRules(UUID playerId, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        data.setRules(playerId, rules);
        // 规则变更后立即同步给玩家，确保客户端 GUI 与维度状态一致
        sendRulesToPlayer(playerId);
    }

    private static void sendRulesToPlayer(UUID playerId) {
        if (AE2Enhanced.network == null) return;
        PlayerDimEntry entry = getEntry(playerId);
        if (entry == null) return;
        MinecraftServer server = getOverworld() != null ? getOverworld().getMinecraftServer() : null;
        if (server == null) return;
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) {
            AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync(entry.rules), player);
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
    public static void onServerStarted(FMLServerStartedEvent event) {
        if (PERSONAL_DIM_TYPE == null) return;
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        for (PlayerDimEntry entry : data.getAllEntries()) {
            if (entry.dimensionId == Integer.MIN_VALUE) continue;
            if (!DimensionManager.isDimensionRegistered(entry.dimensionId)) {
                DimensionManager.registerDimension(entry.dimensionId, PERSONAL_DIM_TYPE);
            }
            // 立即初始化 WorldServer，避免后续首次访问时因懒加载产生异常
            DimensionManager.initDimension(entry.dimensionId);
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
     * 玩家在个人维度死亡并重生后恢复默认能力。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        // 只有重生前位于个人维度时才重置能力，避免误清其他模组的永久飞行/速度加成
        if (isPersonalDimension(event.player.dimension)) {
            PlayerAbilityApplier.resetAbilities(player);
        }
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
            PlayerDimEntry entry = getEntry(player.getUniqueID());
            if (entry != null) {
                PlayerAbilityApplier.applyCapabilities(player, entry.rules);
            }
            sendRulesToPlayer(player.getUniqueID());
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
            PlayerDimEntry entry = getEntry(player.getUniqueID());
            if (entry != null) {
                PlayerAbilityApplier.applyCapabilities(player, entry.rules);
                sendRulesToPlayer(player.getUniqueID());
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
        PlayerDimEntry entry = getEntry(event.player.getUniqueID());
        if (entry != null) {
            PlayerAbilityApplier.tickNoFlightInertia((EntityPlayerMP) event.player, entry.rules);
        }
    }
}
