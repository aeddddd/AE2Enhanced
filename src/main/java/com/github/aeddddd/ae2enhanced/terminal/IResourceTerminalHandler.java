package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * 非物品资源（RF/Mana/Starlight 等）在终端中的统一处理器接口.
 * <p>
 * 用于无 TII 时的 fallback：让标准终端与 Omni Terminal 共享同一套点击、tooltip、
 * 容器填充/排空逻辑。
 * </p>
 */
public interface IResourceTerminalHandler {

    /**
     * 处理器唯一标识，如 "energy" / "mana" / "starlight".
     */
    String getName();

    /**
     * 判断终端槽位中的 AE 堆叠是否为本资源.
     */
    boolean isResourceStack(IAEItemStack aeStack);

    /**
     * 判断玩家手持物品是否为本资源的数据包物品（如 ItemEnergyDrop）.
     */
    boolean isPacketItem(ItemStack stack);

    /**
     * 判断玩家手持物品是否为本资源可交互的容器（如电池、魔力物品）.
     */
    boolean isContainer(ItemStack stack);

    /**
     * 客户端点击处理.
     *
     * @param context 点击上下文
     * @return true 表示已处理，应取消原方法
     */
    boolean handleClick(ResourceClickContext context);

    /**
     * 为终端槽位中的资源堆叠生成 tooltip.
     */
    List<String> getTooltip(IAEItemStack aeStack);

    /**
     * 为玩家手持容器生成 tooltip 提示（注入/提取提示）.
     */
    List<String> getContainerTooltip(ItemStack container);
}
