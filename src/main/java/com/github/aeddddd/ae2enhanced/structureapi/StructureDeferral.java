package com.github.aeddddd.ae2enhanced.structureapi;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 未加载结构的延期登记，不依赖结构执行器或具体 Block。 */
public final class StructureDeferral {

    private static final Map<Integer, Set<BlockPos>> DEFERRED = new HashMap<>();

    private StructureDeferral() {
    }

    public static void defer(World world, BlockPos controllerPos) {
        if (!world.isRemote) {
            DEFERRED.computeIfAbsent(world.provider.getDimension(), key -> new HashSet<>())
                    .add(controllerPos.toImmutable());
        }
    }

    public static Set<BlockPos> getDeferred(int dimension) {
        return DEFERRED.get(dimension);
    }

    public static void scheduleCheck(int dimension, BlockPos controllerPos) {
        Set<BlockPos> deferred = DEFERRED.get(dimension);
        if (deferred != null) {
            deferred.remove(controllerPos);
            if (deferred.isEmpty()) {
                DEFERRED.remove(dimension);
            }
        }
    }

    public static void clearDimension(int dimension) {
        DEFERRED.remove(dimension);
    }
}
