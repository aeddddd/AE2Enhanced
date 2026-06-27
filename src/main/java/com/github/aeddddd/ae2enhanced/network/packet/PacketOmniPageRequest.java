package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 客户端 -> 服务端：Omni Terminal 分页请求。
 *
 * <p>R3 架构核心包。客户端不再维护完整列表，而是按需请求当前可见页的数据。
 * 服务端执行排序/搜索/过滤后，只返回请求范围内的物品。
 *
 * <p>新增：当 HEI/JEI 可用时，客户端会把 HEI 的搜索结果以 {@link ItemDescriptor} 列表形式发给服务端，
 * 从而支持 JECH/HECH 拼音搜索、tooltip 搜索等 HEI 原生语义。
 */
public class PacketOmniPageRequest implements IMessage {

    private String searchString = "";
    private byte searchMode;      // 0=NAME, 1=MOD, 2=TOOLTIP(已禁用)
    private byte sortBy;          // 0=NAME, 1=AMOUNT, 2=MOD, 3=INVTWEAKS
    private byte sortDir;         // 0=ASC, 1=DESC
    private byte viewMode;        // 0=STORED, 1=ALL, 2=CRAFTABLE
    private int offset;           // 起始位置（0-based）
    private int limit;            // 请求数量（通常 45 或 135）
    private List<ItemDescriptor> clientFilter = null; // 可选：HEI 搜索结果描述符

    public PacketOmniPageRequest() {
    }

    public PacketOmniPageRequest(String searchString, byte searchMode, byte sortBy,
                                  byte sortDir, byte viewMode, int offset, int limit) {
        this(searchString, searchMode, sortBy, sortDir, viewMode, offset, limit, null);
    }

    public PacketOmniPageRequest(String searchString, byte searchMode, byte sortBy,
                                  byte sortDir, byte viewMode, int offset, int limit,
                                  List<ItemDescriptor> clientFilter) {
        this.searchString = searchString != null ? searchString : "";
        this.searchMode = searchMode;
        this.sortBy = sortBy;
        this.sortDir = sortDir;
        this.viewMode = viewMode;
        this.offset = offset;
        this.limit = limit;
        this.clientFilter = clientFilter;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.searchString = ByteBufUtils.readUTF8String(buf);
        this.searchMode = buf.readByte();
        this.sortBy = buf.readByte();
        this.sortDir = buf.readByte();
        this.viewMode = buf.readByte();
        this.offset = buf.readInt();
        this.limit = buf.readInt();
        if (buf.readBoolean()) {
            int count = buf.readInt();
            this.clientFilter = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                NBTTagCompound tag = ByteBufUtils.readTag(buf);
                ItemDescriptor desc = tag != null ? ItemDescriptor.fromNBT(tag) : null;
                if (desc != null) {
                    this.clientFilter.add(desc);
                }
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.searchString);
        buf.writeByte(this.searchMode);
        buf.writeByte(this.sortBy);
        buf.writeByte(this.sortDir);
        buf.writeByte(this.viewMode);
        buf.writeInt(this.offset);
        buf.writeInt(this.limit);
        boolean hasFilter = this.clientFilter != null && !this.clientFilter.isEmpty();
        buf.writeBoolean(hasFilter);
        if (hasFilter) {
            buf.writeInt(this.clientFilter.size());
            for (ItemDescriptor desc : this.clientFilter) {
                ByteBufUtils.writeTag(buf, desc.toNBT());
            }
        }
    }

    public String getSearchString() { return searchString; }
    public byte getSearchMode() { return searchMode; }
    public byte getSortBy() { return sortBy; }
    public byte getSortDir() { return sortDir; }
    public byte getViewMode() { return viewMode; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public List<ItemDescriptor> getClientFilter() { return clientFilter; }

    public static class Handler implements IMessageHandler<PacketOmniPageRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniPageRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;
                if (container instanceof ContainerOmniTerm) {
                    ((ContainerOmniTerm) container).handlePageRequest(message);
                }
            });
            return null;
        }
    }
}
