package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.ItemRepo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * ItemRepo 私有字段访问接口, 仅客户端加载.
 * 供 OmniItemRepo 同步父类 view/changed/resort 状态.
 */
@Mixin(value = ItemRepo.class, remap = false)
public interface IItemRepoAccessor {

    @Accessor("view")
    void ae2e$setView(List<IAEItemStack> view);

    @Accessor("changed")
    void ae2e$setChanged(boolean changed);

    @Accessor("resort")
    void ae2e$setResort(boolean resort);
}
