package com.github.aeddddd.ae2enhanced.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;

/**
 * 服务器生命周期事件处理器。
 * <p>负责在关服前安全地刷新所有持久化数据，避免数据丢失或主线程因文件 I/O 阻塞。</p>
 * <p>注：超维度仓储的常规 flush 由 {@link HyperdimensionalControllerBlockEntity#serverTick}
 * 按配置间隔执行；此处仅在服务器停止前做一次最终兜底刷新。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerLifecycleEventHandler {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            ControllerIndex index = ControllerIndex.get(level);
            for (BlockPos pos : index.getAll()) {
                if (level.getBlockEntity(pos) instanceof HyperdimensionalControllerBlockEntity tile) {
                    tile.flushStorage();
                }
            }
        }
    }
}
