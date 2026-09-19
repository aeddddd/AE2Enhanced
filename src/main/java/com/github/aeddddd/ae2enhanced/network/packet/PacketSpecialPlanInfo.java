package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.LinkedHashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;

/**
 * 服务端 → 客户端的特殊计划显示信息同步包.
 * 由 LP 计划 job 求解成功后发送,客户端缓存供合成确认界面 tooltip 使用.
 */
public class PacketSpecialPlanInfo implements IMessage {

    private IAEItemStack output;
    private SpecialPlanInfo info;

    public PacketSpecialPlanInfo() {
    }

    public PacketSpecialPlanInfo(IAEItemStack output, SpecialPlanInfo info) {
        this.output = output.copy();
        this.info = info;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // 键一律写 getDefinition()(count=1):canon 键 stackSize=0,createItemStack
        // 会产生空栈导致 fromItemStack 返回 null;请求量 >127 时 count 还会 NBT 字节溢出
        ByteBufUtils.writeItemStack(buf, output.getDefinition());
        ByteBufUtils.writeVarInt(buf, info.entries.size(), 5);
        for (Map.Entry<IAEItemStack, SpecialPlanInfo.Entry> e : info.entries.entrySet()) {
            ByteBufUtils.writeItemStack(buf, e.getKey().getDefinition());
            SpecialPlanInfo.Entry entry = e.getValue();
            buf.writeByte(entry.kind);
            buf.writeLong(entry.rounds);
            buf.writeLong(entry.perRoundProduce);
            buf.writeLong(entry.perRoundConsume);
            buf.writeLong(entry.totalCrafts);
            buf.writeLong(entry.initialExtract);
        }
        ByteBufUtils.writeVarInt(buf, info.callCounts.size(), 5);
        for (Map.Entry<IAEItemStack, Long> e : info.callCounts.entrySet()) {
            ByteBufUtils.writeItemStack(buf, e.getKey().getDefinition());
            buf.writeLong(e.getValue());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        ItemStack outStack = ByteBufUtils.readItemStack(buf);
        this.output = AEItemStack.fromItemStack(outStack);
        Map<IAEItemStack, SpecialPlanInfo.Entry> entries = new LinkedHashMap<>();
        int entrySize = ByteBufUtils.readVarInt(buf, 5);
        for (int i = 0; i < entrySize; i++) {
            IAEItemStack key = AEItemStack.fromItemStack(ByteBufUtils.readItemStack(buf));
            entries.put(key, new SpecialPlanInfo.Entry(buf.readByte(), buf.readLong(), buf.readLong(),
                    buf.readLong(), buf.readLong(), buf.readLong()));
        }
        Map<IAEItemStack, Long> callCounts = new LinkedHashMap<>();
        int callCountSize = ByteBufUtils.readVarInt(buf, 5);
        for (int i = 0; i < callCountSize; i++) {
            IAEItemStack key = AEItemStack.fromItemStack(ByteBufUtils.readItemStack(buf));
            callCounts.put(key, buf.readLong());
        }
        this.info = new SpecialPlanInfo(entries, callCounts);
    }

    public static class Handler implements IMessageHandler<PacketSpecialPlanInfo, IMessage> {
        @Override
        public IMessage onMessage(PacketSpecialPlanInfo message, MessageContext ctx) {
            scheduleUpdate(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void scheduleUpdate(PacketSpecialPlanInfo message) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
                    com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanClientCache
                            .update(message.info));
        }
    }
}
