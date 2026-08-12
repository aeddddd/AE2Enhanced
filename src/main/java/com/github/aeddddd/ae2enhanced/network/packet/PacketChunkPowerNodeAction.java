package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.tile.TileChunkPowerNode;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 -> 服务端：区块供电节点排除（解除绑定）/恢复指定供电目标.
 */
public class PacketChunkPowerNodeAction implements IMessage {

    private BlockPos nodePos;
    private BlockPos targetPos;
    private boolean excluded;

    public PacketChunkPowerNodeAction() {
    }

    public PacketChunkPowerNodeAction(BlockPos nodePos, BlockPos targetPos, boolean excluded) {
        this.nodePos = nodePos;
        this.targetPos = targetPos;
        this.excluded = excluded;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.nodePos = BlockPos.fromLong(buf.readLong());
        this.targetPos = BlockPos.fromLong(buf.readLong());
        this.excluded = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(nodePos.toLong());
        buf.writeLong(targetPos.toLong());
        buf.writeBoolean(excluded);
    }

    public static class Handler implements IMessageHandler<PacketChunkPowerNodeAction, IMessage> {

        @Override
        public IMessage onMessage(PacketChunkPowerNodeAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                TileEntity te = player.world.getTileEntity(message.nodePos);
                if (!(te instanceof TileChunkPowerNode)) return;
                // 距离与权限校验，防止越权操作他人节点
                if (player.getDistanceSq(message.nodePos.getX() + 0.5, message.nodePos.getY() + 0.5,
                        message.nodePos.getZ() + 0.5) > 64.0) return;
                if (!Platform.hasPermissions(player.world, message.nodePos, player)) return;
                ((TileChunkPowerNode) te).setTargetExcluded(message.targetPos, message.excluded);
            });
            return null;
        }
    }
}
