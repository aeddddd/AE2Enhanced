package com.github.aeddddd.ae2enhanced.storage.mana;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;

/**
 * Botania Mana 通道解析器.
 * <p>
 * 动态返回当前实际生效的 Mana 存储通道：若 Botania_Applie 已注册外部通道则优先返回外部通道,
 * 否则返回 AE2E 自有的 {@link IManaStorageChannel}.
 * </p>
 */
public final class ManaChannelResolver {

    private ManaChannelResolver() {
    }

    /**
     * 获取当前生效的 Mana 存储通道.
     *
     * @return 外部 Mana 通道或 AE2E 自有 Mana 通道
     */
    public static IStorageChannel<?> getChannel() {
        if (BotaniaApplieCompat.isManaStorageChannelAvailable()) {
            return BotaniaApplieCompat.getManaStorageChannelInstance();
        }
        return AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class);
    }
}
