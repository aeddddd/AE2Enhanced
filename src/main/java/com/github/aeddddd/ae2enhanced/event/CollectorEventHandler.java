package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.collector.CollectorDropService;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class CollectorEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.getWorld().isRemote || event.isCanceled() || event.getDrops().isEmpty()) return;
        CollectorDropService.collectItemDrops(event.getWorld(), event.getPos(), event.getDrops());
        if (event.getDrops().isEmpty()) event.setDropChance(0.0f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntityLiving().world.isRemote || event.isCanceled() || event.getDrops().isEmpty()) return;
        CollectorDropService.collectEntityDrops(event.getEntityLiving().world, event.getEntityLiving().getPosition(), event.getDrops());
        if (event.getDrops().isEmpty()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDrops(PlayerDropsEvent event) {
        if (event.getDrops().isEmpty()) return;
        CollectorDropService.collectEntityDrops(event.getEntityPlayer().world, event.getEntityPlayer().getPosition(), event.getDrops());
        if (event.getDrops().isEmpty()) event.setCanceled(true);
    }
}
