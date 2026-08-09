package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.client.gui.GuiSingularityChamber;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
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
 * S→C 奇点处理仓状态同步：能量/并行通道/任务数/输入输出缓存内容（long 数量）.
 * 数据直接投递到打开的 GUI,不走 Tile（客户端 Tile 无缓存副本）.
 */
public class PacketChamberSync implements IMessage {

    private BlockPos pos;
    private int energy;
    private long parallelChannels;
    private long usedChannels;
    private int activeJobs;
    private int redstoneMode;
    private List<String> disabledRecipes = new ArrayList<>();
    private List<ItemStack> inputItems = new ArrayList<>();
    private List<Long> inputCounts = new ArrayList<>();
    private List<ItemStack> outputItems = new ArrayList<>();
    private List<Long> outputCounts = new ArrayList<>();

    public PacketChamberSync() {
    }

    public PacketChamberSync(BlockPos pos, int energy, long parallelChannels, long usedChannels, int activeJobs,
                             int redstoneMode, java.util.Collection<String> disabledRecipes) {
        this.pos = pos;
        this.energy = energy;
        this.parallelChannels = parallelChannels;
        this.usedChannels = usedChannels;
        this.activeJobs = activeJobs;
        this.redstoneMode = redstoneMode;
        this.disabledRecipes.addAll(disabledRecipes);
    }

    public void addInput(ItemStack stack, long count) {
        inputItems.add(stack);
        inputCounts.add(count);
    }

    public void addOutput(ItemStack stack, long count) {
        outputItems.add(stack);
        outputCounts.add(count);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getEnergy() {
        return energy;
    }

    public long getParallelChannels() {
        return parallelChannels;
    }

    public long getUsedChannels() {
        return usedChannels;
    }

    public int getActiveJobs() {
        return activeJobs;
    }

    public int getRedstoneMode() {
        return redstoneMode;
    }

    public List<String> getDisabledRecipes() {
        return disabledRecipes;
    }

    public List<ItemStack> getInputItems() {
        return inputItems;
    }

    public List<Long> getInputCounts() {
        return inputCounts;
    }

    public List<ItemStack> getOutputItems() {
        return outputItems;
    }

    public List<Long> getOutputCounts() {
        return outputCounts;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        energy = buf.readInt();
        parallelChannels = buf.readLong();
        usedChannels = buf.readLong();
        activeJobs = buf.readInt();
        redstoneMode = buf.readInt();
        int disabled = buf.readInt();
        for (int i = 0; i < disabled; i++) {
            disabledRecipes.add(ByteBufUtils.readUTF8String(buf));
        }
        int inCount = buf.readInt();
        for (int i = 0; i < inCount; i++) {
            inputItems.add(ByteBufUtils.readItemStack(buf));
            inputCounts.add(buf.readLong());
        }
        int outCount = buf.readInt();
        for (int i = 0; i < outCount; i++) {
            outputItems.add(ByteBufUtils.readItemStack(buf));
            outputCounts.add(buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeInt(energy);
        buf.writeLong(parallelChannels);
        buf.writeLong(usedChannels);
        buf.writeInt(activeJobs);
        buf.writeInt(redstoneMode);
        buf.writeInt(disabledRecipes.size());
        for (String id : disabledRecipes) {
            ByteBufUtils.writeUTF8String(buf, id);
        }
        buf.writeInt(inputItems.size());
        for (int i = 0; i < inputItems.size(); i++) {
            ByteBufUtils.writeItemStack(buf, inputItems.get(i));
            buf.writeLong(inputCounts.get(i));
        }
        buf.writeInt(outputItems.size());
        for (int i = 0; i < outputItems.size(); i++) {
            ByteBufUtils.writeItemStack(buf, outputItems.get(i));
            buf.writeLong(outputCounts.get(i));
        }
    }

    public static class Handler implements IMessageHandler<PacketChamberSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketChamberSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiSingularityChamber) {
                    ((GuiSingularityChamber) screen).acceptSync(message);
                }
            });
            return null;
        }
    }
}
