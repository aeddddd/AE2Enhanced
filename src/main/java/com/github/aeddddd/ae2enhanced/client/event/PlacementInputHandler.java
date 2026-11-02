package com.github.aeddddd.ae2enhanced.client.event;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementCablePlace;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndo;
import com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.omnitool.placement.PlacementConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public final class PlacementInputHandler {
    private static boolean isPlacementTool(ItemStack stack) {
        return stack.getItem() instanceof ItemMEPlacementTool
                || stack.getItem() instanceof ItemAdvancedMEOmniTool
                && ItemAdvancedMEOmniTool.getMode(stack) == ItemAdvancedMEOmniTool.MODE_PLACEMENT;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getEntityPlayer().world.isRemote || event.isCanceled()) return;
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (!isPlacementTool(stack)) return;
        BlockPos start = new PlacementConfig(stack).getCableStart();
        if (start == null) return;
        event.setCanceled(true);
        AE2Enhanced.network.sendToServer(new PacketPlacementCablePlace(start, event.getPos().offset(event.getFace())));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntityPlayer().world.isRemote || event.isCanceled()) return;
        ItemStack stack = event.getEntityPlayer().getHeldItemMainhand();
        if (!isPlacementTool(stack)) return;
        if (!Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && !Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) return;
        event.setCanceled(true);
        AE2Enhanced.network.sendToServer(new PacketPlacementUndo());
    }
}
