package com.github.aeddddd.ae2enhanced.omnitool.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;

/**
 * 通用/旅行模式下的挖掘破坏逻辑。
 */
public class MiningModule implements IOmniToolModule {

    private static final float DESTROY_SPEED = 1_000_000.0f;

    // ---- 黑名单缓存（5 秒） ----
    private static Set<ResourceLocation> blacklistCache = null;
    private static long blacklistCacheTime = -1L;

    @Override
    public int getMode() {
        return AdvancedMEOmniToolItem.MODE_UNIVERSAL;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        int mode = AdvancedMEOmniToolItem.getMode(stack);
        if (mode != AdvancedMEOmniToolItem.MODE_UNIVERSAL && mode != AdvancedMEOmniToolItem.MODE_TRAVEL) return 1.0f;
        if (isBlacklisted(state.getBlock())) return 0.0f;
        return DESTROY_SPEED;
    }

    @Override
    public boolean canHarvestBlock(BlockState state, ItemStack stack) {
        int mode = AdvancedMEOmniToolItem.getMode(stack);
        if (mode != AdvancedMEOmniToolItem.MODE_UNIVERSAL && mode != AdvancedMEOmniToolItem.MODE_TRAVEL) return false;
        return !isBlacklisted(state.getBlock());
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        int mode = AdvancedMEOmniToolItem.getMode(stack);
        if (mode != AdvancedMEOmniToolItem.MODE_UNIVERSAL && mode != AdvancedMEOmniToolItem.MODE_TRAVEL) {
            return false;
        }

        int cooldown = getBreakCooldown(stack);
        if (cooldown > 0) {
            long now = player.level().getGameTime();
            long last = getLastBreakTick(stack);
            if (now - last < cooldown) {
                // 同步方块状态到客户端，防止幽灵方块
                if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
                }
                return true;
            }
            setLastBreakTick(stack, now);
        }

        // 基岩破坏者：允许破坏硬度为 -1 的不可破坏方块
        if (AdvancedMEOmniToolItem.hasBedrockBreaker(stack)) {
            BlockState state = player.level().getBlockState(pos);
            Block block = state.getBlock();
            if (state.getDestroySpeed(player.level(), pos) < 0.0F) {
                if (!player.level().isClientSide() && player.level() instanceof ServerLevel serverLevel) {
                    List<ItemStack> drops = getDrops(state, serverLevel, pos, serverLevel.getBlockEntity(pos), player,
                            stack);
                    if (drops.isEmpty()) {
                        Item item = block.asItem();
                        if (item != null && item != net.minecraft.world.item.Items.AIR) {
                            drops.add(new ItemStack(item, 1));
                        }
                    }
                    handleDrops(player.level(), player, pos, drops, stack);
                    player.level().destroyBlock(pos, false);
                }
                return true;
            }
        }

        // 高级精准采集：保留方块 NBT 掉落
        if (AdvancedMEOmniToolItem.isSilkTouchEnabled(stack)
                && AdvancedMEOmniToolItem.isAdvancedSilkTouchEnabled(stack)) {
            return breakBlockWithNBT(stack, player.level(), pos, player);
        }

