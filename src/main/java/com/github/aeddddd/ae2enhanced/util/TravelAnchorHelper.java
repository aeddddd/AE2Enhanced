package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
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

    @Nullable
    public static BlockPos getAnchorTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;

        // 1. 尝试读取 TileEntity NBT 中的目标坐标
        try {
            NBTTagCompound nbt = te.writeToNBT(new NBTTagCompound());
            if (nbt.hasKey("targetX") && nbt.hasKey("targetY") && nbt.hasKey("targetZ")) {
                return new BlockPos(nbt.getInteger("targetX"), nbt.getInteger("targetY"), nbt.getInteger("targetZ"));
            }
            if (nbt.hasKey("x") && nbt.hasKey("y") && nbt.hasKey("z")) {
                return new BlockPos(nbt.getInteger("x"), nbt.getInteger("y"), nbt.getInteger("z"));
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to read Travel Anchor NBT at {}", pos);
        }

        // 2. 尝试调用无参 getter 方法
        for (Method m : te.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String name = m.getName().toLowerCase();
            if (!name.contains("target") && !name.contains("destination") && !name.contains("location")) continue;
            try {
                m.setAccessible(true);
                Object ret = m.invoke(te);
                BlockPos target = toBlockPos(ret);
                if (target != null) return target;
            } catch (Exception ignored) {}
        }

        // 3. 回退：目标为锚点自身所在位置（通常 Travel Anchor 会把你送到另一个 Anchor 的位置）
        return null;
    }

    @Nullable
    private static BlockPos toBlockPos(Object obj) {
        if (obj instanceof BlockPos) return (BlockPos) obj;
        if (obj instanceof TileEntity) return ((TileEntity) obj).getPos();
        return null;
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
