package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class PacketPlacementCablePlace implements IMessage {

    private BlockPos start;
    private BlockPos end;

    public PacketPlacementCablePlace() {
    }

    public PacketPlacementCablePlace(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        start = BlockPos.fromLong(buf.readLong());
        end = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(start.toLong());
        buf.writeLong(end.toLong());
    }

    public BlockPos getStart() {
        return start;
    }

    public BlockPos getEnd() {
        return end;
    }
}
