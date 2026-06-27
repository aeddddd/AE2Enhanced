package com.github.aeddddd.ae2enhanced.omnitool.network;

import appeng.api.networking.IGrid;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 通过 AE2 安全终端加密钥链接 ME 网络。
 * 这是 Placement 模式取方块/线缆所使用的链接方式。
 */
public class SecurityTerminalNetworkLink implements IOmniToolNetworkLink {

    public static final String ID = "security_terminal";
    public static final SecurityTerminalNetworkLink INSTANCE = new SecurityTerminalNetworkLink();

    private SecurityTerminalNetworkLink() {}

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTooltipKey() {
        return "item.ae2enhanced.me_omni_tool.placement.linked";
    }

    @Override
    public boolean isLinked(ItemStack stack) {
        return SecurityTerminalBindingHelper.isLinked(stack);
    }

    @Override
    @Nullable
    public IGrid getLinkedGrid(ItemStack stack, World world, @Nullable EntityPlayer player) {
        return SecurityTerminalBindingHelper.getLinkedGrid(stack, world, player);
    }

    @Override
    public void clear(ItemStack stack) {
        SecurityTerminalBindingHelper.clearEncryptionKey(stack);
    }
}
