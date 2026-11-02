package com.github.aeddddd.ae2enhanced.util.termination;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.runtime.ServerScope;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * L4 终止登记：只依赖 {@link Entity} 的基类契约，不写 DataManager、不猜健康通道、不碰字节码。
 *
 * <p>击杀只做三件事：调用一次 {@code onDeath} 保留掉落/归属，直写 {@code isDead} 让世界 tick
 * 负责移除，再在一个有界窗口内复核（防止目标在下一 tick 把自己救回来）。玩家不在普通移除路径上，
 * 因此只做一次 {@code onDeath} + 血量归零，并由同一个窗口做有限次重申。</p>
 *
 * <p>登记表只保存维度、实体 ID、运行时类和坐标，不持有实体引用；实体 ID 被复用时通过类复核丢弃。</p>
 */
public final class TerminationRegistry implements AutoCloseable {

    private static final Field IS_DEAD = resolveIsDead();

    private final Map<Key, Pending> pending = new HashMap<>();
    private final Map<Key, Recent> recent = new HashMap<>();

    private TerminationRegistry() {
    }

    // ==================== 公共入口 ====================

    /** 当前服务器作用域内的登记表；不存在时返回 null，不创建新状态。 */
    public static @Nullable TerminationRegistry active() {
        return RuntimeScopes.currentServer()
                .flatMap(scope -> scope.existingService(TerminationRegistry.class))
                .orElse(null);
    }

    /**
     * 终止一个生物。多部件命中先归一到主体；玩家只走死亡入口与有限重申。
     *
     * @param target  目标（可以是部件实体）
     * @param attacker 击杀归属，可为 null
     * @param source  死亡来源，必须非空
     */
    public static void terminate(EntityLivingBase target, @Nullable Entity attacker, DamageSource source) {
        if (target == null || target.world == null || target.world.isRemote || source == null) {
            return;
        }
        Entity root = resolveRoot(target);
        if (root.isDead) {
            return;
        }
        if (!(root instanceof EntityLivingBase)) {
            ensureDead(root);
            return;
        }
        TerminationRegistry registry = currentForWork();
        if (registry == null) {
            // 无服务器作用域（测试、启动早期）：退化为一次性 L4，不保留跨作用域状态。
            oneShot((EntityLivingBase) root, source);
            return;
        }
        registry.register((EntityLivingBase) root, attacker, source);
    }

    /** 记录一个已经走完死亡入口的玩家，仅用于有限次血量重申。 */
    public static void trackPlayer(EntityPlayer player, DamageSource source) {
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        TerminationRegistry registry = currentForWork();
        if (registry != null) {
            registry.registerPlayer(player);
        }
    }

