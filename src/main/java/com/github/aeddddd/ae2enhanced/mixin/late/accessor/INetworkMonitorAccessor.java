package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import appeng.api.networking.security.IActionSource;
import appeng.me.cache.NetworkMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * NetworkMonitor 私有/包私有成员访问接口.
 * 替代原先的 MixinReflectionHelper 与各 Tile 中的反射缓存.
 * 目标为 AE2 本家类, 始终存在, remap=false 使用源码名.
 */
@Mixin(value = NetworkMonitor.class, remap = false)
public interface INetworkMonitorAccessor {

    @Invoker("notifyListenersOfChange")
    @SuppressWarnings("rawtypes")
    void ae2e$notifyListenersOfChange(Iterable diff, IActionSource src);

    @Invoker("postChange")
    @SuppressWarnings("rawtypes")
    void ae2e$postChange(boolean add, Iterable changes, IActionSource src);

    @Invoker("forceUpdate")
    void ae2e$forceUpdate();

    @Invoker("onTick")
    void ae2e$onTick();

    @Accessor("sendEvent")
    void ae2e$setSendEvent(boolean sendEvent);
}
