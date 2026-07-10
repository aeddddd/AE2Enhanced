package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.adapter;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.codec.GenericKeyDescriptorCodec;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.descriptor.GenericKeyDescriptor;

import javax.annotation.Nullable;
import java.math.BigInteger;

/**
 * 通用 AEKeyType 存储适配器。
 * 用于为游戏中所有已注册 AEKeyType（包括第三方模组注册的类型）提供统一的存储支持。
 * 采用 {@link GenericKeyDescriptor} 与 {@link GenericKeyDescriptorCodec} 进行序列化。
 */
public class GenericStorageAdapter extends AbstractStorageAdapter<AEKey, GenericKeyDescriptor> {

    // 新版 v3 格式不再使用 type byte，保留 0 仅用于 v2 格式校验（实际上不会为第三方类型生成 v2 文件）。
    private static final byte TYPE_GENERIC = 0;

    public GenericStorageAdapter(AEKeyType keyType) {
        super(GenericKeyDescriptorCodec.INSTANCE, keyType);
    }

    @Override
    public GenericKeyDescriptor createDescriptor(AEKey input) {
        return new GenericKeyDescriptor(input);
    }

    @Override
    @Nullable
    public AEKey cast(AEKey key) {
        if (key == null) {
            return null;
        }
        return key.getType().equals(getKeyType()) ? key : null;
    }

    @Override
    public AEKey createResult(AEKey request, BigInteger amount) {
        return request;
    }

    @Override
    protected byte getTypeByte() {
        return TYPE_GENERIC;
    }
}
