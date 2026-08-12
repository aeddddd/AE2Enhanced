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
 * 缓存内容同时写入客户端 Tile 的 store 镜像（虚拟槽位渲染/点击判定依赖它）,
 * 并投递到打开的 GUI 刷新计数覆盖层.
 */
public class PacketChamberSync implements IMessage {

    /** 活动任务视图：输出图标 + 批次数 + 进度 */
    public static class JobView {
        public final ItemStack output;
        public final long batches;
        public final long progress;
        public final long required;

        public JobView(ItemStack output, long batches, long progress, long required) {
            this.output = output;
            this.batches = batches;
            this.progress = progress;
            this.required = required;
        }

        public float fraction() {
            return required <= 0 ? 1.0f : Math.min(1.0f, (float) progress / (float) required);
        }
    }

    private BlockPos pos;
    private int energy;
    private long parallelChannels;
    private long usedChannels;
    private int activeJobs;
    private int redstoneMode;
    private List<ItemStack> inputItems = new ArrayList<>();
    private List<Long> inputCounts = new ArrayList<>();
    private List<ItemStack> outputItems = new ArrayList<>();
    private List<Long> outputCounts = new ArrayList<>();
    private List<JobView> jobs = new ArrayList<>();

    public PacketChamberSync() {
    }

    public PacketChamberSync(BlockPos pos, int energy, long parallelChannels, long usedChannels, int activeJobs,
                             int redstoneMode) {
        this.pos = pos;
        this.energy = energy;
        this.parallelChannels = parallelChannels;
        this.usedChannels = usedChannels;
        this.activeJobs = activeJobs;
        this.redstoneMode = redstoneMode;
    }

    public void addInput(ItemStack stack, long count) {
        inputItems.add(stack);
        inputCounts.add(count);
    }

    public void addOutput(ItemStack stack, long count) {
        outputItems.add(stack);
        outputCounts.add(count);
    }

    public void addJob(JobView job) {
        jobs.add(job);
    }

    public List<JobView> getJobs() {
        return jobs;
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
        int jobCount = buf.readInt();
        for (int i = 0; i < jobCount; i++) {
            ItemStack output = ByteBufUtils.readItemStack(buf);
            long batches = buf.readLong();
            long progress = buf.readLong();
            long required = buf.readLong();
            jobs.add(new JobView(output, batches, progress, required));
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
        buf.writeInt(jobs.size());
        for (JobView job : jobs) {
            ByteBufUtils.writeItemStack(buf, job.output);
            buf.writeLong(job.batches);
            buf.writeLong(job.progress);
            buf.writeLong(job.required);
        }
    }

    public static class Handler implements IMessageHandler<PacketChamberSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketChamberSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // 写入客户端 Tile 的缓存镜像,供虚拟槽位渲染与点击判定
                if (Minecraft.getMinecraft().world != null) {
                    net.minecraft.tileentity.TileEntity te =
                            Minecraft.getMinecraft().world.getTileEntity(message.pos);
                    if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber) {
                        com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber tile =
                                (com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber) te;
                        tile.getInputStore().replaceAll(message.getInputItems(), message.getInputCounts());
                        tile.getOutputStore().replaceAll(message.getOutputItems(), message.getOutputCounts());
                    }
                }
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiSingularityChamber) {
                    ((GuiSingularityChamber) screen).acceptSync(message);
                }
            });
            return null;
        }
    }
}
