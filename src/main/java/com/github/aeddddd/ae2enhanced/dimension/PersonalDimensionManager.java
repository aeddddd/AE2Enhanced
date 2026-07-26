package com.github.aeddddd.ae2enhanced.dimension;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.api.dimension.IFloorPreset;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier;
import com.github.aeddddd.ae2enhanced.mixin.accessor.MinecraftServerAccessor;

/**
 * 个人维度管理器：运行时动态维度创建、传送、规则执行与权限强制.
 *
 * <p>1.20.1 的维度系统基于 {@code ResourceKey<Level>} 与 datapack 注册表,
 * 1.12 的 {@code DimensionManager.registerDimension(int)} 已不复存在.
 * 本管理器通过 accessor 直接向 {@code MinecraftServer.levels} 插入自建的
 * {@link ServerLevel},维度类型由静态 datapack JSON
 * ({@code data/ae2enhanced/dimension_type/personal_dim.json}) 提供,
 * 客户端在登录时即已同步该类型,因此传送时无需额外的客户端注册表同步.</p>
 *
 * <p>与 1.12 的差异：BUILD/INTERACT 权限在 1.12 只有存储没有强制执行点,
 * 本移植版本通过方块破坏/放置/交互事件真正强制执行.</p>
 */
public final class PersonalDimensionManager {

    private PersonalDimensionManager() {
    }

    /**
     * 个人维度存档路径前缀,形如 {@code ae2enhanced:pd_<uuid32>}.
     */
    private static final String DIM_PATH_PREFIX = "pd_";

