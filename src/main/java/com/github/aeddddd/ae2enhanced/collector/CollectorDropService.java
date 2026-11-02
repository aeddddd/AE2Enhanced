package com.github.aeddddd.ae2enhanced.collector;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;

public final class CollectorDropService {
    private CollectorDropService() {
    }

    public static void collectItemDrops(World world, BlockPos pos, List<ItemStack> drops) {
        List<TileAdvancedMECollector> collectors = CollectorRegistry.findCollectorsFor(world, pos);
        if (collectors.isEmpty()) return;
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (drop.isEmpty()) { iterator.remove(); continue; }
            for (TileAdvancedMECollector collector : collectors) {
                ItemStack leftover = collector.tryCollectStackForced(drop);
                if (leftover.isEmpty()) { iterator.remove(); break; }
                if (leftover.getCount() < drop.getCount()) { drop.setCount(leftover.getCount()); break; }
            }
        }
    }

    public static void collectEntityDrops(World world, BlockPos pos, List<EntityItem> drops) {
        List<TileAdvancedMECollector> collectors = CollectorRegistry.findCollectorsFor(world, pos);
        if (collectors.isEmpty()) return;
        Iterator<EntityItem> iterator = drops.iterator();
        while (iterator.hasNext()) {
            EntityItem drop = iterator.next();
            if (drop == null || drop.getItem().isEmpty()) { iterator.remove(); continue; }
            for (TileAdvancedMECollector collector : collectors) {
                ItemStack leftover = collector.tryCollectStackForced(drop.getItem());
                if (leftover.isEmpty()) { iterator.remove(); break; }
                if (leftover.getCount() < drop.getItem().getCount()) { drop.setItem(leftover); break; }
            }
        }
    }
}
