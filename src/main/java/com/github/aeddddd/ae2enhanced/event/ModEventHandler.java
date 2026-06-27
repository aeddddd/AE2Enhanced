package com.github.aeddddd.ae2enhanced.event;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.item.ItemConformalCharge;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;
import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.omnitool.network.WirelessTransmitterNetworkLink;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndo;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraft.util.DamageSource;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AE2Enhanced 全局事件处理器
 */
public final class ModEventHandler {

    private ModEventHandler() {}

    private static final java.lang.reflect.Method DAMAGE_ENTITY_METHOD;
    static {
        java.lang.reflect.Method m = null;
        try {
            m = EntityLivingBase.class.getDeclaredMethod("func_70665_d", DamageSource.class, float.class);
            m.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache damageEntity method", e);
        }
        DAMAGE_ENTITY_METHOD = m;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ModEventHandler());
    }

    /**
     * 配置变更时重载个人维度地板预设缓存。
     */
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (AE2Enhanced.MOD_ID.equals(event.getModID())) {
            PresetLoader.reload();
        }
    }

    /**
     * 允许先进 ME 工具破坏不可破坏方块（如基岩、命令方块等 hardness < 0 的方块）。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote || event.isCanceled()) return;
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemAdvancedMEOmniTool)) return;

        int mode = ItemAdvancedMEOmniTool.getMode(stack);
        if (mode != ItemAdvancedMEOmniTool.MODE_UNIVERSAL && mode != ItemAdvancedMEOmniTool.MODE_TRAVEL) return;

        BlockPos pos = event.getPos();
        IBlockState state = event.getWorld().getBlockState(pos);
        if (state.getBlock().getBlockHardness(state, event.getWorld(), pos) >= 0.0f) return;
        if (MiningModule.isBlacklisted(state.getBlock())) return;

        event.setCanceled(true);
        MiningModule.forceBreakBlock(player, event.getWorld(), pos, stack);
    }

    /**
     * ME 放置工具：左键点击方块时，若已设置线缆起点，则设为终点并放置线缆。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlacementToolLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntityPlayer().world.isRemote || event.isCanceled()) return;

        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemMEPlacementTool)) return;

        PlacementConfig config = new PlacementConfig(stack);
        BlockPos start = config.getCableStart();
        if (start == null) return; // 无线缆起点，正常破坏

        event.setCanceled(true);
        BlockPos end = event.getPos().offset(event.getFace());
        AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementCablePlace(start, end));
    }

    /**
     * ME 放置工具 / 先进 ME 工具放置模式：Ctrl + 右键撤销上一次放置。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlacementToolRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntityPlayer().world.isRemote || event.isCanceled()) return;

        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        boolean isPlacementTool = stack.getItem() instanceof ItemMEPlacementTool;
        boolean isOmniPlacement = stack.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(stack) == ItemAdvancedMEOmniTool.MODE_PLACEMENT;
        if (!isPlacementTool && !isOmniPlacement) return;

        if (!org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LCONTROL)
                && !org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RCONTROL)) {
            return;
        }

        event.setCanceled(true);
        AE2Enhanced.network.sendToServer(new PacketPlacementUndo());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.getWorld().isRemote || event.isCanceled()) return;

        // 先进 ME 收集器优先处理方块破坏掉落
        if (!event.getDrops().isEmpty()) {
            List<ItemStack> drops = event.getDrops();
            collectItemDrops(event.getWorld(), event.getPos(), drops);
            if (drops.isEmpty()) {
                event.setDropChance(0.0f);
                return;
            }
        }

        EntityPlayer player = event.getHarvester();
        if (player == null) return;
        ItemStack mainHand = player.getHeldItemMainhand();
        if (!(mainHand.getItem() instanceof ItemAdvancedMEOmniTool)) return;

        int dropMode = OmniToolUpgrades.getDropMode(mainHand);
        if (dropMode == ItemAdvancedMEOmniTool.DROP_NORMAL) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        event.setDropChance(0.0f);

        if (dropMode == ItemAdvancedMEOmniTool.DROP_INVENTORY) {
            for (ItemStack drop : drops) {
                if (!player.inventory.addItemStackToInventory(drop)) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
            }
        } else if (dropMode == ItemAdvancedMEOmniTool.DROP_AE) {
            IMEMonitor<IAEItemStack> monitor = WirelessTransmitterNetworkLink.getItemMonitor(mainHand, player.world);
            if (monitor == null) {
                // 未绑定 AE 或发射器不可用，回退到正常掉落
                for (ItemStack drop : drops) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
                return;
            }

            IActionSource source = new IActionSource() {
                @Override
                public Optional<EntityPlayer> player() {
                    return Optional.of(player);
                }
                @Override
                public Optional<appeng.api.networking.security.IActionHost> machine() {
                    return Optional.empty();
                }
                @Override
                public <T> Optional<T> context(Class<T> key) {
                    return Optional.empty();
                }
            };

            for (ItemStack drop : drops) {
                IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(drop);
                if (aeStack != null) {
                    IAEItemStack leftover = monitor.injectItems(aeStack, appeng.api.config.Actionable.MODULATE, source);
                    if (leftover != null && leftover.getStackSize() > 0) {
                        ItemStack overflow = leftover.createItemStack();
                        EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, overflow);
                        player.world.spawnEntity(entityItem);
                    }
                } else {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!"matter_cannon".equals(event.getSource().getDamageType())) return;
        if (event.getAmount() <= 1_000_000.0f) return;

        EntityLivingBase entity = event.getEntityLiving();
        World world = entity.world;
        double x = entity.posX;
        double y = entity.posY + entity.height / 2.0;
        double z = entity.posZ;

        // ① 粒子爆发
        if (!world.isRemote) {
            for (int i = 0; i < 10; i++) {
                world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                        x + (world.rand.nextDouble() - 0.5) * 2.0,
                        y + (world.rand.nextDouble() - 0.5) * 2.0,
                        z + (world.rand.nextDouble() - 0.5) * 2.0,
                        0.0, 0.0, 0.0);
            }
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL,
                        x + (world.rand.nextDouble() - 0.5) * 3.0,
                        y + (world.rand.nextDouble() - 0.5) * 3.0,
                        z + (world.rand.nextDouble() - 0.5) * 3.0,
                        world.rand.nextGaussian() * 0.5,
                        world.rand.nextGaussian() * 0.5,
                        world.rand.nextGaussian() * 0.5);
            }
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.END_ROD,
                        x + (world.rand.nextDouble() - 0.5) * 2.0,
                        y + (world.rand.nextDouble() - 0.5) * 2.0,
                        z + (world.rand.nextDouble() - 0.5) * 2.0,
                        world.rand.nextGaussian() * 0.3,
                        world.rand.nextGaussian() * 0.3,
                        world.rand.nextGaussian() * 0.3);
            }
        }

        // ② 处决伤害：反射调用 damageEntity 绕过 Forge 事件系统
        if (DAMAGE_ENTITY_METHOD != null) {
            try {
                DamageSource exec = new DamageSource("ae2enhanced_conformal");
                exec.setDamageIsAbsolute();
                DAMAGE_ENTITY_METHOD.invoke(entity, exec, Float.MAX_VALUE);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Conformal damage reflection failed, falling back", e);
                entity.setHealth(0.0f);
            }
        } else {
            entity.setHealth(0.0f);
        }

        // ③ 虚空伤害
        if (DAMAGE_ENTITY_METHOD != null) {
            try {
                DAMAGE_ENTITY_METHOD.invoke(entity, DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Void damage reflection failed", e);
            }
        }

        // ④ 击退
        if (event.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) event.getSource().getTrueSource();
            double dx = attacker.posX - entity.posX;
            double dz = attacker.posZ - entity.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.001) {
                dx /= dist;
                dz /= dist;
                entity.knockBack(attacker, 4.0f, -dx, -dz);
            }
        }

        // ⑤ 燃烧
        entity.setFire(10);
    }

    // ==================== Anti-Heal ====================

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (CombatModule.hasAntiHeal(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    /**
     * 兜底：每 tick 末强制把带 anti-heal 且未死亡的实体标记为 isDead。
     * 防止某些具有自定义存活逻辑的实体通过覆盖 setDead / onLivingUpdate 等手段
     * 阻止自身被 World.updateEntities() 移除。
     */
    @SubscribeEvent
    public void onWorldTick(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || event.world.isRemote) return;
        for (net.minecraft.entity.Entity entity : event.world.loadedEntityList) {
            if (entity instanceof EntityLivingBase && CombatModule.hasAntiHeal((EntityLivingBase) entity)) {
                if (!entity.isDead) {
                    ForceKillHelper.forceSetIsDead(entity, true);
                }
            }
        }
    }

    // ==================== Conformal Charge Upgrade ====================

    /**
     * 合成共形不变荷升级时保留原 ME 工具的全部 NBT（附魔、混沌核心、时运等）。
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.crafting;
        if (!(result.getItem() instanceof ItemAdvancedMEOmniTool)) return;

        boolean hasOmniTool = false;
        boolean hasConformal = false;
        ItemStack original = ItemStack.EMPTY;

        for (int i = 0; i < event.craftMatrix.getSizeInventory(); i++) {
            ItemStack input = event.craftMatrix.getStackInSlot(i);
            if (input.isEmpty()) continue;
            if (input.getItem() instanceof ItemAdvancedMEOmniTool) {
                hasOmniTool = true;
                original = input;
            } else if (input.getItem() instanceof ItemConformalCharge) {
                hasConformal = true;
            }
        }

        if (hasOmniTool && hasConformal) {
            if (!original.isEmpty() && original.hasTagCompound()) {
                result.setTagCompound(original.getTagCompound().copy());
            }
            OmniToolUpgrades.setConformalCharge(result, true);
        }
    }

    /**
     * 玩家死亡时，共形不变荷升级的 ME 工具不掉落，而是保留在死亡数据中待重生后恢复。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntityLiving().world.isRemote || event.isCanceled() || event.getDrops().isEmpty()) return;
        collectEntityDrops(event.getEntityLiving().world, event.getEntityLiving().getPosition(), event.getDrops());
        if (event.getDrops().isEmpty()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDrops(PlayerDropsEvent event) {
        java.util.Iterator<EntityItem> it = event.getDrops().iterator();
        NBTTagList preserved = new NBTTagList();
        while (it.hasNext()) {
            EntityItem drop = it.next();
            ItemStack stack = drop.getItem();
            if (stack.getItem() instanceof ItemAdvancedMEOmniTool && OmniToolUpgrades.hasConformalCharge(stack)) {
                it.remove();
                preserved.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        if (preserved.tagCount() > 0) {
            event.getEntityPlayer().getEntityData().setTag("AE2E_ConformalPreserved", preserved);
        }

        // 在保留共形不变荷 ME 工具后,将剩余掉落物交给先进 ME 收集器处理
        if (!event.getDrops().isEmpty()) {
            collectEntityDrops(event.getEntityPlayer().world, event.getEntityPlayer().getPosition(), event.getDrops());
            if (event.getDrops().isEmpty()) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 将 ItemStack 掉落物列表交给范围内的先进 ME 收集器处理,成功收集的将从列表移除.
     */
    private void collectItemDrops(World world, BlockPos pos, List<ItemStack> drops) {
        List<TileAdvancedMECollector> collectors = com.github.aeddddd.ae2enhanced.collector.CollectorRegistry.findCollectorsFor(world, pos);
        if (collectors.isEmpty()) return;

        java.util.Iterator<ItemStack> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (drop.isEmpty()) {
                it.remove();
                continue;
            }
            for (TileAdvancedMECollector collector : collectors) {
                ItemStack leftover = collector.tryCollectStackForced(drop);
                if (leftover.isEmpty()) {
                    it.remove();
                    break;
                } else if (leftover.getCount() < drop.getCount()) {
                    drop.setCount(leftover.getCount());
                    break;
                }
            }
        }
    }

    /**
     * 将 EntityItem 掉落物列表交给范围内的先进 ME 收集器强制处理,成功收集的将从列表移除.
     */
    private void collectEntityDrops(World world, BlockPos pos, List<EntityItem> drops) {
        List<TileAdvancedMECollector> collectors = com.github.aeddddd.ae2enhanced.collector.CollectorRegistry.findCollectorsFor(world, pos);
        if (collectors.isEmpty()) return;

        java.util.Iterator<EntityItem> it = drops.iterator();
        while (it.hasNext()) {
            EntityItem drop = it.next();
            if (drop == null || drop.getItem().isEmpty()) {
                it.remove();
                continue;
            }
            for (TileAdvancedMECollector collector : collectors) {
                ItemStack leftover = collector.tryCollectStackForced(drop.getItem());
                if (leftover.isEmpty()) {
                    it.remove();
                    break;
                } else if (leftover.getCount() < drop.getItem().getCount()) {
                    drop.setItem(leftover);
                    break;
                }
            }
        }
    }

    /**
     * 玩家重生时，将死亡前保留的共形不变荷 ME 工具恢复到背包。
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        NBTTagCompound entityData = event.player.getEntityData();
        if (!entityData.hasKey("AE2E_ConformalPreserved", Constants.NBT.TAG_LIST)) return;
        NBTTagList preserved = entityData.getTagList("AE2E_ConformalPreserved", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < preserved.tagCount(); i++) {
            ItemStack stack = new ItemStack(preserved.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                event.player.inventory.addItemStackToInventory(stack);
            }
        }
        entityData.removeTag("AE2E_ConformalPreserved");
    }
}