    private static final ResourceKey<DimensionType> PERSONAL_DIM_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE, new ResourceLocation(AE2Enhanced.MOD_ID, "personal_dim"));

    private static final ChunkProgressListener NOOP_PROGRESS_LISTENER = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos pos) {
        }

        @Override
        public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    // ==================== 维度键 ====================

    /**
     * 从玩家 UUID 确定性推导其个人维度键.
     */
    public static ResourceKey<Level> dimensionKeyFor(UUID playerId) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(AE2Enhanced.MOD_ID,
                DIM_PATH_PREFIX + playerId.toString().replace("-", "")));
    }

    public static boolean isPersonalDimension(ResourceKey<Level> key) {
        ResourceLocation location = key.location();
        return AE2Enhanced.MOD_ID.equals(location.getNamespace())
                && location.getPath().startsWith(DIM_PATH_PREFIX);
    }

    public static boolean isPersonalDimension(Level level) {
        return isPersonalDimension(level.dimension());
    }

    /**
     * 从个人维度键反推所有者 UUID.
     */
    @Nullable
    public static UUID ownerFromKey(ResourceKey<Level> key) {
        if (!isPersonalDimension(key)) {
            return null;
        }
        String hex = key.location().getPath().substring(DIM_PATH_PREFIX.length());
        try {
            return UUID.fromString(hex.replaceFirst(
                    "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                    "$1-$2-$3-$4-$5"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== 数据访问 ====================

    @Nullable
    public static PlayerDimEntry getEntry(MinecraftServer server, UUID playerId) {
        return PersonalDimensionData.get(server).getEntry(playerId);
    }

    @Nullable
    public static PlayerDimEntry getEntryByDimension(MinecraftServer server, ResourceKey<Level> key) {
        UUID owner = ownerFromKey(key);
        return owner != null ? getEntry(server, owner) : null;
    }

    public static void setEntryPoint(MinecraftServer server, UUID playerId, BlockPos pos) {
        PlayerDimEntry entry = getEntry(server, playerId);
        if (entry != null) {
            entry.entryPoint = pos;
            PersonalDimensionData.get(server).setDirty();
        }
    }

    public static void setReturnPoint(ServerPlayer player) {
        PlayerDimEntry entry = getEntry(player.server, player.getUUID());
        if (entry == null) {
            return;
        }
        entry.returnDim = player.level().dimension().location().toString();
        entry.returnX = player.getX();
        entry.returnY = player.getY();
        entry.returnZ = player.getZ();
        entry.returnYaw = player.getYRot();
        entry.returnPitch = player.getXRot();
        entry.hasReturnPoint = true;
        PersonalDimensionData.get(player.server).setDirty();
    }

    // ==================== 维度创建 ====================

    /**
     * 获取或创建玩家个人维度,返回世界实例.失败返回 null.
     */
    public static ServerLevel getOrCreateDimension(ServerPlayer player) {
        return getOrCreateDimension(player.server, player.getUUID());
    }

    /**
     * 获取或创建指定所有者的个人维度,返回世界实例.失败返回 null.
     */
    public static synchronized ServerLevel getOrCreateDimension(MinecraftServer server, UUID ownerId) {
        ResourceKey<Level> key = dimensionKeyFor(ownerId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            return existing;
        }
        ServerLevel created = createDimension(server, ownerId);
        if (created != null) {
            PlayerDimEntry entry = getEntry(server, ownerId);
            if (entry != null) {
                entry.created = true;
                PersonalDimensionData.get(server).setDirty();
            }
            AE2Enhanced.LOGGER.info("[AE2E] Created personal dimension {} for player {}",
                    key.location(), ownerId);
        }
        return created;
    }

    /**
     * 运行时创建个人维度世界并注入服务器.
     */
    @Nullable
    private static ServerLevel createDimension(MinecraftServer server, UUID owner) {
        ResourceKey<Level> key = dimensionKeyFor(owner);
        try {
            RegistryAccess registryAccess = server.registryAccess();
            Holder<DimensionType> typeHolder = registryAccess.registryOrThrow(Registries.DIMENSION_TYPE)
                    .getHolderOrThrow(PERSONAL_DIM_TYPE);
            Holder<Biome> plains = registryAccess.registryOrThrow(Registries.BIOME)
                    .getHolderOrThrow(Biomes.PLAINS);

            ChunkGenerator generator = new PersonalDimChunkGenerator(new FixedBiomeSource(plains),
                    AE2EnhancedConfig.COMMON.personalDimensionFloorY.get());
            LevelStem stem = new LevelStem(typeHolder, generator);
            ServerLevelData levelData = new PersonalDimLevelData(server.getWorldData().overworldData());
            long biomeZoomSeed = BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed());

            MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
            ServerLevel level = new ServerLevel(server, accessor.getExecutor(), accessor.getStorageSource(),
                    levelData, key, stem, NOOP_PROGRESS_LISTENER, false, biomeZoomSeed,
                    List.of(), true, server.overworld().getRandomSequences());

            accessor.getLevels().put(key, level);
            // Forge 的 worldArray 缓存需要显式标记失效,否则新维度不会参与 tick
            server.markWorldsDirty();
            server.overworld().getWorldBorder().addListener(
                    new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder()));
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(level));
            return level;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to create personal dimension for player {}", owner, e);
            return null;
        }
    }

    /**
     * 服务器启动完成后,为所有已创建过的个人维度重建运行时世界.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        PersonalDimensionData data = PersonalDimensionData.get(server);
        for (PlayerDimEntry entry : data.getAllEntries()) {
            if (!entry.created) {
                continue;
            }
            ResourceKey<Level> key = dimensionKeyFor(entry.playerId);
            if (server.getLevel(key) == null && createDimension(server, entry.playerId) == null) {
                AE2Enhanced.LOGGER.error(
                        "[AE2E] Saved personal dimension for player {} could not be recreated.",
                        entry.playerId);
            }
        }
    }

    // ==================== 传送 ====================

    public static void teleportToDimension(ServerPlayer player, ResourceKey<Level> key) {
        ServerLevel target = player.server.getLevel(key);
        if (target == null) {
            return;
        }
        UUID owner = ownerFromKey(key);
        PlayerDimEntry entry = owner != null ? getEntry(player.server, owner) : null;
        BlockPos entryPos = entry != null ? entry.entryPoint
                : new BlockPos(0, AE2EnhancedConfig.COMMON.personalDimensionEntryY.get(), 0);
        player.teleportTo(target, entryPos.getX() + 0.5, entryPos.getY() + 0.1, entryPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    public static void teleportToReturnPoint(ServerPlayer player) {
        PlayerDimEntry entry = getEntry(player.server, player.getUUID());
        if (entry == null || !entry.hasReturnPoint) {
            // 没有记录则返回主世界出生点
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            PlayerAbilityApplier.resetAbilities(player);
            return;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(entry.returnDim);
        ServerLevel target = dimId != null
                ? player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId))
                : null;
        if (target == null) {
            target = player.server.overworld();
        }
        player.teleportTo(target, entry.returnX, entry.returnY, entry.returnZ,
                entry.returnYaw, entry.returnPitch);
        PlayerAbilityApplier.resetAbilities(player);
    }

    /**
     * 将指定玩家传送到目标所有者的个人维度,并校验访问权限.
     *
     * @param player  要传送的玩家
     * @param ownerId 维度所有者
     * @return 是否成功传送
     */
    public static boolean teleportPlayerToDimension(ServerPlayer player, UUID ownerId) {
        if (player.getUUID().equals(ownerId)) {
            ServerLevel level = getOrCreateDimension(player);
            if (level == null) {
                return false;
            }
            teleportToDimension(player, dimensionKeyFor(ownerId));
            return true;
        }

        PlayerDimEntry entry = getEntry(player.server, ownerId);
        if (entry == null || !entry.created) {
            return false;
        }
        if (!entry.allowedPlayers.contains(player.getUUID())
                || !entry.hasPermission(player.getUUID(), PersonalDimPermission.ENTER)) {
            return false;
        }
        ResourceKey<Level> key = dimensionKeyFor(ownerId);
        if (player.server.getLevel(key) == null && createDimension(player.server, ownerId) == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Owner {} personal dimension could not be created, "
                    + "cannot teleport player {}", ownerId, player.getName().getString());
            return false;
        }
        teleportToDimension(player, key);
        return true;
    }

    // ==================== 权限管理 ====================

    /**
     * 邀请玩家进入指定所有者的个人维度.
     */
    public static boolean invitePlayer(MinecraftServer server, UUID ownerId, UUID targetId) {
        PlayerDimEntry entry = getEntry(server, ownerId);
        if (entry == null) {
            return false;
        }
        entry.allowedPlayers.add(targetId);
        entry.grantPermission(targetId, PersonalDimPermission.ENTER);
        entry.grantPermission(targetId, PersonalDimPermission.BUILD);
        entry.grantPermission(targetId, PersonalDimPermission.INTERACT);
        PersonalDimensionData.get(server).setDirty();
        return true;
    }

    /**
     * 将玩家从指定所有者的个人维度白名单移除.
     */
    public static boolean kickPlayer(MinecraftServer server, UUID ownerId, UUID targetId) {
        PlayerDimEntry entry = getEntry(server, ownerId);
        if (entry == null) {
            return false;
        }
        entry.removePlayer(targetId);
        PersonalDimensionData.get(server).setDirty();
        return true;
    }

    /**
     * 设置某玩家对指定所有者维度的某项权限.
     */
    public static boolean setPermission(MinecraftServer server, UUID ownerId, UUID targetId,
            PersonalDimPermission permission, boolean value) {
        PlayerDimEntry entry = getEntry(server, ownerId);
        if (entry == null) {
            return false;
        }
        if (value) {
            entry.grantPermission(targetId, permission);
        } else {
            entry.revokePermission(targetId, permission);
        }
        PersonalDimensionData.get(server).setDirty();
        return true;
    }

    /**
     * 删除指定玩家的个人维度数据,下次进入时会重新创建.
     * 删除前会将仍位于该维度内的所有玩家传送至安全位置（所有者优先返回其记录点）.
     */
    public static boolean deleteDimension(MinecraftServer server, UUID playerId) {
        PlayerDimEntry entry = getEntry(server, playerId);
        if (entry == null || !entry.created) {
            return false;
        }
        ResourceKey<Level> key = dimensionKeyFor(playerId);
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                if (player.getUUID().equals(playerId) && entry.hasReturnPoint) {
                    teleportToReturnPoint(player);
                } else {
                    player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                            0.0f, 0.0f);
                }
                PlayerAbilityApplier.resetAbilities(player);
            }
            try {
                // 先落盘再关闭并移除,区块文件夹保留在磁盘上以便后续重建
                level.save(null, true, false);
                level.close();
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to save/close personal dimension {}", key.location(), e);
            }
            ((MinecraftServerAccessor) server).getLevels().remove(key);
            server.markWorldsDirty();
        }
        PersonalDimensionData.get(server).removeEntry(playerId);
        return true;
    }

    /**
     * 判断玩家是否可以管理指定所有者的个人维度（所有者、MANAGE_RULES 权限或管理员）.
     */
    public static boolean canManage(ServerPlayer player, UUID ownerId) {
        if (player.getUUID().equals(ownerId) || player.hasPermissions(2)) {
            return true;
        }
        PlayerDimEntry entry = getEntry(player.server, ownerId);
        return entry != null && entry.hasPermission(player.getUUID(), PersonalDimPermission.MANAGE_RULES);
    }

    // ==================== 规则 ====================

    public static void setRules(MinecraftServer server, UUID playerId, PersonalDimensionRules rules) {
        PersonalDimensionData.get(server).setRules(playerId, rules);
        // 规则变更后立即对该维度内所有在线玩家应用能力变化(含访客)
        ServerLevel level = server.getLevel(dimensionKeyFor(playerId));
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                PlayerAbilityApplier.applyCapabilities(player, rules);
            }
        }
    }

    /**
     * 按玩家当前所在维度应用/重置能力:
     * 在个人维度内使用<b>维度所有者</b>的规则,不在任何个人维度内时恢复默认,
     * 同时清理 abilities NBT 中可能残留的旧加成(删维度/换存档等场景).
     */
    private static void applyDimensionRules(ServerPlayer player) {
        PlayerDimEntry entry = getEntryByDimension(player.server, player.level().dimension());
        if (entry != null) {
            PlayerAbilityApplier.applyCapabilities(player, entry.rules);
        } else {
            PlayerAbilityApplier.resetAbilities(player);
        }
    }

    // ==================== 颜色方案 ====================

    /**
     * 保存指定所有者的地板颜色方案.
     */
    public static void setColorScheme(MinecraftServer server, UUID ownerId, FloorColorScheme scheme) {
        PersonalDimensionData.get(server).setColorScheme(ownerId, scheme);
    }

    /**
     * 按所有者当前颜色方案重铺个人维度内全部已生成区块的地板层.
     *
     * @return 实际重铺的区块数;维度未创建或未加载时返回 0
     */
    public static int recarpetFloor(MinecraftServer server, UUID ownerId) {
        PlayerDimEntry entry = getEntry(server, ownerId);
        if (entry == null || !entry.created) {
            return 0;
        }
        ResourceKey<Level> key = dimensionKeyFor(ownerId);
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            return 0;
        }
        Path regionDir = ((MinecraftServerAccessor) server).getStorageSource()
                .getDimensionPath(key).resolve("region");
        List<ChunkPos> positions = listGeneratedChunks(regionDir);
        if (positions.isEmpty()) {
            return 0;
        }

        IFloorPreset preset = PresetLoader.getPreset();
        int floorY = AE2EnhancedConfig.COMMON.personalDimensionFloorY.get();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (ChunkPos chunkPos : positions) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int worldX = chunkPos.getBlockX(lx);
                    int worldZ = chunkPos.getBlockZ(lz);
                    BlockState state = preset.getState(worldX, worldZ);
                    if (state == null) {
                        state = bedrock;
                    }
                    chunk.setBlockState(pos.set(worldX, floorY, worldZ), entry.colorScheme.apply(state), false);
                }
            }
            chunk.setUnsaved(true);
            // 地板层变化不触发光照/高度图重算的整包重发,直接给维度内玩家重发区块数据
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk,
                    level.getLightEngine(), null, null);
            for (ServerPlayer viewer : level.players()) {
                viewer.connection.send(packet);
            }
        }
        AE2Enhanced.LOGGER.info("[AE2E] Recarpeted {} chunks in personal dimension of {}", positions.size(),
                ownerId);
        return positions.size();
    }

    /**
     * 扫描 region 目录,列出全部已生成区块坐标(读每个 .mca 的 4KB location table).
     */
    private static List<ChunkPos> listGeneratedChunks(Path regionDir) {
        List<ChunkPos> result = new ArrayList<>();
        if (!Files.isDirectory(regionDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.mca")) {
            for (Path file : stream) {
                String[] parts = file.getFileName().toString().split("\\.");
                if (parts.length != 4) {
                    continue;
                }
                int regionX = Integer.parseInt(parts[1]);
                int regionZ = Integer.parseInt(parts[2]);
                byte[] header = new byte[4096];
                try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                    if (raf.length() < header.length) {
                        continue;
                    }
                    raf.readFully(header);
                }
                for (int i = 0; i < 1024; i++) {
                    int offset = ((header[i * 4] & 0xFF) << 16) | ((header[i * 4 + 1] & 0xFF) << 8)
                            | (header[i * 4 + 2] & 0xFF);
                    int sectors = header[i * 4 + 3] & 0xFF;
                    if (offset != 0 && sectors != 0) {
                        result.add(new ChunkPos(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5)));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to scan region directory {}", regionDir, e);
        }
        return result;
    }

    // ==================== 事件：规则执行 ====================

    /**
     * 阻止个人维度内的生物自然生成.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Level level = event.getLevel().getLevel();
        if (!isPersonalDimension(level.dimension())) {
            return;
        }
        // 幻翼由 PhantomSpawner 直接生成,不经过 ChunkGenerator#getMobsAt;
        // 1.12 无幻翼,为对齐"个人维度无自然刷怪"语义在此无条件拦截
        if (event.getEntity() instanceof Phantom) {
            event.setSpawnCancelled(true);
            return;
        }
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) {
            return;
        }
        PlayerDimEntry entry = getEntryByDimension(server, level.dimension());
        if (entry != null && entry.rules.disableMobSpawning) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * 每 tick 应用天气/时间规则.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!isPersonalDimension(level.dimension())) {
            return;
        }
        PlayerDimEntry entry = getEntryByDimension(level.getServer(), level.dimension());
        if (entry == null) {
            return;
        }

        PersonalDimensionRules rules = entry.rules;
        if (rules.lockWeather && (level.isRaining() || level.isThundering())) {
            level.setWeatherParameters(6000, 0, false, false);
        }
        if (rules.lockTime || !rules.daylightCycle) {
            level.setDayTime(rules.timeValue);
        }
    }

    /**
     * 记录死亡时位于个人维度的玩家,供重生时判定;
     * 对齐 1.12 主分支按"死亡前所在维度"重置,避免在个人维度死亡后把飞行能力带回主世界.
     */
    private static final Set<UUID> diedInPersonalDimension = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isPersonalDimension(player.level().dimension())) {
            diedInPersonalDimension.add(player.getUUID());
        } else {
            diedInPersonalDimension.remove(player.getUUID());
        }
    }

    /**
     * 玩家在个人维度死亡并重生后,按重生落点重新应用/恢复默认能力.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (diedInPersonalDimension.remove(player.getUUID())) {
            applyDimensionRules(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 在维度内按所有者规则应用;不在则恢复默认,清理 abilities 中残留的旧加成
        applyDimensionRules(player);
    }

    /**
     * 玩家切换维度时应用/重置个人维度能力.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isPersonalDimension(event.getTo()) || isPersonalDimension(event.getFrom())) {
            applyDimensionRules(player);
        }
    }

    /**
     * 玩家 tick 中持续应用无飞行惯性规则(按所在维度所有者的规则).
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        PlayerDimEntry entry = getEntryByDimension(player.server, player.level().dimension());
        if (entry != null) {
            PlayerAbilityApplier.tickNoFlightInertia(player, entry.rules);
        }
    }

    // ==================== 事件：权限强制（1.12 缺失,本移植补做） ====================

    /**
     * 判断玩家是否可以在个人维度内行使某项权限.
     * 维度所有者与服务器管理员（权限等级 ≥ 2）始终放行.
     */
    public static boolean hasPermission(ServerPlayer player, ResourceKey<Level> dimension,
            PersonalDimPermission permission) {
        if (!isPersonalDimension(dimension)) {
            return true;
        }
        PlayerDimEntry entry = getEntryByDimension(player.server, dimension);
        if (entry == null) {
            return true;
        }
        if (entry.playerId.equals(player.getUUID()) || player.hasPermissions(2)) {
            return true;
        }
        return entry.hasPermission(player.getUUID(), permission);
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!hasPermission(player, player.level().dimension(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("chat.ae2enhanced.personal_dimension.no_build"), true);
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!hasPermission(player, player.level().dimension(), PersonalDimPermission.BUILD)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("chat.ae2enhanced.personal_dimension.no_build"), true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!hasPermission(player, event.getLevel().dimension(), PersonalDimPermission.INTERACT)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("chat.ae2enhanced.personal_dimension.no_interact"),
                    true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!hasPermission(player, event.getLevel().dimension(), PersonalDimPermission.INTERACT)) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("chat.ae2enhanced.personal_dimension.no_interact"),
                    true);
        }
    }
}
