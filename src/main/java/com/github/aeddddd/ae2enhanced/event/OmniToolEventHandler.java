package com.github.aeddddd.ae2enhanced.event;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;
import com.github.aeddddd.ae2enhanced.omnitool.network.WirelessTransmitterNetworkLink;
import com.github.aeddddd.ae2enhanced.util.reflection.VanillaReflectionHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OmniToolEventHandler {
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
        if (state.getBlock().getBlockHardness(state, event.getWorld(), pos) >= 0.0f || MiningModule.isBlacklisted(state.getBlock())) return;
        event.setCanceled(true);
        MiningModule.forceBreakBlock(player, event.getWorld(), pos, stack);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.getWorld().isRemote || event.isCanceled() || event.getDrops().isEmpty()) return;
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
                if (!player.inventory.addItemStackToInventory(drop)) player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, drop));
            }
        } else if (dropMode == ItemAdvancedMEOmniTool.DROP_AE) {
            IMEMonitor<IAEItemStack> monitor = WirelessTransmitterNetworkLink.getItemMonitor(mainHand, player.world);
            if (monitor == null) {
                for (ItemStack drop : drops) player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, drop));
                return;
            }
            IActionSource source = new IActionSource() {
                @Override public Optional<EntityPlayer> player() { return Optional.of(player); }
                @Override public Optional<appeng.api.networking.security.IActionHost> machine() { return Optional.empty(); }
                @Override public <T> Optional<T> context(Class<T> key) { return Optional.empty(); }
            };
            for (ItemStack drop : drops) {
                IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(drop);
                if (aeStack != null) {
                    IAEItemStack leftover = monitor.injectItems(aeStack, appeng.api.config.Actionable.MODULATE, source);
                    if (leftover != null && leftover.getStackSize() > 0) {
                        player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, leftover.createItemStack()));
                    }
                } else player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, drop));
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!"matter_cannon".equals(event.getSource().getDamageType()) || event.getAmount() <= 1_000_000.0f) return;
        EntityLivingBase entity = event.getEntityLiving();
        World world = entity.world;
        double x = entity.posX;
        double y = entity.posY + entity.height / 2.0;
        double z = entity.posZ;
        if (!world.isRemote) {
            for (int i = 0; i < 10; i++) world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                    x + (world.rand.nextDouble() - 0.5) * 2.0, y + (world.rand.nextDouble() - 0.5) * 2.0,
                    z + (world.rand.nextDouble() - 0.5) * 2.0, 0.0, 0.0, 0.0);
            for (int i = 0; i < 20; i++) world.spawnParticle(EnumParticleTypes.PORTAL,
                    x + (world.rand.nextDouble() - 0.5) * 3.0, y + (world.rand.nextDouble() - 0.5) * 3.0,
                    z + (world.rand.nextDouble() - 0.5) * 3.0, world.rand.nextGaussian() * 0.5,
                    world.rand.nextGaussian() * 0.5, world.rand.nextGaussian() * 0.5);
            for (int i = 0; i < 20; i++) world.spawnParticle(EnumParticleTypes.END_ROD,
                    x + (world.rand.nextDouble() - 0.5) * 2.0, y + (world.rand.nextDouble() - 0.5) * 2.0,
                    z + (world.rand.nextDouble() - 0.5) * 2.0, world.rand.nextGaussian() * 0.3,
                    world.rand.nextGaussian() * 0.3, world.rand.nextGaussian() * 0.3);
        }
        DamageSource execution = new DamageSource("ae2enhanced_conformal");
        execution.setDamageIsAbsolute();
        if (!VanillaReflectionHelper.damageEntity(entity, execution, Float.MAX_VALUE)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Conformal damage invoker unavailable, falling back to setHealth");
            entity.setHealth(0.0f);
        }
        if (!VanillaReflectionHelper.damageEntity(entity, DamageSource.OUT_OF_WORLD, Float.MAX_VALUE)) {
            AE2Enhanced.LOGGER.warn("[AE2E] Void damage invoker unavailable");
        }
        if (event.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) event.getSource().getTrueSource();
            double dx = attacker.posX - entity.posX;
            double dz = attacker.posZ - entity.posZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.001) entity.knockBack(attacker, 4.0f, -dx / distance, -dz / distance);
        }
        entity.setFire(10);
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (CombatModule.hasAntiHeal(event.getEntityLiving())) event.setCanceled(true);
    }
}
