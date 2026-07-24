package com.github.aeddddd.ae2enhanced.item;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.omnitool.ConformalChargeHandler;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.OmniToolModules;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;

/**
 * 先进 ME 全能工具。
 * 物品本体仅作为分发器，将事件转发给当前模式对应的模块。
 */
public class AdvancedMEOmniToolItem extends Item {

    // ---- Drop Modes ----
    public static final int DROP_NORMAL = 0;
    public static final int DROP_INVENTORY = 1;
    public static final int DROP_AE = 2;
    private static final String[] DROP_MODE_NAMES = { "normal", "inventory", "ae" };

    // ---- Modes ----
    public static final int MODE_COUNT = 4;
    public static final int MODE_UNIVERSAL = 0;
    public static final int MODE_PLACEMENT = 1;
    public static final int MODE_ROTATE = 2;
    public static final int MODE_TRAVEL = 3;

    private static final String[] MODE_NAMES = {
            "mode.universal", "mode.placement", "mode.rotate", "mode.travel"
    };

    // ---- Damage Type ----
    public static final ResourceKey<DamageType> OMNITOOL_DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(AE2Enhanced.MOD_ID, "omnitool"));

    public AdvancedMEOmniToolItem(Properties properties) {
        super(properties);
    }

    // ==================== Mining（固定转发 MiningModule） ====================

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).canHarvestBlock(state, stack);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        boolean handled = OmniToolModules.getForMode(MODE_UNIVERSAL).onBlockStartBreak(stack, pos, player);
        if (handled) return true;
        return super.onBlockStartBreak(stack, pos, player);
    }

    // ==================== Attack (Bypass Cooldown) ====================

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        // 战斗逻辑独立于模式，始终由 CombatModule 处理
        boolean handled = new CombatModule().onLeftClickEntity(stack, player, entity);
        if (handled) return true;
        return super.onLeftClickEntity(stack, player, entity);
    }

    // ==================== Sneak Bypass ====================

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        int mode = getMode(stack);
        return mode == MODE_PLACEMENT || mode == MODE_UNIVERSAL;
    }

    // ==================== Right-Click on Block ====================

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player == null) return InteractionResult.PASS;

        // 蹲下右键无线访问点:绑定 AE 网络(对应 1.12 潜行右键安全终端)
        if (player.isShiftKeyDown()
                && level.getBlockEntity(context.getClickedPos()) instanceof IWirelessAccessPoint accessPoint) {
            if (accessPoint.getGrid() == null) {
                player.displayClientMessage(Component.translatable("message.ae2enhanced.omnitool.ae_bind_failed"),
                        true);
                return InteractionResult.FAIL;
            }
            GlobalPos pos = GlobalPos.of(level.dimension(), context.getClickedPos());
            OmniToolNetworkLink.link(stack, pos);
            player.displayClientMessage(
                    Component.translatable("message.ae2enhanced.omnitool.ae_bound",
                            pos.dimension().location() + " " + pos.pos().toShortString()),
                    true);
            player.setItemInHand(context.getHand(), stack);
            return InteractionResult.SUCCESS;
        }

        return OmniToolModules.getForMode(getMode(stack)).onItemUse(context);
    }

    // ==================== Right-Click in Air ====================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);

        return OmniToolModules.getForMode(getMode(stack)).onItemRightClick(level, player, hand);
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

    public static boolean hasConformalCharge(ItemStack stack) {
        return OmniToolUpgrades.hasConformalCharge(stack);
    }

    public static void setConformalCharge(ItemStack stack, boolean has) {
        OmniToolUpgrades.setConformalCharge(stack, has);
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

    public static ListTag getStoredEnchantments(ItemStack stack) {
        return OmniToolEnchantments.getStoredEnchantments(stack);
    }

    // ==================== Param Enabled ====================

    public static boolean isParamEnabled(ItemStack stack, int paramIdx) {
        return OmniToolUpgrades.isParamEnabled(stack, paramIdx);
    }

    public static void setParamEnabled(ItemStack stack, int paramIdx, boolean enabled) {
        OmniToolUpgrades.setParamEnabled(stack, paramIdx, enabled);
    }

    // ==================== Tooltip ====================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int mode = getMode(stack);
        Component modeName = Component.translatable(getModeNameKey(mode)).withStyle(ChatFormatting.YELLOW);

        tooltip.add(Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.mode", modeName)
                .withStyle(ChatFormatting.WHITE));

        if (isSilkTouchEnabled(stack)) {
            tooltip.add(bullet(Component.translatable("item.ae2enhanced.me_omni_tool.silk_touch.on")));
        } else {
            tooltip.add(bullet(Component.translatable("item.ae2enhanced.me_omni_tool.silk_touch.off")));
        }

        OmniToolModules.getForMode(mode).addTooltip(stack, level, tooltip, flag);

        tooltip.add(Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.AQUA));

        boolean hasUpgrades = false;
        if (hasBedrockBreaker(stack)) {
            tooltip.add(upgradeLine(Component.translatable("item.ae2enhanced.me_omni_tool.upgrade.bedrock"),
                    ChatFormatting.DARK_RED));
            hasUpgrades = true;
        }
        ListTag storedEnch = getStoredEnchantments(stack);
        for (int i = 0; i < storedEnch.size(); i++) {
            CompoundTag tag = storedEnch.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            int lvl = tag.getShort("lvl");
            Enchantment ench = id != null ? BuiltInRegistries.ENCHANTMENT.get(id) : null;
            Component name = ench != null ? ench.getFullname(lvl)
                    : Component.translatable("item.ae2enhanced.me_omni_tool.unknown_enchant", tag.getString("id"),
                            lvl);
            tooltip.add(upgradeLine(name, ChatFormatting.GREEN));
            hasUpgrades = true;
        }
        if (hasConformalCharge(stack)) {
            tooltip.add(upgradeLine(Component.translatable("item.ae2enhanced.me_omni_tool.upgrade.conformal"),
                    ChatFormatting.AQUA));
            hasUpgrades = true;
        }
        if (!hasUpgrades) {
            tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.no_upgrades")
                    .withStyle(ChatFormatting.GRAY));
        }

        GlobalPos linkedPos = OmniToolNetworkLink.getLinkedPos(stack);
        if (linkedPos != null) {
            tooltip.add(upgradeLine(
                    Component.translatable("item.ae2enhanced.me_omni_tool.ae_bound",
                            linkedPos.dimension().location() + " " + linkedPos.pos().toShortString()),
                    ChatFormatting.DARK_AQUA));
        }
    }

    private static Component bullet(Component content) {
        return Component.literal("▸ ").withStyle(ChatFormatting.GRAY)
                .append(content.copy().withStyle(ChatFormatting.WHITE));
    }

    private static Component upgradeLine(Component content, ChatFormatting color) {
        return Component.literal("● ").withStyle(color)
                .append(content.copy().withStyle(ChatFormatting.WHITE));
    }

    // ==================== Item Entity Protection (Conformal Charge) ====================

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entityItem) {
        return ConformalChargeHandler.onEntityItemUpdate(entityItem);
    }

    // ==================== Attribute Modifiers ====================

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();
        multimap.putAll(super.getAttributeModifiers(slot, stack));
        multimap.putAll(OmniToolModules.getForMode(getMode(stack)).getAttributeModifiers(slot, stack));
        return multimap;
    }
}
