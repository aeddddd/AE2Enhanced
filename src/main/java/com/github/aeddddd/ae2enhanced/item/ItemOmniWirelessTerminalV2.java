package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import appeng.core.localization.PlayerMessages;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * 第二代全能无线终端："可拼接面板"终端（阶段 2a 起）。
 *
 * <p>与 {@link ItemOmniWirelessTerminal} 的关系是"同一终端的两代外壳"：无线宿主链路
 * （{@code IWirelessTermHandler} 注册、供电、{@code getStorageId} 的 UUID、Baubles 饰品位）
 * 全部继承复用，唯一区别是打开哪个 GUI：
 * 本类打开 {@link GuiHandler#GUI_SPLICED_TERMINAL}（15，可拼接），旧类保持 11。</p>
 *
 * <p>注意：两代物品各自持有独立的 storageId（NBT 里的 UUID 不同 → WorldSavedData 内容不同）。
 * 阶段 5 会加入"旧→新"转换配方（{@code old.copy()} 换物品类型但保留 storageId），
 * 实现内容零迁移；<strong>本阶段不加任何工作台配方</strong>，只能通过创造模式或命令获取。</p>
 */
public class ItemOmniWirelessTerminalV2 extends ItemOmniWirelessTerminal {

    public ItemOmniWirelessTerminalV2() {
        // 父类含名构造器完成注册名/翻译键/无线处理器注册（canHandle 按实例比较，两代互不干扰）
        super("omni_wireless_terminal_ii");
        // V2 当前仅 dev 可见：不出现在创造模式物品栏（JEI 隐藏在 AE2EnhancedJEIPlugin 里做）。
        setCreativeTab(null);
    }

    /**
     * 与父类相同的开启前校验（加密密钥 / 电量），但打开可拼接终端 GUI。
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            ItemStack held = player.getHeldItem(hand);
            String key = this.getEncryptionKey(held);
            if (key.isEmpty()) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());
                return new ActionResult<>(EnumActionResult.FAIL, held);
            }
            if (!this.hasPower(player, 0.5, held)) {
                player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                return new ActionResult<>(EnumActionResult.FAIL, held);
            }
            int slot = player.inventory.currentItem;
            if (hand == EnumHand.OFF_HAND) {
                slot = 40;
            }
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_SPLICED_TERMINAL, world, slot, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
