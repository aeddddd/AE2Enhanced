package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import java.math.BigInteger;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * 把内部 {@link BigInteger} 存储桥接到 AE2 {@link MEStorage} 的适配器。
 * <p>AE2 的接口使用 {@code long}，因此单次交易上限为 {@link Long#MAX_VALUE}，但内部可保存更多。</p>
 */
public class HyperdimensionalMEStorage implements MEStorage {

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
        BigInteger delta = BigInteger.valueOf(amount);
        BigInteger actual = delta.min(storage.getRemainingCapacity(what));
        if (actual.signum() <= 0) {
            return 0;
        }
        if (mode == Actionable.SIMULATE) {
            return actual.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        }
        return storage.add(what, actual).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || what == null) {
            return 0;
        }
        BigInteger delta = BigInteger.valueOf(amount);
        BigInteger have = storage.get(what);
        BigInteger actual = delta.min(have);
        if (actual.signum() <= 0) {
            return 0;
        }
        if (mode == Actionable.SIMULATE) {
            return actual.longValue();
        }
        return storage.remove(what, actual).longValue();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : storage.getContents().entrySet()) {
            BigInteger amount = entry.getValue();
            if (amount.signum() > 0) {
                // AE2 网络侧最多只能看到 Long.MAX_VALUE
                long visible = amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                out.add(entry.getKey(), visible);
            }
        }
    }
}
