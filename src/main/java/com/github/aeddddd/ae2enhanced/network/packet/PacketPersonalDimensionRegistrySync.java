package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端的个人维度注册表同步包。
 *
 * <p>参考 PersonalWorlds：客户端必须在玩家进入维度前预先通过
 * {@link DimensionManager#registerDimension(int, net.minecraft.world.DimensionType)}
 * 注册好个人维度 ID，否则客户端收到跨维度重生包时会因
 * "Could not get provider type for dimension X, does not exist" 崩溃。</p>
 */
public class PacketPersonalDimensionRegistrySync implements IMessage {

    private final List<Integer> dimensionIds = new ArrayList<>();

    public PacketPersonalDimensionRegistrySync() {
    }

    public PacketPersonalDimensionRegistrySync(List<Integer> dimensionIds) {
        this.dimensionIds.addAll(dimensionIds);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimensionIds.clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            dimensionIds.add(buf.readInt());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimensionIds.size());
        for (int id : dimensionIds) {
            buf.writeInt(id);
        }
    }

    public static class Handler implements IMessageHandler<PacketPersonalDimensionRegistrySync, IMessage> {

        @Override
        public IMessage onMessage(PacketPersonalDimensionRegistrySync message, MessageContext ctx) {
            scheduleRegister(message.dimensionIds);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void scheduleRegister(List<Integer> ids) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.world.DimensionType type = PersonalDimensionManager.getDimensionType();
                if (type == null) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Client personal dimension type not registered, cannot sync dimension registry");
                    return;
                }
                for (int dimId : ids) {
                    if (!DimensionManager.isDimensionRegistered(dimId)) {
                        try {
                            DimensionManager.registerDimension(dimId, type);
                        } catch (Exception e) {
                            AE2Enhanced.LOGGER.warn("[AE2E] Failed to register personal dimension {} on client", dimId, e);
                        }
                    }
                }
            });
        }
    }
}
