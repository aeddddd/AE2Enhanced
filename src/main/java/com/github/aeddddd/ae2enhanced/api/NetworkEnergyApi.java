package com.github.aeddddd.ae2enhanced.api;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;

/**
 * 网络 RF 能源对外 API.
 *
 * <p>允许外部模组（如 MMCE-addition）以 {@link IActionHost} 的身份直接查询/消耗
 * 当前 AE 网络中存储的 RF（能源存储通道，1 stackSize = 1 RF），
 * 无需经过任何能源仓/能源总线方块。</p>
 *
 * <p>本类位于稳定的 {@code api} 包中，方法签名保持向后兼容；
 * 外部模组可通过反射调用以保持软依赖：
 * {@code Class.forName("com.github.aeddddd.ae2enhanced.api.NetworkEnergyApi")}.</p>
 */
public final class NetworkEnergyApi {

    private NetworkEnergyApi() {
    }

    /**
     * 查询宿主所在网络当前存储的 RF 总量.
     *
     * @param host 已接入网络的 AE 设备宿主（如 TileEntity）
     * @return 网络中可提取的 RF 总量；未接入网络或查询失败时返回 0
     */
    public static long getStoredEnergy(IActionHost host) {
        return extractEnergy(host, Long.MAX_VALUE, true);
    }

    /**
     * 从宿主所在网络提取 RF.
     *
     * <p>注意：与 AE2 存储语义一致，网络存量不足时可能发生部分提取，
     * 返回值为实际提取到的数量。需要"全有或全无"语义的调用方应先以
     * {@code simulate=true} 确认存量充足，再实际提取。</p>
     *
     * @param host     已接入网络的 AE 设备宿主
     * @param amount   要提取的 RF 数量（必须 &gt; 0）
     * @param simulate true = 仅模拟，不实际扣除
     * @return 实际（或模拟可）提取到的 RF 数量
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static long extractEnergy(IActionHost host, long amount, boolean simulate) {
        if (host == null || amount <= 0) {
            return 0;
        }
        try {
            IGridNode node = host.getActionableNode();
            if (node == null) {
                return 0;
            }
            IGrid grid = node.getGrid();
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) {
                return 0;
            }
            // 经 EnergyChannelResolver 解析当前生效的能量通道（兼容 Flux_Applied 外部通道）
            IStorageChannel channel = EnergyChannelResolver.getChannel();
            if (channel == null) {
                return 0;
            }
            IMEInventory inventory = storage.getInventory(channel);
            if (inventory == null) {
                return 0;
            }
            // 按当前生效通道建堆(Flux 通道下 createStack(Number) 会返回 null,必须走 resolver)
            IAEStack request = EnergyChannelResolver.createStack(amount);
            if (request == null) {
                return 0;
            }
            IActionSource source = new MachineSource(host);
            IAEStack extracted = (IAEStack) inventory.extractItems(request,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source);
            return extracted == null ? 0 : extracted.getStackSize();
        } catch (Exception e) {
            // 节点未就绪（GridAccessException）或通道未注册等情况：静默视为无能量
            return 0;
        }
    }
}
