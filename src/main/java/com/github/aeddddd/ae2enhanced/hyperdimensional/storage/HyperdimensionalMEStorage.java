package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.network.chat.Component;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannel;

/**
 * 把内部 {@link HyperdimensionalStorage} 桥接到 AE2 {@link MEStorage} 的适配器。
 * <p>同时实现 {@link IStorageProvider}，方便直接挂载到 AE2 网络。</p>
 */
public class HyperdimensionalMEStorage implements MEStorage, IStorageProvider {

    private final HyperdimensionalStorage storage;
    private final IActionSource source;

    public HyperdimensionalMEStorage(HyperdimensionalStorage storage, IActionSource source) {
        this.storage = storage;
        this.source = source;
    }

    public HyperdimensionalStorage getInternalStorage() {
        return storage;
    }

    public IActionSource getSource() {
        return source;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("block.ae2enhanced.hyperdimensional_controller");
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        StorageChannel<?> channel = storage.getChannel(what.getType());
        if (channel == null) {
            return 0;
        }
        return storage.insert(what, amount, mode);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        StorageChannel<?> channel = storage.getChannel(what.getType());
        if (channel == null) {
            return 0;
        }
        return storage.extract(what, amount, mode);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        // 通过 HyperdimensionalStorage 的统一接口获取，利用内部缓存避免每次都遍历全量条目
        storage.getAvailableStacks(out);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(this);
    }
}