        return false;
    }

    @Override
    public void addTooltip(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int cooldown = getBreakCooldown(stack);
        tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.break_cooldown", cooldown));

        int dropMode = AdvancedMEOmniToolItem.getDropMode(stack);
        Component dropModeName = Component.translatable(AdvancedMEOmniToolItem.getDropModeNameKey(dropMode));
        tooltip.add(Component.translatable("item.ae2enhanced.me_omni_tool.drop_mode", dropModeName));
    }

    /**
     * 左键事件路径的强制破坏（用于基岩等不可破坏方块）。
     */
    public static void forceBreakBlock(Player player, Level level, BlockPos pos, ItemStack stack) {
        if (level.isClientSide()) return;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (isBlacklisted(block)) return;

        int cooldown = getBreakCooldown(stack);
        if (cooldown > 0) {
            long now = level.getGameTime();
            long last = getLastBreakTick(stack);
            if (now - last < cooldown) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundBlockUpdatePacket(level, pos));
                }
                return;
            }
            setLastBreakTick(stack, now);
        }

        // 高级精准采集：保留方块 NBT 掉落
        if (AdvancedMEOmniToolItem.isSilkTouchEnabled(stack)
                && AdvancedMEOmniToolItem.isAdvancedSilkTouchEnabled(stack)) {
            breakBlockWithNBT(stack, level, pos, player);
            return;
        }

        // 基岩破坏：对硬度为 -1 的不可破坏方块特殊处理，直接掉落方块本身
        if (state.getDestroySpeed(level, pos) < 0.0F && level instanceof ServerLevel serverLevel) {
            List<ItemStack> drops = getDrops(state, serverLevel, pos, level.getBlockEntity(pos), player, stack);
            if (drops.isEmpty()) {
                Item item = block.asItem();
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    drops.add(new ItemStack(item, 1));
                }
            }
            handleDrops(level, player, pos, drops, stack);
            level.destroyBlock(pos, false);
            level.levelEvent(2001, pos, Block.getId(state));
            return;
        }

        // 普通破坏
        if (player.isCreative()) {
            level.destroyBlock(pos, false);
        } else {
            level.destroyBlock(pos, true, player);
        }
        level.levelEvent(2001, pos, Block.getId(state));
    }

    /**
     * 按工具当前的掉落模式分发掉落物：普通掉落、直接进入背包、或注入 AE 网络。
     */
    public static void handleDrops(Level level, Player player, BlockPos pos, List<ItemStack> drops, ItemStack tool) {
        if (level.isClientSide() || drops.isEmpty()) {
            return;
        }

        int dropMode = AdvancedMEOmniToolItem.getDropMode(tool);
        if (dropMode == AdvancedMEOmniToolItem.DROP_NORMAL) {
            for (ItemStack drop : drops) {
                ItemEntity entityItem = new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                entityItem.setPickUpDelay(10);
                level.addFreshEntity(entityItem);
            }
            return;
        }

        if (dropMode == AdvancedMEOmniToolItem.DROP_INVENTORY) {
            for (ItemStack drop : drops) {
                if (!player.getInventory().add(drop)) {
                    ItemEntity entityItem = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), drop);
                    level.addFreshEntity(entityItem);
                }
            }
        } else if (dropMode == AdvancedMEOmniToolItem.DROP_AE) {
            insertIntoNetwork(level, player, drops, tool);
        }
    }

    /**
     * 将掉落物注入绑定的 ME 网络；未绑定、网络不可用或注入剩余时在玩家脚下生成掉落物回退。
     */
    public static void insertIntoNetwork(Level level, Player player, List<ItemStack> drops, ItemStack tool) {
        IGrid grid = OmniToolNetworkLink.getLinkedGrid(tool, level);
        if (grid == null) {
            spawnAtPlayer(level, player, drops);
            return;
        }
        MEStorage storage = grid.getStorageService().getInventory();
        var energy = grid.getEnergyService();
        IActionSource source = IActionSource.ofPlayer(player);

        for (ItemStack drop : drops) {
            AEItemKey key = AEItemKey.of(drop);
            if (key == null) {
                spawnAtPlayer(level, player, List.of(drop));
                continue;
            }
            long inserted = StorageHelper.poweredInsert(energy, storage, key, drop.getCount(), source,
                    Actionable.MODULATE);
            long leftover = drop.getCount() - inserted;
            if (leftover > 0) {
                ItemStack overflow = key.toStack((int) leftover);
                spawnAtPlayer(level, player, List.of(overflow));
            }
        }
    }

    private static void spawnAtPlayer(Level level, Player player, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemEntity entityItem = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), drop);
            level.addFreshEntity(entityItem);
        }
    }

    /**
     * 高级精准采集：破坏方块并保留方块实体 NBT（附加到首个掉落物的 BlockEntityTag）。
     */
    private static boolean breakBlockWithNBT(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return true;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (isBlacklisted(block)) {
            return false;
        }

        // 1. 保存方块实体 NBT
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag beNbt = null;
        if (be != null) {
            beNbt = be.saveWithFullMetadata();
            beNbt.remove("x");
            beNbt.remove("y");
            beNbt.remove("z");
            beNbt.remove("id");
        }

        // 2. 移除方块实体，防止 onRemove 额外掉落内容物
        level.removeBlockEntity(pos);

        // 3. 获取掉落物
        List<ItemStack> drops = level instanceof ServerLevel serverLevel
                ? getDrops(state, serverLevel, pos, null, player, stack)
                : new ArrayList<>();
        if (drops.isEmpty()) {
            Item item = block.asItem();
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                drops.add(new ItemStack(item, 1));
            }
        }

        // 4. 给第一个掉落物附加 NBT
        if (beNbt != null && !drops.isEmpty()) {
            ItemStack mainDrop = drops.get(0);
            mainDrop.getOrCreateTag().put("BlockEntityTag", beNbt);
        }

        // 5. 按当前掉落模式分发掉落物
        handleDrops(level, player, pos, drops, stack);

        // 6. 破坏方块并移除（destroyBlock 触发 onRemove/destroy，等同于 1.12 的 breakBlock + setBlockToAir）
        level.destroyBlock(pos, false);
        return true;
    }

    /**
     * 以存储时运等级计算掉落物。使用仅带时运的副本工具，
     * 避免精准采集可见附魔影响自定义破坏路径的掉落表。
     */
    private static List<ItemStack> getDrops(BlockState state, ServerLevel level, BlockPos pos,
            @Nullable BlockEntity blockEntity, Player player, ItemStack tool) {
        int fortune = AdvancedMEOmniToolItem.getFortuneLevel(tool);
        ItemStack lootTool = tool.copy();
        if (lootTool.hasTag()) {
            lootTool.getTag().remove("Enchantments");
        }
        if (fortune > 0) {
            lootTool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        }
        return Block.getDrops(state, level, pos, blockEntity, player, lootTool);
    }

    public static boolean isBlacklisted(Block block) {
        long now = System.currentTimeMillis();
        if (blacklistCache == null || now - blacklistCacheTime > 5000L) {
            blacklistCache = new HashSet<>();
            for (String raw : AE2EnhancedConfig.COMMON.omniToolBreakableBlacklist.get()) {
                if (raw == null || raw.trim().isEmpty()) continue;
                try {
                    blacklistCache.add(new ResourceLocation(raw.trim()));
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Invalid breakable blacklist entry: {}", raw);
                }
            }
            blacklistCacheTime = now;
        }
        ResourceLocation reg = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
        return reg != null && blacklistCache.contains(reg);
    }

    // ==================== Break Cooldown ====================

    public static int getBreakCooldown(ItemStack stack) {
        return OmniToolUpgrades.getBreakCooldown(stack);
    }

    public static void setBreakCooldown(ItemStack stack, int ticks) {
        OmniToolUpgrades.setBreakCooldown(stack, ticks);
    }

    private static long getLastBreakTick(ItemStack stack) {
        return OmniToolUpgrades.getLastBreakTick(stack);
    }

    private static void setLastBreakTick(ItemStack stack, long tick) {
        OmniToolUpgrades.setLastBreakTick(stack, tick);
    }
}
