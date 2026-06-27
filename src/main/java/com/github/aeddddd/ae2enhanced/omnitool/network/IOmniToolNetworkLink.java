package com.github.aeddddd.ae2enhanced.omnitool.network;

import appeng.api.networking.IGrid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 先进ME全能工具的网络链接抽象。
 * 工具支持多种链接方式（安全终端加密钥、无线频道发射器坐标等），
 * 所有方式都通过此接口对外暴露统一能力。
 */
public interface IOmniToolNetworkLink {

    /**
     * 返回此链接类型的唯一标识，用于日志/配置。
     */
    String getId();

    /**
     * 返回该链接在 tooltip 中显示的本地化键。
     */
    String getTooltipKey();

    /**
     * 判断指定物品是否已使用该链接方式绑定。
     */
    boolean isLinked(ItemStack stack);

    /**
     * 获取该物品当前绑定的 ME 网络。
     *
     * @param stack  工具物品
     * @param world  当前世界
     * @param player 可选玩家，用于发送提示信息
     * @return 网格，未绑定或不可用时返回 null
     */
    @Nullable
    IGrid getLinkedGrid(ItemStack stack, World world, @Nullable EntityPlayer player);

    /**
     * 清除该链接方式的绑定数据。
     */
    void clear(ItemStack stack);
}
