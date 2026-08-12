package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.tile.TileChunkPowerNode;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：区块供电节点 GUI 数据同步.
 *
 * <p>携带网络状态、上一 tick 实际输出以及供电目标列表
 * （坐标、方块名、排除状态、上一 tick 交付量）.</p>
 */
public class PacketChunkPowerNodeSync implements IMessage {

    /**
     * 单个供电目标的 GUI 展示数据.
     *
     * <p>display 为目标方块的物品形式（客户端用 {@code getDisplayName()} 获取本地化名称）；
     * 部分方块无对应物品（display 为空）时回退到 fallbackKey + ".name" 翻译.</p>
     */
    public static class TargetInfo {
        private final BlockPos pos;
        private final net.minecraft.item.ItemStack display;
        private final String fallbackKey;
        private final boolean excluded;
        private final long delivered;

        public TargetInfo(BlockPos pos, net.minecraft.item.ItemStack display, String fallbackKey,
                          boolean excluded, long delivered) {
            this.pos = pos;
            this.display = display;
            this.fallbackKey = fallbackKey;
            this.excluded = excluded;
            this.delivered = delivered;
        }

        public BlockPos getPos() {
            return pos;
        }

        public net.minecraft.item.ItemStack getDisplay() {
            return display;
        }

        public String getFallbackKey() {
            return fallbackKey;
        }

        public boolean isExcluded() {
            return excluded;
        }

        public long getDelivered() {
            return delivered;
        }
    }

    private BlockPos nodePos;
    private boolean powered;
    private boolean active;
    private long lastTickOutput;
    private final List<TargetInfo> targets = new ArrayList<>();

    public PacketChunkPowerNodeSync() {
    }

    public PacketChunkPowerNodeSync(BlockPos nodePos, boolean powered, boolean active,
                                    long lastTickOutput, List<TargetInfo> targets) {
        this.nodePos = nodePos;
        this.powered = powered;
        this.active = active;
        this.lastTickOutput = lastTickOutput;
        this.targets.addAll(targets);
    }

    public BlockPos getNodePos() {
        return nodePos;
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isActive() {
        return active;
    }

    public long getLastTickOutput() {
        return lastTickOutput;
    }

    public List<TargetInfo> getTargets() {
        return targets;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.nodePos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.powered = buf.readBoolean();
        this.active = buf.readBoolean();
        this.lastTickOutput = buf.readLong();
        int count = buf.readShort();
        targets.clear();
        for (int i = 0; i < count; i++) {
            BlockPos pos = BlockPos.fromLong(buf.readLong());
            net.minecraft.item.ItemStack display = ByteBufUtils.readItemStack(buf);
            String fallbackKey = ByteBufUtils.readUTF8String(buf);
            boolean excluded = buf.readBoolean();
            long delivered = buf.readLong();
            targets.add(new TargetInfo(pos, display, fallbackKey, excluded, delivered));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(nodePos.getX());
        buf.writeInt(nodePos.getY());
        buf.writeInt(nodePos.getZ());
        buf.writeBoolean(powered);
        buf.writeBoolean(active);
        buf.writeLong(lastTickOutput);
        buf.writeShort(targets.size());
        for (TargetInfo t : targets) {
            buf.writeLong(t.getPos().toLong());
            ByteBufUtils.writeItemStack(buf, t.getDisplay());
            ByteBufUtils.writeUTF8String(buf, t.getFallbackKey());
            buf.writeBoolean(t.isExcluded());
            buf.writeLong(t.getDelivered());
        }
    }

    public static class Handler implements IMessageHandler<PacketChunkPowerNodeSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketChunkPowerNodeSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world == null) return;
                TileEntity te = Minecraft.getMinecraft().world.getTileEntity(message.getNodePos());
                if (te instanceof TileChunkPowerNode) {
                    ((TileChunkPowerNode) te).handleSyncPacket(message);
                }
            });
            return null;
        }
    }
}
