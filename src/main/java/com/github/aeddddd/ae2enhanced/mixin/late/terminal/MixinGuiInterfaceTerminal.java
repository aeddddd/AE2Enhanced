package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.me.ClientDCInternalInv;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 扩展接口终端,使其能够显示装配中枢的大量样板槽.
 *
 * <p>原版 {@code GuiInterfaceTerminal.getById()} 创建的 {@link ClientDCInternalInv}
 * 固定只有 36 槽,无法容纳装配中枢最多 2880 个样板槽.本 Mixin 为装配中枢跟踪数据
 * (ID &gt;= 0,与原版负 ID 不重叠)创建足够大的客户端库存.</p>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public class MixinGuiInterfaceTerminal {

    @Inject(method = "getById", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onGetById(long id, long sortBy, String string,
                                       CallbackInfoReturnable<ClientDCInternalInv> cir) {
        if (id >= 0) {
            cir.setReturnValue(new ClientDCInternalInv(
                    TileAssemblyController.PATTERN_SLOTS_MAX, id, sortBy, string));
        }
    }
}
