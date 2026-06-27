package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端向客户端发送虚拟合成粒子效果数据。
 *
 * <p>每个包可包含多个目标位置，每个位置附带粒子类型与数量，
 * 由客户端在本机生成粒子，避免服务端 spawn 带来的同步开销。</p>
 */
public class PacketVirtualCraftingParticles implements IMessage {

    public static class ParticleTarget {
        public final BlockPos pos;
        public final int particleType;
        public final int count;
        public final int color;

        public ParticleTarget(BlockPos pos, int particleType, int count, int color) {
            this.pos = pos;
            this.particleType = particleType;
            this.count = count;
            this.color = color;
        }
    }

    private final List<ParticleTarget> targets = new ArrayList<>();

    public PacketVirtualCraftingParticles() {
    }

    public PacketVirtualCraftingParticles(List<ParticleTarget> targets) {
        this.targets.addAll(targets);
    }

    public List<ParticleTarget> getTargets() {
        return targets;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        targets.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            long posLong = buf.readLong();
            int particleType = buf.readInt();
            int count = buf.readInt();
            int color = buf.readInt();
            targets.add(new ParticleTarget(BlockPos.fromLong(posLong), particleType, count, color));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targets.size());
        for (ParticleTarget target : targets) {
            buf.writeLong(target.pos.toLong());
            buf.writeInt(target.particleType);
            buf.writeInt(target.count);
            buf.writeInt(target.color);
        }
    }

    public static class Handler implements IMessageHandler<PacketVirtualCraftingParticles, IMessage> {
        @Override
        public IMessage onMessage(PacketVirtualCraftingParticles message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketVirtualCraftingParticles message) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world == null || mc.player == null) {
                return;
            }
            for (ParticleTarget target : message.getTargets()) {
                EnumParticleTypes particleType = EnumParticleTypes.getParticleFromId(target.particleType);
                if (particleType == null) {
                    particleType = EnumParticleTypes.PORTAL;
                }
                spawnParticles(mc, target.pos, particleType, target.count, target.color);
            }
        }

        @SideOnly(Side.CLIENT)
        private void spawnParticles(Minecraft mc, BlockPos pos, EnumParticleTypes type, int count, int color) {
            double baseX = pos.getX() + 0.5;
            double baseY = pos.getY() + 0.5;
            double baseZ = pos.getZ() + 0.5;
            for (int i = 0; i < count; i++) {
                double offsetX = (mc.world.rand.nextDouble() - 0.5) * 1.2;
                double offsetY = (mc.world.rand.nextDouble() - 0.5) * 1.2;
                double offsetZ = (mc.world.rand.nextDouble() - 0.5) * 1.2;
                double x = baseX + offsetX;
                double y = baseY + offsetY;
                double z = baseZ + offsetZ;
                double motionX = (mc.world.rand.nextDouble() - 0.5) * 0.05;
                double motionY = (mc.world.rand.nextDouble() - 0.5) * 0.05 + 0.05;
                double motionZ = (mc.world.rand.nextDouble() - 0.5) * 0.05;

                if (type == EnumParticleTypes.REDSTONE) {
                    float r = ((color >> 16) & 0xFF) / 255.0f;
                    float g = ((color >> 8) & 0xFF) / 255.0f;
                    float b = (color & 0xFF) / 255.0f;
                    mc.world.spawnParticle(type, x, y, z, r, g, b);
                } else {
                    mc.world.spawnParticle(type, x, y, z, motionX, motionY, motionZ);
                }
            }
        }
    }
}
