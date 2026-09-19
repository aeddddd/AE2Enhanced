package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Ender IO Travel Anchor 兼容辅助类（完全反射，避免硬依赖 EIO）。
 */
public final class TravelAnchorHelper {

    private TravelAnchorHelper() {}

    private static final List<String> ANCHOR_REGISTRY_KEYS = Arrays.asList("travel_anchor", "block_travel_anchor");

    public static boolean isTravelAnchor(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        Block block = world.getBlockState(pos).getBlock();
        ResourceLocation reg = block.getRegistryName();
        if (reg == null) return false;
        String path = reg.getPath().toLowerCase();
        String mod = reg.getNamespace().toLowerCase();
        return ("enderio".equals(mod) || "enderiomachines".equals(mod) || "enderiozoo".equals(mod))
                && ANCHOR_REGISTRY_KEYS.contains(path);
    }

    public static boolean teleportToAnchor(EntityPlayer player, World world, BlockPos target) {
        if (target == null) return false;
        if (world.provider.getDimension() != player.world.provider.getDimension()) return false;
        if (!world.isBlockLoaded(target)) return false;

        // 尝试将玩家传送到目标 Anchor 的上方，避免卡在方块内
        BlockPos landing = findSafeLanding(world, target);
        if (landing == null) landing = target.up();

        player.setPositionAndUpdate(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        player.fallDistance = 0.0f;
        return true;
    }

    @Nullable
    private static BlockPos findSafeLanding(World world, BlockPos anchorPos) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos pos = anchorPos.up(dy);
            if (world.isAirBlock(pos) && world.isAirBlock(pos.up())) {
                return pos;
            }
        }
        return null;
    }
}
