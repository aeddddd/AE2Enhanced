package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * C→S 奇点处理仓动作包：循环红石模式.
 * （物品取回/倒入走 Container.slotClick 标准链路,不在此包内）
 */
public class PacketChamberAction implements IMessage {

    public static final int ACTION_CYCLE_REDSTONE = 3;

    private BlockPos pos;
    private int action;
    private String param = "";

    public PacketChamberAction() {
    }

    public PacketChamberAction(BlockPos pos, int action, String param) {
        this.pos = pos;
        this.action = action;
        this.param = param != null ? param : "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        action = buf.readInt();
        param = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, param);
    }

    public static class Handler implements IMessageHandler<PacketChamberAction, IMessage> {

        @Override
        public IMessage onMessage(PacketChamberAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                // 距离校验,防止远程操作任意坐标的处理仓
                if (player.getDistanceSq(message.pos) > 64.0) {
                    return;
                }
                TileEntity te = player.world.getTileEntity(message.pos);
                if (!(te instanceof TileSingularityChamber)) {
                    return;
                }
                if (message.action == ACTION_CYCLE_REDSTONE) {
                    ((TileSingularityChamber) te).cycleRedstoneMode();
                }
            });
            return null;
        }
    }
}
