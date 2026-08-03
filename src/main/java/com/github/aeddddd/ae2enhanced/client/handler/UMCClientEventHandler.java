package com.github.aeddddd.ae2enhanced.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;

/**
 * 通用内存卡的客户端交互事件分发(对应 1.12 的 ItemUniversalMemoryCard.ClientEvents).
 *
 * <p>手势设计(在 1.12 基础上重构绑定手势):
 * <ul>
 * <li>Shift+右键方块:复制配置</li>
 * <li>右键方块:粘贴配置(命中选区内方块时批量粘贴)</li>
 * <li>Ctrl+右键方块:选取/取消选取目标</li>
 * <li>右键无线访问点:绑定该网络(对应 1.12 右键中枢 ME 接口绑定)</li>
 * <li>Alt+右键无线访问点:解除绑定(对应 1.12 Alt+右键清空绑定)</li>
 * <li>对空气右键:打开管理界面</li>
 * </ul></p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UMCClientEventHandler {

    private UMCClientEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof UniversalMemoryCardItem)) {
            return;
        }

        boolean isSneaking = player.isShiftKeyDown();
        boolean isCtrl = Screen.hasControlDown();
        boolean isAlt = Screen.hasAltDown();

        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        boolean isAccessPoint = be instanceof IWirelessAccessPoint;

        PacketUMCAction.ActionType type;
        if (isAccessPoint && isAlt && !isSneaking && !isCtrl) {
            type = PacketUMCAction.ActionType.CLEAR_BINDING;
        } else if (isAccessPoint && !isSneaking && !isCtrl) {
            type = PacketUMCAction.ActionType.BIND_ACCESS_POINT;
        } else if (isCtrl) {
            type = PacketUMCAction.ActionType.SELECT;
        } else if (isSneaking) {
            type = PacketUMCAction.ActionType.COPY;
        } else {
            type = PacketUMCAction.ActionType.PASTE;
        }

        ModNetwork.CHANNEL.sendToServer(new PacketUMCAction(type, event.getPos(), event.getFace()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getEntity().getMainHandItem();
        if (!(stack.getItem() instanceof UniversalMemoryCardItem)) {
            return;
        }
        // 仅当准心未指向方块(对空气)时打开管理界面,对应 1.12 的 MouseEvent + ray MISS 判断
        Minecraft mc = Minecraft.getInstance();
        HitResult ray = mc.hitResult;
        if (ray == null || ray.getType() == HitResult.Type.MISS) {
            ModNetwork.CHANNEL.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.OPEN_GUI));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
