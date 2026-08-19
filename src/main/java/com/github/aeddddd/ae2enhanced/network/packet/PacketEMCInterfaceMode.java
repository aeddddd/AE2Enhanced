package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.tile.TileEMCInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.math.BigInteger;

/**
 * 客户端请求切换 EMC 接口的创造模式.
 *
 * <p>开启需满足: 已绑定 + 操作者有管理权限 + 绑定玩家 EMC 余额达到上限
 * ({@link TileEMCInterface#EMC_CAP});关闭无限制. 模式状态持久化于方块 NBT,
 * 不随玩家上下线变化.</p>
 */
public class PacketEMCInterfaceMode implements IMessage {

    private BlockPos pos;

    public PacketEMCInterfaceMode() {}

    public PacketEMCInterfaceMode(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.pos.toLong());
    }

    public static class Handler implements IMessageHandler<PacketEMCInterfaceMode, IMessage> {
        @Override
        public IMessage onMessage(PacketEMCInterfaceMode message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.getDistanceSq(message.pos) > 64.0) return;
                World world = player.world;
                TileEntity te = world.getTileEntity(message.pos);
                if (!(te instanceof TileEMCInterface)) return;
                TileEMCInterface tile = (TileEMCInterface) te;
                if (!tile.canManage(player)) {
                    player.sendMessage(new TextComponentTranslation(
                            "chat.ae2enhanced.emc_interface.no_permission"));
                    return;
                }

                if (tile.isCreativeMode()) {
                    tile.setCreativeMode(false);
                    player.sendMessage(new TextComponentTranslation(
                            "chat.ae2enhanced.emc_interface.creative_disabled"));
                    return;
                }

                // 开启校验: 必须已绑定且余额达到 EMC 上限
                if (!tile.isBound()) return;
                Object provider = tile.getKnowledgeProvider();
                BigInteger balance = provider == null
                        ? BigInteger.ZERO : ProjectEHelper.getEmcBig(provider);
                if (balance.compareTo(TileEMCInterface.EMC_CAP) < 0) {
                    player.sendMessage(new TextComponentTranslation(
                            "chat.ae2enhanced.emc_interface.creative_denied"));
                    return;
                }
                tile.setCreativeMode(true);
                player.sendMessage(new TextComponentTranslation(
                        "chat.ae2enhanced.emc_interface.creative_enabled"));
            });
            return null;
        }
    }
}
