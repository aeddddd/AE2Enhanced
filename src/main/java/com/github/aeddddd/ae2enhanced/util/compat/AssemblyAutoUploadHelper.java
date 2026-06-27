package com.github.aeddddd.ae2enhanced.util.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

/**
 * 装配枢纽自动上传功能的辅助类.
 * 被 MixinPatternEncoder 和 ContainerOmniTerm 共用.
 */
public class AssemblyAutoUploadHelper {

    /**
     * 检查样板是否为合成样板(crafting=1),而非处理样板.
     */
    public static boolean isCraftingPattern(ItemStack pattern) {
        if (!pattern.hasTagCompound()) return false;
        NBTTagCompound tag = pattern.getTagCompound();
        return tag.hasKey("crafting", Constants.NBT.TAG_BYTE) && tag.getByte("crafting") == 1;
    }

    /**
     * 尝试将样板自动上传到最近的、安装了自动上传升级的装配枢纽.
     * 搜索范围：所有已加载维度中同一 ME 网络内的装配枢纽.
     *
     * @param playerGrid 玩家当前所在的 ME 网格(通过终端获取),为 null 时不限制网络
     * @return true 如果上传成功
     */
    public static boolean tryUploadPattern(World world, EntityPlayer player, ItemStack pattern, IGrid playerGrid) {
        if (world.isRemote || pattern.isEmpty()) return false;
        if (!isCraftingPattern(pattern)) return false;

        TileAssemblyController target = findTargetController(player, pattern, playerGrid);
        if (target == null) return false;

        return target.tryAutoUploadPattern(pattern);
    }

    /**
     * 兼容旧调用：不限制 ME 网络.
     */
    public static boolean tryUploadPattern(World world, EntityPlayer player, ItemStack pattern) {
        return tryUploadPattern(world, player, pattern, null);
    }

    private static TileAssemblyController findTargetController(EntityPlayer player, ItemStack pattern, IGrid playerGrid) {
        TileAssemblyController best = null;
        double bestDist = Double.MAX_VALUE;

        // 遍历所有已加载维度
        for (World w : DimensionManager.getWorlds()) {
            if (w == null) continue;
            TileAssemblyController candidate = findTargetControllerInWorld(w, player, pattern, playerGrid);
            if (candidate != null) {
                double dist = player.getDistanceSq(candidate.getPos().getX() + 0.5,
                        candidate.getPos().getY() + 0.5,
                        candidate.getPos().getZ() + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static TileAssemblyController findTargetControllerInWorld(World world, EntityPlayer player,
                                                                      ItemStack pattern, IGrid playerGrid) {
        ControllerIndex index = ControllerIndex.get(world);
        if (index == null) return null;

        TileAssemblyController best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : index.getAll()) {
            // 区块必须已加载(天然满足：ControllerIndex 只记录已加载区块中的控制器)
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileAssemblyController)) continue;
            TileAssemblyController controller = (TileAssemblyController) te;
            if (!controller.isFormed()) continue;
            if (!controller.hasAutoUploadUpgrade()) continue;
            if (!controller.canAcceptPattern(pattern)) continue;

            // 网络过滤：如果提供了 playerGrid,只匹配同一网络
            if (playerGrid != null) {
                IGrid controllerGrid = getControllerGrid(controller);
                if (controllerGrid == null || controllerGrid != playerGrid) {
                    continue;
                }
            }

            double dist = player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = controller;
            }
        }
        return best;
    }

    private static IGrid getControllerGrid(TileAssemblyController controller) {
        try {
            appeng.me.helpers.AENetworkProxy proxy = controller.getProxy();
            if (proxy == null) return null;
            IGridNode node = proxy.getNode();
            if (node == null) return null;
            return node.getGrid();
        } catch (Exception e) {
            return null;
        }
    }
}
