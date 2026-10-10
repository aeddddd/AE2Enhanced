package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：中枢 ME 接口的绑定描边数据（只有接口坐标 + 绑定坐标列表）.
 *
 * <p>客户端消费方只需要"哪个接口绑了哪些坐标"：</p>
 * <ul>
 *   <li>{@code client/render/BindingLineRenderer} 画源/目标描边</li>
 *   <li>Universal Memory Card 的 tooltip 显示绑定数量</li>
 * </ul>
 *
 * <p>绑定变更时由 {@link TileCentralMEInterface} 发给正在跟踪该方块所在区块的玩家；
 * 区块加载时（玩家进入视距/重新登录）由瘦身后的 {@code getUpdateTag} 携带同一份数据，
 * 因此本包只承担"跟踪期间的增量刷新"。</p>
 *
 * <p>载荷中不含 blockId：描边与计数都不使用它，快照里的 {@code blockId} 为 null。</p>
 */
public class PacketCentralBindingOutline implements IMessage {

    private int dimension;
    private long interfacePos;
    private final List<TargetBinding> bindings = new ArrayList<>();

    public PacketCentralBindingOutline() {
    }

    public PacketCentralBindingOutline(int dimension, BlockPos interfacePos, List<TargetBinding> bindings) {
        this.dimension = dimension;
        this.interfacePos = interfacePos.toLong();
        this.bindings.addAll(bindings);
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getInterfacePos() {
        return BlockPos.fromLong(interfacePos);
    }

    public List<TargetBinding> getBindings() {
        return bindings;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        bindings.clear();
        dimension = buf.readInt();
        interfacePos = buf.readLong();
        int size = ByteBufUtils.readVarInt(buf, 5);
        for (int i = 0; i < size; i++) {
            int bindingDim = buf.readInt();
            long bindingPos = buf.readLong();
            bindings.add(new TargetBinding(BlockPos.fromLong(bindingPos), bindingDim, null));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeLong(interfacePos);
        ByteBufUtils.writeVarInt(buf, bindings.size(), 5);
        for (TargetBinding binding : bindings) {
            buf.writeInt(binding.dimension);
            buf.writeLong(binding.pos.toLong());
        }
    }

    public static class Handler implements IMessageHandler<PacketCentralBindingOutline, IMessage> {

        @Override
        public IMessage onMessage(PacketCentralBindingOutline message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketCentralBindingOutline message) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world == null) {
                return;
            }
            // 玩家不在该维度时本机没有对应方块实体，直接丢弃；
            // 玩家稍后进入该维度时会随区块加载拿到同一份数据。
            if (mc.world.provider.getDimension() != message.getDimension()) {
                return;
            }
            TileEntity te = mc.world.getTileEntity(message.getInterfacePos());
            if (te instanceof TileCentralMEInterface) {
                ((TileCentralMEInterface) te).applyBindingSnapshot(message.getBindings());
            }
        }
    }
}
