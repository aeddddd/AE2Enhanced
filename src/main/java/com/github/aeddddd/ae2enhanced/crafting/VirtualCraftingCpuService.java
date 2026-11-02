package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.tile.crafting.TileCraftingTile;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

/** 计算核心虚拟 CPU 的生命周期与网格查询。 */
public final class VirtualCraftingCpuService {

    private VirtualCraftingCpuService() {
    }

    public static boolean isVirtual(CraftingCpuExecutionContext context) {
        return context.computationCore() != null;
    }

    public static Boolean activeOverride(CraftingCpuExecutionContext context) {
        if (!isVirtual(context)) {
            return null;
        }
        IGridNode node = ((IActionHost) context.computationCore()).getActionableNode();
        return node != null && node.isActive();
    }

    public static IGrid gridOverride(CraftingCpuExecutionContext context) {
        if (!isVirtual(context)) {
            return null;
        }
        IGridNode node = ((IActionHost) context.computationCore()).getActionableNode();
        return node != null ? node.getGrid() : null;
    }

    public static World worldOverride(CraftingCpuExecutionContext context) {
        return isVirtual(context) ? context.computationCore().getWorld() : null;
    }

    public static String localizedName(CraftingCpuExecutionContext context) {
        return isVirtual(context) ? I18n.translateToLocal("tile.ae2enhanced.computation_core.name") : null;
    }

    public static boolean cancelMarkDirty(CraftingCpuExecutionContext context) {
        boolean first = context.markDirty();
        return !first || isVirtual(context);
    }

    public static boolean redirectTileActive(CraftingCpuExecutionContext context, TileCraftingTile tile) {
        if (isVirtual(context)) {
            IGridNode node = ((IActionHost) context.computationCore()).getActionableNode();
            return node != null && node.isActive();
        }
        return tile != null && tile.isActive();
    }
}