    /** 该实体是否正在终止窗口内；供 Mixin 收敛血量伪装使用。 */
    public static boolean isTerminating(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity.world == null || entity.world.isRemote) {
            return false;
        }
        TerminationRegistry registry = active();
        return registry != null && registry.contains(entity);
    }

    /** 直写 {@code isDead} 字段；反射不可用时回退到 {@code setDead()}。 */
    public static void ensureDead(Entity entity) {
        if (entity == null || entity.isDead) {
            return;
        }
        if (IS_DEAD != null) {
            try {
                IS_DEAD.setBoolean(entity, true);
                return;
            } catch (Exception ignored) {
                // 回退到公开方法
            }
        }
        entity.setDead();
    }

    // ==================== 登记与复核 ====================

    private static void oneShot(EntityLivingBase target, DamageSource source) {
        try {
            target.setHealth(0.0f);
        } catch (Throwable ignored) {
            // 一次性路径不记录日志，避免刷屏
        }
        try {
            target.onDeath(source);
        } catch (Throwable ignored) {
            // 死亡入口失败时仍由 isDead 保证移除
        }
        ensureDead(target);
    }

    private static Entity resolveRoot(Entity entity) {
        Entity current = entity;
        while (current instanceof MultiPartEntityPart) {
            Object parent = ((MultiPartEntityPart) current).parent;
            if (!(parent instanceof Entity)) {
                break;
            }
            current = (Entity) parent;
        }
        return current;
    }

    private static @Nullable TerminationRegistry currentForWork() {
        ServerScope scope = RuntimeScopes.currentServer().orElse(null);
        if (scope == null || !scope.acceptingWork()) {
            return null;
        }
        return scope.service(TerminationRegistry.class, TerminationRegistry::new);
    }

    private static Field resolveIsDead() {
        for (String name : new String[]{"field_70128_L", "isDead"}) {
            try {
                Field field = Entity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续尝试下一个候选名
            }
        }
        return null;
    }

    private static void logOnce(String stage, Entity target, Throwable failure) {
        AE2Enhanced.LOGGER.warn("[AE2E] termination {} failed for {}: {}",
                stage, target.getClass().getName(), failure.toString());
    }

    // ==================== 内部工具 ====================

    private void register(EntityLivingBase target, @Nullable Entity attacker, DamageSource source) {
        ConfigSnapshot config = ConfigSnapshot.read();
        if (target instanceof EntityPlayer && !config.players) {
            return;
        }
        Key key = new Key(target.dimension, target.getEntityId());
        Pending existing = pending.get(key);
        if (existing != null && existing.type == target.getClass()) {
            if (!existing.player) {
                ensureDead(target);
            }
            return;
        }
        if (pending.size() >= config.maxPending) {
            evictOne();
        }

        Pending entry = new Pending(target.getClass(), config.ttl, target instanceof EntityPlayer);
        pending.put(key, entry);
        recent.put(key, new Recent(target.getClass(), target.dimension, target.posX, target.posY, target.posZ,
                target.world.getTotalWorldTime() + config.substituteWindow));

        if (attacker instanceof EntityLivingBase) {
            target.setRevengeTarget((EntityLivingBase) attacker);
        }
        try {
            target.setHealth(0.0f);
        } catch (Throwable failure) {
            logOnce("terminal health write", target, failure);
        }
        if (entry.player) {
            // 玩家只走一次死亡入口；后续由窗口重申血量，避免重复掉落。
            try {
                target.onDeath(source);
            } catch (Throwable failure) {
                logOnce("player death entry", target, failure);
            }
            return;
        }
        try {
            target.onDeath(source);
        } catch (Throwable failure) {
            logOnce("living death entry", target, failure);
        }
        ensureDead(target);
    }

    private void registerPlayer(EntityPlayer player) {
        ConfigSnapshot config = ConfigSnapshot.read();
        if (!config.players) {
            return;
        }
        Key key = new Key(player.dimension, player.getEntityId());
        Pending existing = pending.get(key);
        if (existing != null && existing.type == player.getClass()) {
            return;
        }
        if (pending.size() >= config.maxPending) {
            evictOne();
        }
        pending.put(key, new Pending(player.getClass(), config.ttl, true));
    }

    /** 服务器 tick 复核；有界预算、有界 TTL，只持 ID 不持引用。 */
    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        expireRecent(server);
        if (pending.isEmpty()) {
            return;
        }
        ConfigSnapshot config = ConfigSnapshot.read();
        int budget = config.tickBudget;
        Iterator<Map.Entry<Key, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            budget--;
            Map.Entry<Key, Pending> entry = iterator.next();
            Key key = entry.getKey();
            Pending record = entry.getValue();
            World world = server.getWorld(key.dimension);
            Entity entity = world == null ? null : world.getEntityByID(key.entityId);
            if (entity == null || entity.getClass() != record.type) {
                // 已移除，或实体 ID 被新实体复用：不误伤。
                iterator.remove();
                continue;
            }
            if (record.player) {
                EntityPlayer player = (EntityPlayer) entity;
                if (!player.isDead && player.getHealth() > 0.0f && record.reasserts < config.playerReasserts) {
                    try {
                        player.setHealth(0.0f);
                    } catch (Throwable failure) {
                        logOnce("player health reassert", player, failure);
                    }
                    record.reasserts++;
                }
                if (--record.ttl <= 0) {
                    iterator.remove();
                }
                continue;
            }
            if (!entity.isDead) {
                ensureDead(entity);
            }
            world.removeEntity(entity);
            if (--record.ttl <= 0) {
                iterator.remove();
            }
        }
    }

    /** 同一次终止后的短窗替身防护：同运行时类、同维度、限定半径。 */
    public boolean shouldBlockJoin(Entity entity) {
        if (entity == null || entity.world == null || entity.world.isRemote || entity instanceof EntityPlayer) {
            return false;
        }
        ConfigSnapshot config = ConfigSnapshot.read();
        if (!config.substituteGuard || config.substituteRadius <= 0.0) {
            return false;
        }
        long now = entity.world.getTotalWorldTime();
        double radiusSq = config.substituteRadius * config.substituteRadius;
        for (Recent record : recent.values()) {
            if (now > record.expire) {
                continue;
            }
            if (record.dimension != entity.dimension || record.type != entity.getClass()) {
                continue;
            }
            double dx = entity.posX - record.x;
            double dy = entity.posY - record.y;
            double dz = entity.posZ - record.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        pending.clear();
        recent.clear();
    }

    private boolean contains(Entity entity) {
        if (pending.isEmpty()) {
            return false;
        }
        Pending record = pending.get(new Key(entity.dimension, entity.getEntityId()));
        return record != null && record.type == entity.getClass();
    }

    private void expireRecent(MinecraftServer server) {
        if (recent.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Key, Recent>> iterator = recent.entrySet().iterator();
        while (iterator.hasNext()) {
            Key key = iterator.next().getKey();
            World world = server.getWorld(key.dimension);
            long now = world == null ? 0L : world.getTotalWorldTime();
            Recent record = recent.get(key);
            if (world == null || now > record.expire) {
                iterator.remove();
            }
        }
    }

    private void evictOne() {
        Iterator<Key> iterator = pending.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    // ==================== 数据结构 ====================

    private static final class Key {
        private final int dimension;
        private final int entityId;

        private Key(int dimension, int entityId) {
            this.dimension = dimension;
            this.entityId = entityId;
        }

        @Override
        public int hashCode() {
            return dimension * 31 + entityId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key key = (Key) other;
            return dimension == key.dimension && entityId == key.entityId;
        }
    }

    private static final class Pending {
        private final Class<?> type;
        private final boolean player;
        private int ttl;
        private int reasserts;

        private Pending(Class<?> type, int ttl, boolean player) {
            this.type = type;
            this.ttl = ttl;
            this.player = player;
        }
    }

    private static final class Recent {
        private final Class<?> type;
        private final int dimension;
        private final double x;
        private final double y;
        private final double z;
        private final long expire;

        private Recent(Class<?> type, int dimension, double x, double y, double z, long expire) {
            this.type = type;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.expire = expire;
        }
    }

    private static final class ConfigSnapshot {
        private final int ttl;
        private final int maxPending;
        private final int tickBudget;
        private final int substituteWindow;
        private final int playerReasserts;
        private final boolean substituteGuard;
        private final boolean players;
        private final double substituteRadius;

        private ConfigSnapshot(ServerConfig.OmniTool source) {
            this.ttl = Math.max(1, source.forceKillReassertTicks);
            this.maxPending = Math.max(1, source.forceKillMaxPending);
            this.tickBudget = Math.max(1, source.forceKillTickBudget);
            this.substituteWindow = Math.max(0, source.forceKillSubstituteWindowTicks);
            this.playerReasserts = Math.max(0, source.forceKillPlayerReasserts);
            this.substituteGuard = source.forceKillSubstituteGuard;
            this.players = source.forceKillPlayers;
            this.substituteRadius = Math.max(0.0, source.forceKillSubstituteRadius);
        }

        private static ConfigSnapshot read() {
            return new ConfigSnapshot(ServerConfig.current().omniTool);
        }
    }
}
