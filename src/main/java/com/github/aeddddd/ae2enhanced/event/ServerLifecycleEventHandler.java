package com.github.aeddddd.ae2enhanced.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.structure.ControllerIndex;

/**
 * 服务器生命周期事件处理器。
 * <p>负责在关服前安全地刷新所有持久化数据，避免数据丢失；
 * 超维度仓储的常规 flush 由 {@link HyperdimensionalControllerBlockEntity#serverTick}
 * 按配置间隔执行，文件 I/O 已异步化，不会阻塞主线程。关服时先提交所有脏 section 的写入任务，
 * 再关闭异步执行器并等待其完成，确保数据落盘。</p>
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
        // 关闭异步 I/O 执行器，等待所有已提交的写入任务完成，防止数据丢失。
        HyperdimensionalStorageFile.shutdown();
    }
}
