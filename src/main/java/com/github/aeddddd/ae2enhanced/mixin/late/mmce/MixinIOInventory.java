package com.github.aeddddd.ae2enhanced.mixin.late.mmce;

import com.github.aeddddd.ae2enhanced.mixin.bridge.ISlotIndexProvider;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IOInventory.readNBT 会整体重建 inSlots/inventory 数组（绕过全部常规写入口）,
 * 此处补充槽位索引置脏,配合 {@link MixinIItemHandlerImpl}。
 */
@Mixin(value = IOInventory.class, remap = false)
public abstract class MixinIOInventory {

    @Inject(method = "readNBT", at = @At("RETURN"), require = 0)
    private void ae2e$dirtyOnReadNBT(NBTTagCompound tag, CallbackInfo ci) {
        ((ISlotIndexProvider) this).ae2e$markSlotIndexDirty();
    }
}
