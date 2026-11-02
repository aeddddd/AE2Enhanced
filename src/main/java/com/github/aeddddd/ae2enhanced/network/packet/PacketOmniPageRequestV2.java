package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.RequestContext;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/** 带容器会话、查询代次和请求序号的终端分页请求。 */
public final class PacketOmniPageRequestV2 implements IMessage {
    private final PacketOmniPageRequest page;
    private long containerSessionId;
    private long queryGeneration;
    private long requestId;

    public PacketOmniPageRequestV2() {
        this.page = new PacketOmniPageRequest();
    }

    public PacketOmniPageRequestV2(long containerSessionId, long queryGeneration, long requestId,
            String searchString, byte searchMode, byte sortBy, byte sortDir, byte viewMode,
            int offset, int limit, List<ItemDescriptor> clientFilter) {
        this.containerSessionId = containerSessionId;
        this.queryGeneration = queryGeneration;
        this.requestId = requestId;
        this.page = new PacketOmniPageRequest(searchString, searchMode, sortBy, sortDir,
                viewMode, offset, limit, clientFilter);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            containerSessionId = buf.readLong();
            queryGeneration = buf.readLong();
            requestId = buf.readLong();
            page.fromBytes(buf);
        } catch (RuntimeException ex) {
            containerSessionId = 0L;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(containerSessionId);
        buf.writeLong(queryGeneration);
        buf.writeLong(requestId);
        page.toBytes(buf);
    }

    public boolean isValid() {
        return containerSessionId != 0L && queryGeneration >= 0L && requestId > 0L && page.isValid();
    }

    public long getContainerSessionId() { return containerSessionId; }
    public long getQueryGeneration() { return queryGeneration; }
    public long getRequestId() { return requestId; }
    public String getSearchString() { return page.getSearchString(); }
    public byte getSearchMode() { return page.getSearchMode(); }
    public byte getSortBy() { return page.getSortBy(); }
    public byte getSortDir() { return page.getSortDir(); }
    public byte getViewMode() { return page.getViewMode(); }
    public int getOffset() { return page.getOffset(); }
    public int getLimit() { return page.getLimit(); }
    public List<ItemDescriptor> getClientFilter() { return page.getClientFilter(); }

    public static final class Handler implements IMessageHandler<PacketOmniPageRequestV2, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniPageRequestV2 message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ContainerOmniTerm container = RequestContext.omniTerminal(player,
                        message.getContainerSessionId());
                if (message.isValid() && container != null) {
                    container.handlePageRequest(message);
                }
            });
            return null;
        }
    }
}
