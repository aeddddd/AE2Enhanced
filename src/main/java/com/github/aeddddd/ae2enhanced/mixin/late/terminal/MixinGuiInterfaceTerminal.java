package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.client.gui.implementations.GuiInterfaceTerminal;
import appeng.client.me.ClientDCInternalInv;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 扩展接口终端，使其能够显示装配中枢的样板槽。
 *
 * <p>装配中枢的样板库存现在按行（每行 9 槽）拆分为多个 {@link ClientDCInternalInv}。
 * 原版 {@code GuiInterfaceTerminal.getById()} 创建的 {@link ClientDCInternalInv}
 * 固定只有 36 槽，这里改为 9 槽以适应每行显示。</p>
 *
 * <p>关键点：原方法会把新建的 {@code ClientDCInternalInv} 放入 {@code byId} 缓存，
 * 如果直接在 HEAD 取消并返回新对象，会导致装配中枢条目不被加入缓存，从而在
 * {@code refreshList()} 中无法显示。因此使用 {@link Redirect} 替换构造器参数，
 * 保留原方法的缓存和 {@code refreshList} 逻辑。</p>
 */
@SideOnly(Side.CLIENT)
@Mixin(value = GuiInterfaceTerminal.class, remap = false)
public class MixinGuiInterfaceTerminal {

    @Redirect(method = "getById", at = @At(value = "NEW", target = "Lappeng/client/me/ClientDCInternalInv;"))
    private ClientDCInternalInv ae2enhanced$createClientDCInternalInv(int size, long id, long sortBy, String string) {
        if (id >= 0) {
            return new ClientDCInternalInv(9, id, sortBy, string);
        }
        return new ClientDCInternalInv(size, id, sortBy, string);
    }
}
