package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 选取交互卡的中键提取请求.
 * 客户端发送目标物品的序列化数据,服务器执行 SHIFT_CLICK 等效提取.
 */
public class PacketPickerAction implements IMessage {

    private ItemStack definition;
    private long stackSize;
    private boolean craftable;

    public PacketPickerAction() {
    }

    public PacketPickerAction(IAEItemStack stack) {
        this.definition = stack.getDefinition().copy();
        this.stackSize = stack.getStackSize();
        this.craftable = stack.isCraftable();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.definition = net.minecraftforge.fml.common.network.ByteBufUtils.readItemStack(buf);
        this.stackSize = buf.readLong();
        this.craftable = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        net.minecraftforge.fml.common.network.ByteBufUtils.writeItemStack(buf, this.definition);
        buf.writeLong(this.stackSize);
        buf.writeBoolean(this.craftable);
    }

    public static class Handler implements IMessageHandler<PacketPickerAction, IMessage> {

        @Override
        public IMessage onMessage(PacketPickerAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerOmniTerm)) {
                    return;
                }
                ContainerOmniTerm container = (ContainerOmniTerm) player.openContainer;
                if (container.getPowerSource() == null || container.getCellInventory() == null) {
                    return;
                }

                IAEItemStack ais = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                        .createStack(message.definition);
                if (ais == null) {
                    return;
                }
                ais.setStackSize(message.stackSize);

                // 模拟玩家背包可容纳数量
                ItemStack myItem = ais.createItemStack();
                ais.setStackSize(myItem.getMaxStackSize());
                InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player);
                myItem.setCount((int) ais.getStackSize());
                myItem = adp.simulateAdd(myItem);
                if (!myItem.isEmpty()) {
                    ais.setStackSize(ais.getStackSize() - (long) myItem.getCount());
                }

                // 执行提取
                IAEItemStack extracted = Platform.poweredExtraction(
                        container.getPowerSource(),
                        container.getCellInventory(),
                        ais,
                        container.getActionSource()
                );
                if (extracted != null) {
                    adp.addItems(extracted.createItemStack());
                }
            });
            return null;
        }
    }
}
