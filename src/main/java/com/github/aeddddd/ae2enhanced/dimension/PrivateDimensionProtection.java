package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 服务端统一的私域建造与交互权限闸门。 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class PrivateDimensionProtection {
    private PrivateDimensionProtection() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!canBuild(event.getPlayer(), event.getWorld().provider.getDimension())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.PlaceEvent event) {
        if (!canBuild(event.getPlayer(), event.getWorld().provider.getDimension())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!canInteract(event.getEntityPlayer(), event.getWorld().provider.getDimension())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!canInteract(event.getEntityPlayer(), event.getWorld().provider.getDimension())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!canInteract(event.getEntityPlayer(), event.getWorld().provider.getDimension())) {
            event.setCanceled(true);
        }
    }

    private static boolean canBuild(EntityPlayer player, int dimension) {
        if (player == null || player.world.isRemote || !PersonalDimensionManager.isPersonalDimension(dimension)) {
            return true;
        }
        PersonalDimensionData data = PersonalDimensionData.get(player.world);
        return PrivateDimensionPermissions.canBuild(player, data, data.getOwnerByDimensionId(dimension));
    }

    private static boolean canInteract(EntityPlayer player, int dimension) {
        if (player == null || player.world.isRemote || !PersonalDimensionManager.isPersonalDimension(dimension)) {
            return true;
        }
        PersonalDimensionData data = PersonalDimensionData.get(player.world);
        return PrivateDimensionPermissions.canInteract(player, data, data.getOwnerByDimensionId(dimension));
    }
}
