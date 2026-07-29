package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import appeng.menu.me.crafting.CraftAmountMenu;

import com.github.aeddddd.ae2enhanced.menu.CraftAmountMenuLongExt;

/**
 * 突破 AE2 int 上限的下单请求包（最大 9.2E / Long.MAX_VALUE）.
 * <p>AE2 原生下下单链路（CraftAmountScreen -> ConfirmAutoCraftPacket(int) ->
 * CraftAmountMenu.confirm(int) -> CraftConfirmMenu.planJob(int)）全部为 int,
 * 单次下单上限为 21.4 亿.本包携带 long 数量,由扩展方法
 * {@link CraftAmountMenuLongExt#ae2e$confirmLong} 以 long 语义复刻原生流程.</p>
 */
public class CraftAmountLongPacket implements ServerboundPacket {

    private final long amount;
    private final boolean craftMissingAmount;
    private final boolean autoStart;

    public CraftAmountLongPacket(long amount, boolean craftMissingAmount, boolean autoStart) {
        this.amount = amount;
        this.craftMissingAmount = craftMissingAmount;
        this.autoStart = autoStart;
    }

    public static CraftAmountLongPacket decode(FriendlyByteBuf buffer) {
        return new CraftAmountLongPacket(buffer.readVarLong(), buffer.readBoolean(), buffer.readBoolean());
    }

    public static void encode(CraftAmountLongPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarLong(packet.amount);
        buffer.writeBoolean(packet.craftMissingAmount);
        buffer.writeBoolean(packet.autoStart);
    }

    public static void handle(CraftAmountLongPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                packet.handleOnServer(player);
            }
        });
        context.setPacketHandled(true);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        if (player.level().isClientSide()) {
            return;
        }
        if (player.containerMenu instanceof CraftAmountMenu menu && amount > 0) {
            ((CraftAmountMenuLongExt) menu).ae2e$confirmLong(amount, craftMissingAmount, autoStart);
        }
    }
}
