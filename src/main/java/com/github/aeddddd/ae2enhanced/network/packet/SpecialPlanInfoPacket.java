package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;

/**
 * 服务端 → 客户端:计划确认界面的特殊计划显示信息(自增殖/循环链轮次详情).
 * 普通计划发送 {@link SpecialPlanInfo#EMPTY} 以清空客户端缓存.
 */
public class SpecialPlanInfoPacket {

    private final SpecialPlanInfo info;

    public SpecialPlanInfoPacket(SpecialPlanInfo info) {
        this.info = info;
    }

    public static SpecialPlanInfoPacket decode(FriendlyByteBuf buffer) {
        return new SpecialPlanInfoPacket(SpecialPlanInfo.read(buffer));
    }

    public static void encode(SpecialPlanInfoPacket packet, FriendlyByteBuf buffer) {
        packet.info.write(buffer);
    }

    public static void handle(SpecialPlanInfoPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanClientCache
                        .set(packet.info)));
        context.setPacketHandled(true);
    }
}
