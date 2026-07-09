package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.EnergyKey;

import javax.annotation.Nullable;

/**
 * 超维度存储文件中的 section 类型。
 * 用于 dirty 标记，只持久化发生变更的 section。
 * <p>ITEMS、FLUIDS、ENERGY 分别对应 AE2 原生物品、流体与模组内部能量类型；
 * ANY 作为第三方可选通道（如气体、源质、mana、starlight 等）的通用 section。</p>
 */
public enum StorageSection {
    ITEMS("items", AEKeyType.items()),
    FLUIDS("fluids", AEKeyType.fluids()),
    ENERGY("energy", EnergyKey.ENERGY_KEY_TYPE),
    ANY("any", null);

    private final String name;
    @Nullable
    private final AEKeyType keyType;

    StorageSection(String name, @Nullable AEKeyType keyType) {
        this.name = name;
        this.keyType = keyType;
    }

    /**
     * @return section 名称，用于日志与文件标识
     */
    public String getName() {
        return name;
    }

    /**
     * @return section 对应的 AEKeyType，若未绑定则返回 null（如 ANY）
     */
    @Nullable
    public AEKeyType getKeyType() {
        return keyType;
    }

    /**
     * 根据 AEKeyType 推断对应的 section。
     * <p>未知类型全部归入 {@link #ANY}，便于第三方可选通道统一处理。</p>
     *
     * @param type AE key type
     * @return 对应的 section，type 为 null 时返回 {@link #ANY}
     */
    public static StorageSection fromType(@Nullable AEKeyType type) {
        if (type == null) {
            return ANY;
        }
        if (type == AEKeyType.items()) {
            return ITEMS;
        }
        if (type == AEKeyType.fluids()) {
            return FLUIDS;
        }
        if (type == EnergyKey.ENERGY_KEY_TYPE) {
            return ENERGY;
        }
        return ANY;
    }
}
