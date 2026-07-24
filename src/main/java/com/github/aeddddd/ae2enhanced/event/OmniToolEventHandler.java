package com.github.aeddddd.ae2enhanced.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipe;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlacementUndo;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;

/**
 * 先进 ME 全能工具全局事件处理器。
 * <p>该类不在 {@code @Mod.EventBusSubscriber} 中自动注册，而是在
 * {@code FMLCommonSetupEvent} 中手动注册（参照 StructureEventHandler 的模式）。</p>
 */
public final class OmniToolEventHandler {

    private OmniToolEventHandler() {}

    // ==================== 基岩破坏者（左键不可破坏方块） ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.isCanceled()) return;
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)) return;

        int mode = AdvancedMEOmniToolItem.getMode(stack);
        if (mode != AdvancedMEOmniToolItem.MODE_UNIVERSAL && mode != AdvancedMEOmniToolItem.MODE_TRAVEL) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getDestroySpeed(event.getLevel(), event.getPos()) >= 0.0f) return;
        if (MiningModule.isBlacklisted(state.getBlock())) return;

        event.setCanceled(true);
        MiningModule.forceBreakBlock(player, event.getLevel(), event.getPos(), stack);
    }

    // ==================== 放置模式：Ctrl+右键撤销 ====================

    @SubscribeEvent
    public static void onPlacementToolRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.isCanceled()) return;

        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AdvancedMEOmniToolItem)
                || AdvancedMEOmniToolItem.getMode(stack) != AdvancedMEOmniToolItem.MODE_PLACEMENT) {
            return;
        }

        // 仅在客户端检测 Ctrl 键；服务端分支已在上方返回
        if (!net.minecraft.client.gui.screens.Screen.hasControlDown()) {
            return;
        }

        event.setCanceled(true);
        ModNetwork.CHANNEL.sendToServer(new PacketPlacementUndo());
    }

    // ==================== 掉落模式改写（普通挖掘路径） ====================
    // 注：NeoForge 1.20.1 已移除 BlockDropsEvent，普通挖掘掉落改写由
    // MixinBlockOmniToolDrops 拦截 Block.dropResources 实现。

    // ==================== 共形不变荷：掉落物不消失 ====================

    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (stack.getItem() instanceof AdvancedMEOmniToolItem && OmniToolUpgrades.hasConformalCharge(stack)) {
            event.setCanceled(true);
        }
    }

    // ==================== 共形不变荷：死亡保留 / 重生返还 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide() || event.isCanceled() || event.getDrops().isEmpty()) return;

        ListTag preserved = new ListTag();
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next().getItem();
            if (stack.getItem() instanceof AdvancedMEOmniToolItem && OmniToolUpgrades.hasConformalCharge(stack)) {
                it.remove();
                preserved.add(stack.save(new CompoundTag()));
            }
        }
        if (!preserved.isEmpty()) {
            player.getPersistentData().put(OmniToolNBT.CONFORMAL_PRESERVED, preserved);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        CompoundTag data = player.getPersistentData();
        if (!data.contains(OmniToolNBT.CONFORMAL_PRESERVED, Tag.TAG_LIST)) return;
        ListTag preserved = data.getList(OmniToolNBT.CONFORMAL_PRESERVED, Tag.TAG_COMPOUND);
        for (int i = 0; i < preserved.size(); i++) {
            ItemStack stack = ItemStack.of(preserved.getCompound(i));
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        data.remove(OmniToolNBT.CONFORMAL_PRESERVED);
    }

    // ==================== 升级配方注册（代码注入，无需 JSON） ====================

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        List<Recipe<?>> recipes = new ArrayList<>(event.getRecipeManager().getRecipes());
        addUpgradeRecipeIfMissing(recipes, "omni_tool_enchant_upgrade",
                OmniToolUpgradeRecipe.Type.ENCHANTED_BOOK);
        if (AE2EnhancedConfig.COMMON.omniToolEnableBedrockBreakerUpgrade.get()) {
            addUpgradeRecipeIfMissing(recipes, "omni_tool_bedrock_upgrade",
                    OmniToolUpgradeRecipe.Type.BEDROCK);
        }
        addUpgradeRecipeIfMissing(recipes, "omni_tool_conformal_upgrade",
                OmniToolUpgradeRecipe.Type.CONFORMAL_CHARGE);
        event.getRecipeManager().replaceRecipes(recipes);
    }

    private static void addUpgradeRecipeIfMissing(List<Recipe<?>> recipes, String name,
            OmniToolUpgradeRecipe.Type type) {
        ResourceLocation id = new ResourceLocation(AE2Enhanced.MOD_ID, name);
        for (Recipe<?> recipe : recipes) {
            if (recipe.getId().equals(id)) return;
        }
        recipes.add(new OmniToolUpgradeRecipe(id, type));
    }
}
