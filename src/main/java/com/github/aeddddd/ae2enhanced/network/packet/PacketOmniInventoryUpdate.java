package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.GuiOmniTerm;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 服务端 → 客户端：Omni Terminal 物品列表同步包
 *
 * <p>四种模式：
 * <ul>
 *   <li>FULL_INIT —— 首次全量同步第一批（客户端应清空旧数据）</li>
 *   <li>FULL_CONTINUE —— 全量同步后续批次（客户端应追加）</li>
 *   <li>ITEM_REGISTER —— 新物品种类注册（发送 definition）</li>
 *   <li>DELTA_COUNT —— 差量同步（仅发送 id + count，12 字节/物品）</li>
 * </ul>
 */
public class PacketOmniInventoryUpdate implements IMessage {

    public enum Mode {
        FULL_INIT,
        FULL_CONTINUE,
        FULL_END,
        ITEM_REGISTER,
        DELTA_COUNT
    }

    public static class Entry {
        public int id;
        public IAEItemStack stack; // FULL_INIT / FULL_CONTINUE / ITEM_REGISTER 时非 null；DELTA_COUNT 时为 null
        public long count;

        public Entry() {
        }

        public Entry(int id, IAEItemStack stack, long count) {
            this.id = id;
            this.stack = stack;
            this.count = count;
        }
    }

    private Mode mode;
    private List<Entry> entries = new ArrayList<>();

    public PacketOmniInventoryUpdate() {
    }

    public PacketOmniInventoryUpdate(Mode mode, List<Entry> entries) {
        this.mode = mode;
        this.entries = entries != null ? entries : Collections.emptyList();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.mode = Mode.values()[buf.readByte()];
        int count = buf.readInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = buf.readInt();
            long c = buf.readLong();
            boolean hasStack = buf.readBoolean();
            IAEItemStack stack = null;
            if (hasStack) {
                try {
                    stack = AEItemStack.fromPacket(buf);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to read stack from OmniInventoryUpdate", e);
                }
            }
            this.entries.add(new Entry(id, stack, c));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.mode.ordinal());
        buf.writeInt(this.entries.size());
        for (Entry e : this.entries) {
            buf.writeInt(e.id);
            buf.writeLong(e.count);
            boolean hasStack = (e.stack != null);
            buf.writeBoolean(hasStack);
            if (hasStack) {
                try {
                    e.stack.writeToPacket(buf);
                } catch (Exception ex) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to write stack to OmniInventoryUpdate", ex);
                }
            }
        }
    }

    public Mode getMode() {
        return this.mode;
    }

    public List<Entry> getEntries() {
        return this.entries;
    }

    public static class Handler implements IMessageHandler<PacketOmniInventoryUpdate, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniInventoryUpdate message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen gui = Minecraft.getMinecraft().currentScreen;
                if (gui instanceof GuiOmniTerm) {
                    ((GuiOmniTerm) gui).handleOmniInventoryUpdate(message.getMode(), message.getEntries());
                }
            });
            return null;
        }
    }
}
