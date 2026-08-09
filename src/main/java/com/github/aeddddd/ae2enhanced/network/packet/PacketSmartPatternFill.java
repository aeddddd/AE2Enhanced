package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * 智能样板接口：JEI 一键转移,整体覆盖当前锁定配方的输入输出.
 *
 * <p>输入上限 81 槽(9组 x 3x3),输出上限 9 槽(3x3),超出部分服务端截断.</p>
 */
public class PacketSmartPatternFill implements IMessage {

    private static final int MAX_INPUTS = 81;
    private static final int MAX_OUTPUTS = 9;

    private long pos;
    private NBTTagList inputs;
    private NBTTagList outputs;

    public PacketSmartPatternFill() {
    }

    public PacketSmartPatternFill(BlockPos pos, List<IAEItemStack> inputs, List<IAEItemStack> outputs) {
        this.pos = pos.toLong();
        this.inputs = writeList(inputs, MAX_INPUTS);
        this.outputs = writeList(outputs, MAX_OUTPUTS);
    }

    private static NBTTagList writeList(List<IAEItemStack> stacks, int max) {
        NBTTagList list = new NBTTagList();
        int count = Math.min(stacks.size(), max);
        for (int i = 0; i < count; i++) {
            IAEItemStack stack = stacks.get(i);
            NBTTagCompound tag = new NBTTagCompound();
            if (stack != null) {
                ((AEItemStack) stack).writeToNBT(tag);
            }
            list.appendTag(tag);
        }
        return list;
    }

    private static IAEItemStack[] readList(NBTTagList list) {
        IAEItemStack[] stacks = new IAEItemStack[list.tagCount()];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            stacks[i] = tag.getKeySet().isEmpty() ? null : AEItemStack.fromNBT(tag);
        }
        return stacks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.inputs = (NBTTagList) ByteBufUtils.readTag(buf).getTag("inputs");
        this.outputs = (NBTTagList) ByteBufUtils.readTag(buf).getTag("outputs");
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        NBTTagCompound in = new NBTTagCompound();
        in.setTag("inputs", inputs);
        ByteBufUtils.writeTag(buf, in);
        NBTTagCompound out = new NBTTagCompound();
        out.setTag("outputs", outputs);
        ByteBufUtils.writeTag(buf, out);
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternFill, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternFill message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                TileEntity te = world.getTileEntity(BlockPos.fromLong(message.pos));
                if (te instanceof TileSmartPatternInterface) {
                    ((TileSmartPatternInterface) te).fillLockedRecipe(
                            readList(message.inputs), readList(message.outputs));
                }
            });
            return null;
        }
    }
}
