package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

/**
 * 包私有抽象类 ItemVariantList 的 getRecords 调用接口.
 * 供 MixinContainerMEMonitorable 判断 bucket 是否已清空.
 */
@Mixin(targets = "appeng.util.item.ItemVariantList", remap = false)
public interface IItemVariantListAccessor {

    @Invoker("getRecords")
    @SuppressWarnings("rawtypes")
    Map ae2e$invokeGetRecords();
}
