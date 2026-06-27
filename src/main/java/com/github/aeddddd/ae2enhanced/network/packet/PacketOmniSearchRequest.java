package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import io.netty.buffer.ByteBuf;
import net.minecraft.inventory.Container;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 → 服务端：Omni Terminal 搜索请求
 *
 * <p>客户端输入搜索词时发送，服务端利用超维度仓储的预构建索引快速筛选，
 * 返回匹配结果，避免客户端遍历大规模物品列表。
 */
public class PacketOmniSearchRequest implements IMessage {

    private String query = "";
    private boolean isModSearch = false;
    private int viewModeOrdinal = 0;
    private int sortByOrdinal = 0;
    private int sortDirOrdinal = 0;
    private int limit = 500;

    public PacketOmniSearchRequest() {
    }

    public PacketOmniSearchRequest(String query, boolean isModSearch,
                                   int viewModeOrdinal, int sortByOrdinal, int sortDirOrdinal, int limit) {
        this.query = query != null ? query : "";
        this.isModSearch = isModSearch;
        this.viewModeOrdinal = viewModeOrdinal;
        this.sortByOrdinal = sortByOrdinal;
        this.sortDirOrdinal = sortDirOrdinal;
        this.limit = limit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = buf.readShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        this.query = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        this.isModSearch = buf.readBoolean();
        this.viewModeOrdinal = buf.readByte();
        this.sortByOrdinal = buf.readByte();
        this.sortDirOrdinal = buf.readByte();
        this.limit = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        byte[] bytes = this.query.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
        buf.writeBoolean(this.isModSearch);
        buf.writeByte(this.viewModeOrdinal);
        buf.writeByte(this.sortByOrdinal);
        buf.writeByte(this.sortDirOrdinal);
        buf.writeShort(this.limit);
    }

    public String getQuery() {
        return query;
    }

    public boolean isModSearch() {
        return isModSearch;
    }

    public int getViewModeOrdinal() {
        return viewModeOrdinal;
    }

    public int getSortByOrdinal() {
        return sortByOrdinal;
    }

    public int getSortDirOrdinal() {
        return sortDirOrdinal;
    }

    public int getLimit() {
        return limit;
    }

    public static class Handler implements IMessageHandler<PacketOmniSearchRequest, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniSearchRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;
                if (container instanceof ContainerOmniTerm) {
                    ((ContainerOmniTerm) container).handleSearchRequest(message);
                }
            });
            return null;
        }
    }
}
