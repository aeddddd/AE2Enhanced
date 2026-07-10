package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter.GenericStorageAdapter;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.GenericKeyDescriptor;

/**
 * 通用 AEKeyType 存储通道。
 * 为游戏中所有已注册的 AEKeyType（包括第三方模组注册的类型）提供统一的存储支持。
 */
public class GenericStorageChannel extends AbstractStorageChannel<AEKey, GenericKeyDescriptor> {

    public GenericStorageChannel(AEKeyType keyType) {
        super(new GenericStorageAdapter(keyType));
    }

    @Override
    public AEKeyType getKeyType() {
        return adapter.getKeyType();
    }
}
