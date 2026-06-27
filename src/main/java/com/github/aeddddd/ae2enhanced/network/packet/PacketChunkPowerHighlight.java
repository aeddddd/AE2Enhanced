package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：区块供电节点高亮目标位置列表.
 */
public class PacketChunkPowerHighlight implements IMessage {

    private final List<BlockPos> targets = new ArrayList<>();
    private int durationTicks;

    public PacketChunkPowerHighlight() {
    }

    public PacketChunkPowerHighlight(List<BlockPos> targets, int durationTicks) {
        this.targets.addAll(targets);
        this.durationTicks = durationTicks;
    }

    public List<BlockPos> getTargets() {
        return targets;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readShort();
        targets.clear();
        for (int i = 0; i < count; i++) {
            targets.add(BlockPos.fromLong(buf.readLong()));
        }
        durationTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(targets.size());
        for (BlockPos pos : targets) {
            buf.writeLong(pos.toLong());
        }
        buf.writeInt(durationTicks);
    }
}
