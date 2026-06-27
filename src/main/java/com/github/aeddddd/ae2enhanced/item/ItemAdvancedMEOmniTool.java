package com.github.aeddddd.ae2enhanced.item;

import appeng.api.features.INetworkEncodable;
import appeng.api.networking.IGrid;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.omnitool.ConformalChargeHandler;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.network.SecurityTerminalNetworkLink;
import com.github.aeddddd.ae2enhanced.omnitool.network.WirelessTransmitterNetworkLink;
import com.github.aeddddd.ae2enhanced.block.BlockWirelessChannelTransmitter;

import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.OmniToolModules;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.Block;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemAdvancedMEOmniTool extends Item implements INetworkEncodable {

    // ---- Drop Modes ----
    public static final int DROP_NORMAL = 0;
    public static final int DROP_INVENTORY = 1;
    public static final int DROP_AE = 2;
    private static final String[] DROP_MODE_NAMES = {"normal", "inventory", "ae"};

    // ---- Modes ----
    public static final int MODE_COUNT = 4;
    public static final int MODE_UNIVERSAL = 0;
    public static final int MODE_PLACEMENT = 1;
    public static final int MODE_ROTATE = 2;
    public static final int MODE_TRAVEL = 3;

    private static final String[] MODE_NAMES = {
        "mode.universal", "mode.placement", "mode.rotate", "mode.travel"
    };

    // ---- Damage Sources ----
    public static final DamageSource OMNITOOL_DAMAGE =
        new DamageSource("ae2enhanced.omnitool").setDamageBypassesArmor();
    public static final DamageSource CHAOS_DAMAGE =
        new DamageSource("ae2enhanced.omnitool.chaos").setDamageBypassesArmor();

    public ItemAdvancedMEOmniTool() {
        setRegistryName(AE2Enhanced.MOD_ID, "me_omni_tool");
        setTranslationKey(AE2Enhanced.MOD_ID + ".me_omni_tool");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
        // 扳手集成已移除
    }

    // ==================== Mining ====================

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).getDestroySpeed(stack, state);
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).canHarvestBlock(state, stack);
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).getHarvestLevel(stack, toolClass, player, blockState);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        boolean handled = OmniToolModules.getForMode(MODE_UNIVERSAL).onBlockStartBreak(stack, pos, player);
        if (handled) return true;
        return super.onBlockStartBreak(stack, pos, player);
    }

    // ==================== Attack (Bypass Cooldown) ====================

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        // 战斗逻辑独立于模式，始终由 CombatModule 处理
        boolean handled = new CombatModule()
                .onLeftClickEntity(stack, player, entity);
        if (handled) return true;
        return super.onLeftClickEntity(stack, player, entity);
    }

    // ==================== Item Use First (Universal Rotate) ====================

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        int mode = getMode(stack);
        return mode == MODE_PLACEMENT || mode == MODE_UNIVERSAL;
    }

    // ==================== Right-Click on Block ====================

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // 蹲下右键无线频道发生器：绑定 AE
        Block targetBlock = world.getBlockState(pos).getBlock();
        if (targetBlock instanceof BlockWirelessChannelTransmitter && player.isSneaking()) {
            WirelessTransmitterNetworkLink.setBound(stack, pos, world.provider.getDimension());
            player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.ae_bound", pos.getX(), pos.getY(), pos.getZ()), true);
            player.setHeldItem(hand, stack);
            return EnumActionResult.SUCCESS;
        }

        return OmniToolModules.getForMode(getMode(stack))
                .onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    // ==================== Right-Click in Air ====================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        return OmniToolModules.getForMode(getMode(stack))
                .onItemRightClick(world, player, hand);
    }

    // ==================== Mode ====================

    public static int getMode(ItemStack stack) {
        return OmniToolUpgrades.getMode(stack);
    }

    public static void setMode(ItemStack stack, int mode) {
        OmniToolUpgrades.setMode(stack, mode);
    }

    public static void cycleMode(ItemStack stack) {
        OmniToolUpgrades.cycleMode(stack);
    }

    public static String getModeNameKey(int mode) {
        return "item.ae2enhanced.me_omni_tool." + MODE_NAMES[mode % MODE_COUNT];
    }

    // ==================== Drop Mode ====================

    public static int getDropMode(ItemStack stack) {
        return OmniToolUpgrades.getDropMode(stack);
    }

    public static void setDropMode(ItemStack stack, int mode) {
        OmniToolUpgrades.setDropMode(stack, mode);
    }

    public static void cycleDropMode(ItemStack stack) {
        OmniToolUpgrades.cycleDropMode(stack);
    }

    public static String getDropModeNameKey(int mode) {
        return "item.ae2enhanced.me_omni_tool.drop_mode." + DROP_MODE_NAMES[mode % 3];
    }

    // ==================== AE Binding ====================

    // INetworkEncodable —— 用于放置模式的安全终端绑定（不影响原有的无线频道发射器绑定）
    @Override
    public String getEncryptionKey(ItemStack item) {
        return SecurityTerminalNetworkLink.INSTANCE.isLinked(item)
                ? SecurityTerminalBindingHelper.getEncryptionKey(item)
                : "";
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        SecurityTerminalNetworkLink.INSTANCE.clear(item);
        if (encKey != null && !encKey.isEmpty()) {
            SecurityTerminalBindingHelper.setEncryptionKey(item, encKey);
        }
    }

    // AE 坐标绑定已迁移到 WirelessTransmitterNetworkLink；以下方法保留为兼容包装
    public static boolean isAEBound(ItemStack stack) {
        return WirelessTransmitterNetworkLink.INSTANCE.isLinked(stack);
    }

    @Deprecated
    public static void setAEBound(ItemStack stack, BlockPos pos, int dim) {
        WirelessTransmitterNetworkLink.setBound(stack, pos, dim);
    }

    @Deprecated
    public static BlockPos getAETransmitterPos(ItemStack stack) {
        return WirelessTransmitterNetworkLink.getTransmitterPos(stack);
    }

    @Deprecated
    public static int getAETransmitterDim(ItemStack stack) {
        return WirelessTransmitterNetworkLink.getTransmitterDim(stack);
    }

    @Deprecated
    public static void clearAEBinding(ItemStack stack) {
        WirelessTransmitterNetworkLink.INSTANCE.clear(stack);
    }

    @Nullable
    public static IGrid getAELinkedGrid(ItemStack stack, World world) {
        return WirelessTransmitterNetworkLink.INSTANCE.getLinkedGrid(stack, world, null);
    }

    // ==================== Silk Touch ====================

    public static boolean isSilkTouchEnabled(ItemStack stack) {
        return OmniToolUpgrades.isSilkTouchEnabled(stack);
    }

    public static void setSilkTouchEnabled(ItemStack stack, boolean enabled) {
        OmniToolUpgrades.setSilkTouchEnabled(stack, enabled);
    }

    public static void toggleSilkTouch(ItemStack stack) {
        OmniToolUpgrades.toggleSilkTouch(stack);
    }

    public static boolean isAdvancedSilkTouchEnabled(ItemStack stack) {
        return OmniToolUpgrades.isAdvancedSilkTouchEnabled(stack);
    }

    public static void setAdvancedSilkTouchEnabled(ItemStack stack, boolean enabled) {
        OmniToolUpgrades.setAdvancedSilkTouchEnabled(stack, enabled);
    }

    public static boolean hasBedrockBreaker(ItemStack stack) {
        return OmniToolUpgrades.hasBedrockBreaker(stack);
    }

    public static void setBedrockBreaker(ItemStack stack, boolean has) {
        OmniToolUpgrades.setBedrockBreaker(stack, has);
    }

    // ==================== Upgrades ====================

    public static boolean hasChaosCore(ItemStack stack) {
        return OmniToolUpgrades.hasChaosCore(stack);
    }

    public static void setChaosCore(ItemStack stack, boolean has) {
        OmniToolUpgrades.setChaosCore(stack, has);
    }

    public static boolean isChaosForceKillEnabled(ItemStack stack) {
        return OmniToolUpgrades.isChaosForceKillEnabled(stack);
    }

    public static void setChaosForceKillEnabled(ItemStack stack, boolean enabled) {
        OmniToolUpgrades.setChaosForceKillEnabled(stack, enabled);
    }

    public static boolean hasConformalCharge(ItemStack stack) {
        return OmniToolUpgrades.hasConformalCharge(stack);
    }

    public static void setConformalCharge(ItemStack stack, boolean has) {
        OmniToolUpgrades.setConformalCharge(stack, has);
    }

    public static boolean hasTravelStaff(ItemStack stack) {
        return OmniToolUpgrades.hasTravelStaff(stack);
    }

    public static void setTravelStaff(ItemStack stack, boolean has) {
        OmniToolUpgrades.setTravelStaff(stack, has);
    }

    public static boolean hasFortuneUpgrade(ItemStack stack) {
        return OmniToolUpgrades.hasFortuneUpgrade(stack);
    }

    public static int getFortuneLevel(ItemStack stack) {
        return OmniToolUpgrades.getFortuneLevel(stack);
    }

    public static void setFortuneLevel(ItemStack stack, int level) {
        OmniToolUpgrades.setFortuneLevel(stack, level);
    }

    // ==================== Stored Enchantments (from Enchanted Book) ====================

    public static boolean hasStoredEnchantments(ItemStack stack) {
        return OmniToolEnchantments.hasStoredEnchantments(stack);
    }

    public static NBTTagList getStoredEnchantments(ItemStack stack) {
        return OmniToolEnchantments.getStoredEnchantments(stack);
    }

    public static int getStoredEnchantmentLevel(ItemStack stack, short enchantmentId) {
        return OmniToolEnchantments.getStoredEnchantmentLevel(stack, enchantmentId);
    }

    public static int getEnchantmentSourceLevel(ItemStack stack, short enchantmentId) {
        return OmniToolEnchantments.getEnchantmentSourceLevel(stack, enchantmentId);
    }

    public static void setStoredEnchantmentLevel(ItemStack stack, short enchantmentId, int level) {
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, enchantmentId, level);
    }

    public static void setStoredEnchantments(ItemStack stack, NBTTagList list) {
        OmniToolEnchantments.setStoredEnchantments(stack, list);
    }

    public static NBTTagList copyEnchantmentsFromBook(ItemStack book) {
        return OmniToolEnchantments.copyEnchantmentsFromBook(book);
    }

    // ==================== Param Enabled ====================

    public static boolean isParamEnabled(ItemStack stack, int paramIdx) {
        return OmniToolUpgrades.isParamEnabled(stack, paramIdx);
    }

    public static void setParamEnabled(ItemStack stack, int paramIdx, boolean enabled) {
        OmniToolUpgrades.setParamEnabled(stack, paramIdx, enabled);
    }

    // 禁疗标记保持对 Mixin 的可访问性
    public static boolean hasAntiHeal(EntityLivingBase entity) {
        return OmniToolUpgrades.hasAntiHeal(entity);
    }

    // ==================== Tooltip ====================

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        int mode = getMode(stack);
        String modeName = I18n.format(getModeNameKey(mode));

        tooltip.add(TextFormatting.AQUA + "━━━━━━━━━━━━━━━━━━━━");
        tooltip.add(TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.mode", TextFormatting.YELLOW + modeName));

        if (isSilkTouchEnabled(stack)) {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.on"));
        } else {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.off"));
        }

        OmniToolModules.getForMode(mode).addTooltip(stack, worldIn, tooltip, flagIn);

        tooltip.add(TextFormatting.AQUA + "━━━━━━━━━━━━━━━━━━━━");

        boolean hasUpgrades = false;
        if (hasChaosCore(stack)) {
            tooltip.add(TextFormatting.GOLD + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.chaos"));
            hasUpgrades = true;
        }
        if (hasBedrockBreaker(stack)) {
            tooltip.add(TextFormatting.DARK_RED + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.bedrock"));
            hasUpgrades = true;
        }
        NBTTagList storedEnch = getStoredEnchantments(stack);
        for (int i = 0; i < storedEnch.tagCount(); i++) {
            NBTTagCompound tag = storedEnch.getCompoundTagAt(i);
            short id = tag.getShort("id");
            short lvl = tag.getShort("lvl");
            Enchantment ench = Enchantment.getEnchantmentByID(id);
            String name = ench != null ? ench.getTranslatedName(lvl) : I18n.format("item.ae2enhanced.me_omni_tool.unknown_enchant", id, lvl);
            tooltip.add(TextFormatting.GREEN + "● " + TextFormatting.WHITE + name);
            hasUpgrades = true;
        }
        if (hasTravelStaff(stack)) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.travel"));
            hasUpgrades = true;
        }
        if (hasConformalCharge(stack)) {
            tooltip.add(TextFormatting.AQUA + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.conformal"));
            hasUpgrades = true;
        }
        if (!hasUpgrades) {
            tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2enhanced.me_omni_tool.no_upgrades"));
        }

        if (com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule.isTravelAnchorBound(stack)) {
            BlockPos anchorPos = com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule.getBoundTravelAnchorPos(stack);
            int anchorDim = com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule.getBoundTravelAnchorDim(stack);
            tooltip.add(TextFormatting.LIGHT_PURPLE + "● " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.travel_anchor_bound", anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(), anchorDim));
        }

        if (WirelessTransmitterNetworkLink.INSTANCE.isLinked(stack)) {
            BlockPos aePos = WirelessTransmitterNetworkLink.getTransmitterPos(stack);
            int aeDim = WirelessTransmitterNetworkLink.getTransmitterDim(stack);
            if (aePos != null) {
                tooltip.add(TextFormatting.DARK_AQUA + "● " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_omni_tool.ae_bound", aePos.getX(), aePos.getY(), aePos.getZ(), aeDim));
            }
        }
    }

    // ==================== Item Entity Protection (Conformal Charge) ====================

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        return ConformalChargeHandler.onEntityItemUpdate(entityItem);
    }

    // ==================== Attribute Modifiers ====================

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        multimap.putAll(super.getAttributeModifiers(slot, stack));
        multimap.putAll(OmniToolModules.getForMode(getMode(stack)).getAttributeModifiers(slot, stack));
        return multimap;
    }

}
