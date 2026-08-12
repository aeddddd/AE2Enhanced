package com.github.aeddddd.ae2enhanced.item;

import appeng.api.features.INetworkEncodable;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.ring.RingNBT;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 先进网络链接凭证 —— 绑定 ME 网络的个人功能/防护凭证.
 *
 * <p>置于背包、双手或饰品栏即生效；通过安全终端绑定到 ME 网络后,
 * 可直接消耗网络中存储的 RF(能量存储通道),网络不可达时回退到内部 2.1G RF 缓存.
 * 分 I~III 阶段递进解锁功能,与 16 个无限时间被约束微型奇点逐个合成后飞升.</p>
 */
@Optional.InterfaceList({
        @Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
})
public class ItemNetworkLinkCredential extends Item implements INetworkEncodable, baubles.api.IBauble {

    public ItemNetworkLinkCredential() {
        setRegistryName(AE2Enhanced.MOD_ID, "network_link_credential");
        setTranslationKey(AE2Enhanced.MOD_ID + ".network_link_credential");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
        setMaxDamage(0);
    }

    // ==================== 安全终端绑定 ====================

    @Override
    public String getEncryptionKey(ItemStack item) {
        return SecurityTerminalBindingHelper.getEncryptionKeyForEncodable(item);
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        SecurityTerminalBindingHelper.setEncryptionKeyForEncodable(item, encKey, name);
    }

    // ==================== 右键打开配置 GUI ====================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_RING_CONFIG, world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    // ==================== 内部 RF 缓存(Forge Energy capability) ====================

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable NBTTagCompound nbt) {
        return new ICapabilityProvider() {
            private final IEnergyStorage storage = new IEnergyStorage() {
                @Override
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    int space = getMaxEnergyStored() - getEnergyStored();
                    int accepted = Math.min(space, Math.max(0, maxReceive));
                    if (!simulate && accepted > 0) {
                        RingNBT.setEnergy(stack, getEnergyStored() + accepted);
                    }
                    return accepted;
                }

                @Override
                public int extractEnergy(int maxExtract, boolean simulate) {
                    return 0; // 内部缓存仅供凭证自身消耗,不对外放电
                }

                @Override
                public int getEnergyStored() {
                    return RingNBT.getEnergy(stack);
                }

                @Override
                public int getMaxEnergyStored() {
                    return AE2EnhancedConfig.ring.internalBufferSize;
                }

                @Override
                public boolean canExtract() {
                    return false;
                }

                @Override
                public boolean canReceive() {
                    return true;
                }
            };

            @Override
            public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
                return capability == CapabilityEnergy.ENERGY;
            }

            @Nullable
            @Override
            @SuppressWarnings("unchecked")
            public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
                return capability == CapabilityEnergy.ENERGY ? (T) storage : null;
            }
        };
    }

    // ==================== 掉落物保护(任何版本均免疫环境销毁) ====================

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        if (!entityItem.isEntityInvulnerable(net.minecraft.util.DamageSource.GENERIC)) {
            entityItem.setEntityInvulnerable(true);
        }
        return false;
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return false;
    }

    // ==================== 飞升光效 ====================

    @SideOnly(Side.CLIENT)
    @Override
    public boolean hasEffect(ItemStack stack) {
        return RingNBT.isAscended(stack);
    }

    // ==================== Tooltip(简洁 + Shift 展开详情) ====================

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        String keyPrefix = "item.ae2enhanced.network_link_credential.";

        boolean linked = SecurityTerminalBindingHelper.isLinked(stack);
        tooltip.add((linked ? TextFormatting.GREEN : TextFormatting.RED)
                + I18n.format(keyPrefix + (linked ? "linked" : "unlinked")));

        int energy = RingNBT.getEnergy(stack);
        int max = AE2EnhancedConfig.ring.internalBufferSize;
        tooltip.add(TextFormatting.YELLOW + formatCompact(energy)
                + TextFormatting.GRAY + " / " + formatCompact(max) + " RF");

        if (!GuiScreen.isShiftKeyDown()) {
            tooltip.add(TextFormatting.DARK_GRAY + I18n.format(keyPrefix + "shift_hint"));
            return;
        }

        // ---- Shift 详情 ----
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format(keyPrefix + "energy_detail",
                TextFormatting.WHITE + String.format("%,d", energy) + TextFormatting.DARK_GRAY,
                String.format("%,d", max)));

        int progress = RingNBT.getAscendProgress(stack);
        if (progress > 0 && !RingNBT.isAscended(stack)) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format(keyPrefix + "ascend_progress",
                    progress, RingNBT.MAX_ASCEND));
        }

        tooltip.add(TextFormatting.GRAY + I18n.format(keyPrefix + "features." + featureGroup(stack)));
        tooltip.add(TextFormatting.DARK_GRAY + I18n.format(keyPrefix + "hint"));
    }

    /** 当前阶段解锁的功能组(用于 tooltip 摘要). */
    private static int featureGroup(ItemStack stack) {
        if (RingNBT.isAscended(stack)) return 3;
        return RingNBT.getTier(stack);
    }

    /** 紧凑数字格式: 1.2G / 350M / 12k. */
    private static String formatCompact(long value) {
        if (value >= 1_000_000_000L) return String.format("%.1fG", value / 1_000_000_000.0);
        if (value >= 1_000_000L) return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000L) return String.format("%.1fk", value / 1_000.0);
        return String.valueOf(value);
    }

    // ==================== Baubles 饰品支持 ====================

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
