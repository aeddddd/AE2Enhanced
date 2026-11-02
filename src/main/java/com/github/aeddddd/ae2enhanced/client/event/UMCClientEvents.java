package com.github.aeddddd.ae2enhanced.client.event;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings;
import com.github.aeddddd.ae2enhanced.clientapi.ClientInput;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.item.packet.PacketUMCAction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** UMC 客户端输入与按键提示装配。 */
@SideOnly(Side.CLIENT)
public final class UMCClientEvents {

    private UMCClientEvents() {
    }

    public static void register() {
        ClientInput.register(UMCKeyBindings::isSelectDown, UMCKeyBindings::isClearDown,
                UMCKeyBindings::isTooltipDetailDown, key -> {
                    switch (key) {
                        case 0:
                            return UMCKeyBindings.displayName(UMCKeyBindings.TOOLTIP_DETAIL);
                        case 1:
                            return UMCKeyBindings.displayName(UMCKeyBindings.SELECT_MODIFIER);
                        case 2:
                            return UMCKeyBindings.displayName(UMCKeyBindings.CLEAR_MODIFIER);
                        default:
                            return "";
                    }
                });
        MinecraftForge.EVENT_BUS.register(new UMCClientEvents());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getEntityPlayer().getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemUniversalMemoryCard)) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        boolean isSneaking = player.isSneaking();
        boolean isSelect = UMCKeyBindings.isSelectDown();
        boolean isClear = UMCKeyBindings.isClearDown();
        net.minecraft.tileentity.TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        boolean central = tile instanceof com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
        boolean smart = tile instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
        boolean recycler = tile instanceof com.github.aeddddd.ae2enhanced.recycler.TileMENetworkRecycler;
        boolean security = tile instanceof appeng.tile.misc.TileSecurityStation;
        if (security && !isSneaking && !isSelect && !isClear) {
            return;
        }
        if (smart && !isSneaking && !isSelect && !isClear) {
            ItemUniversalMemoryCard.handleSmartPatternBindClient(player, stack, event.getPos(), event.getWorld());
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.FAIL);
            return;
        }
        PacketUMCAction.ActionType type;
        if (recycler && isClear && !isSneaking && !isSelect) {
            type = PacketUMCAction.ActionType.CLEAR_RECYCLER_BINDINGS;
        } else if (recycler && !isSneaking && !isSelect) {
            type = PacketUMCAction.ActionType.BIND_RECYCLER;
        } else if (central && isClear && !isSneaking && !isSelect) {
            type = PacketUMCAction.ActionType.CLEAR_BINDINGS;
        } else if (central && !isSneaking && !isSelect) {
            type = PacketUMCAction.ActionType.BIND_SOURCE;
        } else if (isSelect) {
            type = PacketUMCAction.ActionType.SELECT;
        } else if (isSneaking) {
            type = PacketUMCAction.ActionType.COPY;
        } else {
            type = PacketUMCAction.ActionType.PASTE;
        }
        AE2Enhanced.network.sendToServer(new PacketUMCAction(type, event.getPos(), event.getFace()));
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.FAIL);
    }

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (event.getButton() != 1 || !event.isButtonstate()) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) {
            return;
        }
        ItemStack stack = mc.player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemUniversalMemoryCard)) {
            return;
        }
        net.minecraft.util.math.RayTraceResult ray = mc.objectMouseOver;
        if (ray == null || ray.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.MISS) {
            AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.OPEN_GUI));
            event.setCanceled(true);
        }
    }
}
