package com.github.aeddddd.ae2enhanced.util.network;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 无线频道连接的定时验证任务.
 *
 * <p>每 {@link AE2EnhancedConfig.WirelessChannel#reconnectIntervalTicks} tick
 * 扫描一次 {@link WirelessChannelConnectionHelper} 中缓存的所有远程连接,
 * 销毁已失效的连接.失效连接会在下一次有效事件(升级槽变化、chunk 加载、
 * 世界重载)触发 {@code tryConnect} 时自动重建.</p>
 */
public class WirelessChannelTickHandler {

    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int interval = AE2EnhancedConfig.wirelessChannel.reconnectIntervalTicks;
        if (interval <= 0) return;

        tickCounter++;
        if (tickCounter >= interval) {
            tickCounter = 0;
            WirelessChannelConnectionHelper.validateAllConnections();
        }
    }
}
