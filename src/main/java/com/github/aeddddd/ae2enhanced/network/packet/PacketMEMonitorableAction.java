package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * E2a：直接容器提取/注入网络包.
 * 客户端发送 IAEItemStack.getDefinition() 的完整 NBT,服务器端用 FakeItemRegister 解析.
 *
 * 设计参考 ae2fc CpacketMEMonitorableAction.
 */
public class PacketMEMonitorableAction implements IMessage {

    public static final byte FLUID_WORK = 0;       // 手持流体容器点击槽位(填充/排空)
    public static final byte GAS_WORK = 1;         // 手持气体容器点击槽位(填充/排空)
    public static final byte FLUID_OPERATE = 2;    // 空手点击流体槽位(获取装满的桶)
    public static final byte GAS_OPERATE = 3;      // 空手点击气体槽位(获取装满的气体容器)
    public static final byte RESOURCE_ACTION = 4;  // RF/Mana/Starlight 统一资源动作（无 TII fallback）

    private byte type;
    private NBTTagCompound nbt;

    public PacketMEMonitorableAction() {
    }

    public PacketMEMonitorableAction(byte type, NBTTagCompound nbt) {
        this.type = type;
        this.nbt = nbt;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = buf.readByte();
        this.nbt = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type);
        ByteBufUtils.writeTag(buf, this.nbt);
    }

    public byte getType() {
        return type;
    }

    public NBTTagCompound getNbt() {
        return nbt;
    }

    public static class Handler implements IMessageHandler<PacketMEMonitorableAction, IMessage> {

        @Override
        public IMessage onMessage(PacketMEMonitorableAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Container c = player.openContainer;
                if (!(c instanceof ContainerMEMonitorable)) {
                    return;
                }
                ContainerMEMonitorable cme = (ContainerMEMonitorable) c;
                if (cme.getNetworkNode() == null || cme.getNetworkNode().getGrid() == null) {
                    return;
                }
                IStorageGrid grid = cme.getNetworkNode().getGrid().getCache(IStorageGrid.class);
                Object target = cme.getTarget();
                if (!(target instanceof IActionHost)) {
                    return;
                }
                PlayerSource source = new PlayerSource(player, (IActionHost) target);

                ItemStack held = player.inventory.getItemStack();
                ItemStack ch = held.copy();
                ch.setCount(1);

                switch (message.getType()) {
                    case FLUID_WORK:
                        if (!held.isEmpty()) {
                            fluidWork(message, ch, grid, source, player);
                        }
                        break;
                    case GAS_WORK:
                        if (!held.isEmpty()) {
                            gasWorkReflect(message, ch, grid, source, player);
                        }
                        break;
                    case FLUID_OPERATE:
                        if (held.isEmpty()) {
                            fluidOperateWork(message, grid, source, player);
                        }
                        break;
                    case GAS_OPERATE:
                        if (held.isEmpty()) {
                            gasOperateWorkReflect(message, grid, source, player);
                        }
                        break;
                    case RESOURCE_ACTION:
                        com.github.aeddddd.ae2enhanced.terminal.UnifiedResourceTerminalServer.handle(
                                player, cme, grid, source, message.getNbt());
                        break;
                }
            });
            return null;
        }

        private static void fluidWork(PacketMEMonitorableAction message, ItemStack singleHeld, IStorageGrid grid,
                                       IActionSource source, EntityPlayerMP player) {
            ItemStack actualHeld = player.inventory.getItemStack();
            if (!ItemStack.areItemsEqual(singleHeld, actualHeld) || !ItemStack.areItemStackTagsEqual(singleHeld, actualHeld)
                    || actualHeld.isEmpty()) {
                return;
            }

            IFluidHandlerItem fh = FluidUtil.getFluidHandler(singleHeld);
            if (fh == null) return;

            FluidStack targetFluid = null;
            if (message.getNbt() != null && !message.getNbt().isEmpty()) {
                targetFluid = FluidStack.loadFluidStackFromNBT(message.getNbt());
            }
            if (targetFluid != null) {
                targetFluid.amount = Integer.MAX_VALUE;
            }

            IMEMonitor<IAEFluidStack> fluidStorage = grid.getInventory(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

            boolean drain = false;
            FluidStack allFluid = fh.drain(Integer.MAX_VALUE, false);

            if (targetFluid != null && allFluid != null && allFluid.amount > 0 && !allFluid.isFluidEqual(targetFluid)) {
                drain = true;
            } else if (targetFluid == null) {
                drain = true;
            }

            if (drain) {
                if (allFluid == null || allFluid.amount == 0) return;
                AEFluidStack allAEFluid = AEFluidStack.fromFluidStack(allFluid);
                if (allAEFluid == null) return;
                IAEFluidStack notInjected = fluidStorage.injectItems(allAEFluid, Actionable.SIMULATE, source);
                long size = allAEFluid.getStackSize() - (notInjected == null ? 0L : notInjected.getStackSize());
                if (size <= 0) return;
                fluidStorage.injectItems(allAEFluid.setStackSize(size), Actionable.MODULATE, source);
                fh.drain((int) size, true);
            } else {
                AEFluidStack targetAEFluid = AEFluidStack.fromFluidStack(targetFluid);
                IAEFluidStack extracted = fluidStorage.extractItems(targetAEFluid, Actionable.SIMULATE, source);
                if (extracted == null) return;
                int filled = fh.fill(extracted.getFluidStack(), false);
                if (filled <= 0) return;
                fluidStorage.extractItems(targetAEFluid.setStackSize(filled), Actionable.MODULATE, source);
                fh.fill(extracted.getFluidStack(), true);
            }

            // 处理手持多个物品的情况(复现 ae2fc 逻辑：placeItemBackInInventory)
            if (actualHeld.getCount() > 1) {
                actualHeld.shrink(1);
                ItemStack result = fh.getContainer();
                result.setCount(1);
                player.inventory.placeItemBackInInventory(player.world, result);
            } else {
                player.inventory.setItemStack(fh.getContainer());
            }
            updateHeld(player);
        }

        private static void gasWorkReflect(PacketMEMonitorableAction message, ItemStack singleHeld,
                                              IStorageGrid grid, PlayerSource source, EntityPlayerMP player) {
            try {
                Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableActionGasHelper");
                java.lang.reflect.Method method = helperClass.getMethod("gasWork",
                        PacketMEMonitorableAction.class, ItemStack.class,
                        IStorageGrid.class, PlayerSource.class, EntityPlayerMP.class);
                method.invoke(null, message, singleHeld, grid, source, player);
            } catch (Exception e) {
                AELog.error(e);
            }
        }

        private static void fluidOperateWork(PacketMEMonitorableAction message, IStorageGrid grid,
                                              IActionSource source, EntityPlayerMP player) {
            if (!player.inventory.getItemStack().isEmpty()) return;

            ItemStack definition = new ItemStack(message.getNbt());
            FluidStack fluid = FakeItemRegister.getStack(definition);
            if (fluid == null) return;
            fluid.amount = 1000;

            boolean shift = message.getNbt() != null && message.getNbt().getBoolean("shift");

            IMEMonitor<IAEItemStack> itemStorage = grid.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            IMEMonitor<IAEFluidStack> fluidStorage = grid.getInventory(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));

            IAEItemStack bucketReq = AEItemStack.fromItemStack(new ItemStack(Items.BUCKET));
            IAEItemStack bucket = itemStorage.extractItems(bucketReq, Actionable.SIMULATE, source);
            if (bucket == null) return;

            IAEFluidStack aeFluid = fluidStorage.extractItems(AEFluidStack.fromFluidStack(fluid), Actionable.SIMULATE, source);
            if (aeFluid == null || aeFluid.getStackSize() < 1000L) return;

            IFluidHandlerItem fh = FluidUtil.getFluidHandler(bucket.createItemStack());
            if (fh == null) return;
            int filled = fh.fill(aeFluid.getFluidStack(), false);
            if (filled != 1000) return;

            fh.fill(aeFluid.getFluidStack(), true); // 真正执行填充
            ItemStack out = fh.getContainer();
            if (shift) {
                int slot = player.inventory.getFirstEmptyStack();
                if (slot == -1) return;
                player.inventory.setInventorySlotContents(slot, out);
            } else {
                player.inventory.setItemStack(out);
            }

            itemStorage.extractItems(bucketReq, Actionable.MODULATE, source);
            fluidStorage.extractItems(AEFluidStack.fromFluidStack(fluid).setStackSize(1000), Actionable.MODULATE, source);
            updateHeld(player);
        }

        private static void gasOperateWorkReflect(PacketMEMonitorableAction message, IStorageGrid grid,
                                                     PlayerSource source, EntityPlayerMP player) {
            try {
                Class<?> helperClass = Class.forName("com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableActionGasHelper");
                java.lang.reflect.Method method = helperClass.getMethod("gasOperateWork",
                        PacketMEMonitorableAction.class, IStorageGrid.class, PlayerSource.class, EntityPlayerMP.class);
                method.invoke(null, message, grid, source, player);
            } catch (Exception e) {
                AELog.error(e);
            }
        }

        public static void updateHeld(EntityPlayerMP player) {
            if (Platform.isServer()) {
                try {
                    NetworkHandler.instance().sendTo(
                            new PacketInventoryAction(InventoryAction.UPDATE_HAND, 0,
                                    AEItemStack.fromItemStack(player.inventory.getItemStack())),
                            player);
                } catch (Exception e) {
                    AELog.debug(e);
                }
            }
        }
    }
}
