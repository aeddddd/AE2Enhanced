package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

/** 超维度物品存储适配器；终端查询与网络订阅由容器级 TerminalQueryService 持有。 */
public class ItemStorageAdapter extends AbstractStorageAdapter<IAEItemStack, ItemDescriptor> {

    public ItemStorageAdapter(HyperdimensionalStorageFile file) {
        super(file);
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        file.load(storage);
        file.registerPostLoadHook(this::recalcTotal);
    }

    @Override
    protected StorageSection getStorageSection() {
        return StorageSection.ITEM;
    }

    @Override
    protected ItemDescriptor createDescriptor(IAEItemStack input) {
        ItemStack definition = input.getDefinition();
        return new ItemDescriptor(definition != null ? definition : input.createItemStack());
    }

    @Override
    protected IAEItemStack createResult(IAEItemStack request, HugeCount amount) {
        IAEItemStack result = ((IItemStorageChannel) channel).createStack(request.createItemStack());
        if (result == null) return null;
        result.setStackSize(amount.toLongSaturated());
        return result;
    }

    @Override
    protected IAEItemStack getAETemplate(ItemDescriptor descriptor) {
        return descriptor.getAETemplate((IItemStorageChannel) channel);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return (IStorageChannel<IAEItemStack>) channel;
    }
}
