package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt;
import com.github.aeddddd.ae2enhanced.container.SplicedTermLayoutValidator;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 → 服务端：上报可拼接终端的布局（阶段 4a）。
 *
 * <p>客户端是布局的权威来源（阶段 4b 的编辑器也归客户端），服务端只做两件事：
 * <strong>校验</strong>（长度 + 能不能 decode）与<strong>落库</strong>（写进终端物品 NBT 的
 * {@code omni_layout}）。几何不参与任何服务端逻辑。</p>
 *
 * <p>写入目标不需要包里带定位信息：玩家此刻打开的容器就是 {@link ContainerSplicedTerm}，
 * 它的宿主物品栈（{@code WirelessTerminalGuiObject.getItemStack()}）正是这次 GUI 绑定那一份——
 * 比"按槽位重新查找"更不容易写错（同一玩家可能带着多把终端）。</p>
 */
public class PacketOmniLayout implements IMessage {

    private String layoutJson = "";

    public PacketOmniLayout() {
    }

    public PacketOmniLayout(String layoutJson) {
        this.layoutJson = layoutJson == null ? "" : layoutJson;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.layoutJson = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.layoutJson == null ? "" : this.layoutJson);
    }

    public static class Handler implements IMessageHandler<PacketOmniLayout, IMessage> {

        @Override
        public IMessage onMessage(PacketOmniLayout message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerSplicedTerm)) {
                    // 旧终端（GUI 11）与其它容器一律忽略：它们不写 omni_layout
                    return;
                }
                String json = message.layoutJson;
                if (!OmniTerminalLayoutNbt.isAcceptable(json)) {
                    AE2Enhanced.LOGGER.warn("拒绝过大的 Omni 终端布局载荷: {} 字节, 上限 {}",
                            OmniTerminalLayoutNbt.utf8Length(json), OmniTerminalLayoutNbt.MAX_LAYOUT_BYTES);
                    return;
                }
                if (!SplicedTermLayoutValidator.isValid(json)) {
                    // 校验器已经记过日志
                    return;
                }
                ContainerSplicedTerm container = (ContainerSplicedTerm) player.openContainer;
                ItemStack stack = container.getTerminalItemStack();
                if (!OmniTerminalLayoutNbt.write(stack, json)) {
                    AE2Enhanced.LOGGER.warn("写入终端物品 NBT 布局失败(载荷不可接受)");
                    return;
                }
                // 让容器把物品栈的新 NBT 同步回客户端（终端在玩家背包槽里时靠这一步）
                container.detectAndSendChanges();
            });
            return null;
        }
    }
}
