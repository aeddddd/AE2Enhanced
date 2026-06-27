package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.IActionHost;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.concurrent.Future;

/**
 * 支持 long 数量下单的合成请求包.
 * AE2-UEL 原生的 PacketCraftRequest 构造函数接收 int,导致超过 int 范围的下单量被截断.
 * 此包复制了原生包的完整服务端处理逻辑,但使用 long 传输数量.
 */
public class PacketCraftRequestLong implements IMessage {

    private long amount;
    private boolean heldShift;

    public PacketCraftRequestLong() {
    }

    public PacketCraftRequestLong(long craftAmt, boolean shift) {
        this.amount = craftAmt;
        this.heldShift = shift;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.heldShift = buf.readBoolean();
        this.amount = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.heldShift);
        buf.writeLong(this.amount);
    }

    public static class Handler implements IMessageHandler<PacketCraftRequestLong, IMessage> {

        @Override
        public IMessage onMessage(PacketCraftRequestLong message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ContainerCraftAmount cca;
                Object target;
                if (player.openContainer instanceof ContainerCraftAmount
                        && (target = (cca = (ContainerCraftAmount) player.openContainer).getTarget()) instanceof IActionHost) {
                    IActionHost ah = (IActionHost) target;
                    IGridNode gn = ah.getActionableNode();
                    if (gn == null) return;
                    IGrid g = gn.getGrid();
                    if (g == null || cca.getItemToCraft() == null) return;

                    cca.getItemToCraft().setStackSize(message.amount);
                    Future<ICraftingJob> futureJob = null;
                    try {
                        ICraftingGrid cg = g.getCache(ICraftingGrid.class);
                        futureJob = cg.beginCraftingJob(cca.getWorld(), cca.getGrid(), cca.getActionSrc(), cca.getItemToCraft(), null);
                        ContainerOpenContext context = cca.getOpenContext();
                        if (context != null) {
                            TileEntity te = context.getTile();
                            if (te != null) {
                                Platform.openGUI(player, te, cca.getOpenContext().getSide(), GuiBridge.GUI_CRAFTING_CONFIRM);
                            } else if (ah instanceof IInventorySlotAware) {
                                IInventorySlotAware i = (IInventorySlotAware) ah;
                                Platform.openGUI(player, i.getInventorySlot(), GuiBridge.GUI_CRAFTING_CONFIRM, i.isBaubleSlot());
                            }
                            if (player.openContainer instanceof ContainerCraftConfirm) {
                                ContainerCraftConfirm ccc = (ContainerCraftConfirm) player.openContainer;
                                ccc.setAutoStart(message.heldShift);
                                ccc.setJob(futureJob);
                                cca.detectAndSendChanges();
                            }
                        }
                    } catch (Throwable e) {
                        if (futureJob != null) {
                            futureJob.cancel(true);
                        }
                        AELog.debug(e);
                    }
                }
            });
            return null;
        }
    }
}
