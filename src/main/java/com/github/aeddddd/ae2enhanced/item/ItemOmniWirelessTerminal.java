package com.github.aeddddd.ae2enhanced.item;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.features.IWirelessTermHandler;
import appeng.core.localization.PlayerMessages;
import appeng.util.ConfigManager;
import appeng.api.util.IConfigManager;
import appeng.core.AEConfig;
import appeng.items.tools.powered.powersink.AEBasePoweredItem;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/**
 * 全能无线终端 —— 物品库 + 合成栏 + 编码样板(81槽位) + 右侧存储
 */
@Optional.InterfaceList({
        @Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
})
public class ItemOmniWirelessTerminal extends AEBasePoweredItem implements IWirelessTermHandler, baubles.api.IBauble {

    public ItemOmniWirelessTerminal() {
        super(AEConfig.instance().getWirelessTerminalBattery());
        setMaxStackSize(1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.omni_wireless_terminal");
        setRegistryName("omni_wireless_terminal");
        AEApi.instance().registries().wireless().registerWirelessHandler(this);
    }

    @Override
    public boolean canHandle(ItemStack is) {
        return is.getItem() == this;
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        return Platform.openNbtData(item).getString("encryptionKey");
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        Platform.openNbtData(item).setString("encryptionKey", encKey);
    }

    @Override
    public boolean usePower(EntityPlayer player, double amt, ItemStack is) {
        return this.extractAEPower(is, amt, appeng.api.config.Actionable.MODULATE) >= amt - 0.5;
    }

    @Override
    public boolean hasPower(EntityPlayer player, double amt, ItemStack is) {
        return this.getAECurrentPower(is) >= amt;
    }

    @Override
    public IConfigManager getConfigManager(ItemStack target) {
        ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            NBTTagCompound data = Platform.openNbtData(target);
            manager.writeToNBT(data);
        });
        out.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        out.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        out.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        out.readFromNBT(Platform.openNbtData(target).copy());
        return out;
    }

    /**
     * 获取或创建该终端 ItemStack 对应的存储 UUID.
     * UUID 持久化在 ItemStack NBT 中,WorldSavedData 通过此 UUID 存取实际数据.
     */
    public static UUID getStorageId(ItemStack stack) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        if (!tag.hasUniqueId("storageId")) {
            UUID id = UUID.randomUUID();
            tag.setUniqueId("storageId", id);
        }
        return tag.getUniqueId("storageId");
    }

    public static void setStorageId(ItemStack stack, UUID id) {
        Platform.openNbtData(stack).setUniqueId("storageId", id);
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        // 返回 AE2 无线终端的 GuiBridge,避免 GuiCraftAmount.initGui() 中的 ClassCastException.
        // Omni Terminal 没有自己的 GuiBridge enum 值,使用 GUI_WIRELESS_TERM 作为回退.
        return appeng.core.sync.GuiBridge.GUI_WIRELESS_TERM;
    }

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
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TERMINAL, world, slot, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean isFull3D() {
        return false;
    }

    // === Baubles 饰品支持 ===

    @Override
    @Optional.Method(modid = "baubles")
    public baubles.api.BaubleType getBaubleType(ItemStack itemStack) {
        return baubles.api.BaubleType.TRINKET;
    }

    @Override
    @Optional.Method(modid = "baubles")
    public boolean canEquip(ItemStack stack, EntityLivingBase player) {
        return true;
    }

    @Override
    @Optional.Method(modid = "baubles")
    public boolean willAutoSync(ItemStack stack, EntityLivingBase player) {
        return true;
    }
}
