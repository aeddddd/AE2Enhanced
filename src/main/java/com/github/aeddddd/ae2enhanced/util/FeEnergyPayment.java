package com.github.aeddddd.ae2enhanced.util;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionHost;
import com.github.aeddddd.ae2enhanced.api.NetworkEnergyApi;

/**
 * FE 支付工具：先消耗 ME 网络中存储的 FE（能源存储通道，1 stackSize = 1 FE），
 * 不足部分再走 AE 能源网（1 AE = 2 FE，上取整避免换算截断导致差 1 FE 永远付不起）.
 *
 * <p>支付口径与奇点腔室（{@code TileSingularityChamber#tryPay} 的缺口补足分支）一致，
 * 供需要对 FE 计费的机器共用，避免各处重复实现换算与异常兜底。</p>
 *
 * <p>网络存量不足时可能只支付一部分（与 AE 存储语义一致）；需要「全有或全无」的调用方
 * 应先以 {@code simulate = true} 确认可付额度，再按需实际扣除。</p>
 */
public final class FeEnergyPayment {

    private FeEnergyPayment() {
    }

    /**
     * 支付一笔 FE.
     *
     * @param host     已接入网络的宿主（其可操作节点用于定位网络）
     * @param fe       需要的 FE 数量
     * @param simulate true = 仅模拟，不实际扣除
     * @return 实际（或模拟可）支付的 FE 数量，可能小于 {@code fe}
     */
    public static long pay(IActionHost host, long fe, boolean simulate) {
        if (host == null || fe <= 0L) {
            return 0L;
        }
        long fromNetwork = NetworkEnergyApi.extractEnergy(host, fe, simulate);
        if (fromNetwork >= fe) {
            return fe;
        }
        return fromNetwork + fromAeGrid(host, fe - fromNetwork, simulate);
    }

    /** 从 AE 能源网提取，返回到手的 FE。 */
    private static long fromAeGrid(IActionHost host, long fe, boolean simulate) {
        if (fe <= 0L) {
            return 0L;
        }
        try {
            IGridNode node = host.getActionableNode();
            if (node == null) {
                return 0L;
            }
            IGrid grid = node.getGrid();
            if (grid == null) {
                return 0L;
            }
            IEnergyGrid energy = grid.getCache(IEnergyGrid.class);
            if (energy == null) {
                return 0L;
            }
            double aeNeeded = Math.ceil(PowerUnits.RF.convertTo(PowerUnits.AE, fe));
            double pulled = energy.extractAEPower(aeNeeded,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, PowerMultiplier.CONFIG);
            return (long) PowerUnits.AE.convertTo(PowerUnits.RF, pulled);
        } catch (Exception ignored) {
            // 未联网 / 节点未就绪 / 网络无能源网：视为无电
            return 0L;
        }
    }
}
