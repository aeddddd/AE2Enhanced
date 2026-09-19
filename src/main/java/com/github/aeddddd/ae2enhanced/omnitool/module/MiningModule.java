package com.github.aeddddd.ae2enhanced.omnitool.module;

import appeng.api.AEApi;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.network.WirelessTransmitterNetworkLink;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 通用/旅行模式下的挖掘破坏逻辑。
 */
public class MiningModule implements IOmniToolModule {

    private static final float DESTROY_SPEED = 1_000_000.0f;

    // ---- Blacklist cache ----
    private static Set<ResourceLocation> blacklistCache = null;
    private static long blacklistCacheTime = -1L;

    @Override
    public int getMode() {
        return ItemAdvancedMEOmniTool.MODE_UNIVERSAL;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        int mode = ItemAdvancedMEOmniTool.getMode(stack);
        if (mode != ItemAdvancedMEOmniTool.MODE_UNIVERSAL && mode != ItemAdvancedMEOmniTool.MODE_TRAVEL) return 1.0f;
        if (isBlacklisted(state.getBlock())) return 0.0f;
        return DESTROY_SPEED;
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        int mode = ItemAdvancedMEOmniTool.getMode(stack);
        if (mode != ItemAdvancedMEOmniTool.MODE_UNIVERSAL && mode != ItemAdvancedMEOmniTool.MODE_TRAVEL) return false;
        return !isBlacklisted(state.getBlock());
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        int mode = ItemAdvancedMEOmniTool.getMode(stack);
        if (mode != ItemAdvancedMEOmniTool.MODE_UNIVERSAL && mode != ItemAdvancedMEOmniTool.MODE_TRAVEL) return -1;
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        int mode = ItemAdvancedMEOmniTool.getMode(stack);
        if (mode != ItemAdvancedMEOmniTool.MODE_UNIVERSAL && mode != ItemAdvancedMEOmniTool.MODE_TRAVEL) {
            return false;
        }

        // 混沌核心：允许破坏 DE 混沌水晶（即使 hardness = -1）
        if (ItemAdvancedMEOmniTool.hasChaosCore(stack) && isChaosCrystal(player.world, pos)) {
            IBlockState state = player.world.getBlockState(pos);
            Block block = state.getBlock();
            if (!player.world.isRemote) {
                block.dropBlockAsItem(player.world, pos, state, ItemAdvancedMEOmniTool.getFortuneLevel(stack));
                player.world.setBlockToAir(pos);
                block.breakBlock(player.world, pos, state);
            }
            return true;
        }

        int cooldown = getBreakCooldown(stack);
        if (cooldown > 0) {
            long now = player.world.getTotalWorldTime();
            long last = getLastBreakTick(stack);
            if (now - last < cooldown) {
                // 同步方块状态到客户端，防止幽灵方块
                if (!player.world.isRemote && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                    ((net.minecraft.entity.player.EntityPlayerMP) player).connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketBlockChange(player.world, pos));
                }
                return true;
            }
            setLastBreakTick(stack, now);
        }

        // 基岩破坏者：允许破坏硬度为 -1 的不可破坏方块
        if (ItemAdvancedMEOmniTool.hasBedrockBreaker(stack)) {
            IBlockState state = player.world.getBlockState(pos);
            Block block = state.getBlock();
            if (block.getBlockHardness(state, player.world, pos) < 0.0F) {
                if (!player.world.isRemote) {
                    List<ItemStack> drops = block.getDrops(player.world, pos, state, ItemAdvancedMEOmniTool.getFortuneLevel(stack));
                    if (drops.isEmpty()) {
                        Item item = Item.getItemFromBlock(block);
                        if (item != null && item != net.minecraft.init.Items.AIR) {
                            drops.add(new ItemStack(item, 1, block.damageDropped(state)));
                        }
                    }
                    handleDrops(player.world, player, pos, drops, stack);
                    player.world.setBlockToAir(pos);
                    block.breakBlock(player.world, pos, state);
                }
                return true;
            }
        }

        // 高级精准采集：保留方块 NBT 掉落
        if (ItemAdvancedMEOmniTool.isSilkTouchEnabled(stack) && ItemAdvancedMEOmniTool.isAdvancedSilkTouchEnabled(stack)) {
            return breakBlockWithNBT(stack, player.world, pos, player);
        }

        return false;
    }

    @Override
    public void addTooltip(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        int cooldown = getBreakCooldown(stack);
        tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.break_cooldown",
                TextFormatting.YELLOW + String.valueOf(cooldown)));

        int dropMode = ItemAdvancedMEOmniTool.getDropMode(stack);
        String dropModeName = net.minecraft.client.resources.I18n.format(ItemAdvancedMEOmniTool.getDropModeNameKey(dropMode));
        tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                + net.minecraft.client.resources.I18n.format("item.ae2enhanced.me_omni_tool.drop_mode",
                TextFormatting.YELLOW + dropModeName));
    }

    public static void forceBreakBlock(EntityPlayer player, World world, BlockPos pos, ItemStack stack) {
        if (world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (isBlacklisted(block)) return;

        int cooldown = getBreakCooldown(stack);
        if (cooldown > 0) {
            long now = world.getTotalWorldTime();
            long last = getLastBreakTick(stack);
            if (now - last < cooldown) {
                if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                    ((net.minecraft.entity.player.EntityPlayerMP) player).connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketBlockChange(world, pos));
                }
                return;
            }
            setLastBreakTick(stack, now);
        }

        // 高级精准采集：保留方块 NBT 掉落
        if (ItemAdvancedMEOmniTool.isSilkTouchEnabled(stack) && ItemAdvancedMEOmniTool.isAdvancedSilkTouchEnabled(stack)) {
            breakBlockWithNBT(stack, world, pos, player);
            return;
        }

        // 基岩破坏：对硬度为 -1 的不可破坏方块特殊处理，直接掉落方块本身
        if (block.getBlockHardness(state, world, pos) < 0.0F) {
            List<ItemStack> drops = block.getDrops(world, pos, state, ItemAdvancedMEOmniTool.getFortuneLevel(stack));
            if (drops.isEmpty()) {
                Item item = Item.getItemFromBlock(block);
                if (item != null && item != net.minecraft.init.Items.AIR) {
                    drops.add(new ItemStack(item, 1, block.damageDropped(state)));
                }
            }
            handleDrops(world, player, pos, drops, stack);
            world.setBlockToAir(pos);
            block.breakBlock(world, pos, state);
            world.playEvent(2001, pos, net.minecraft.block.Block.getStateId(state));
            return;
        }

        // 普通破坏
        TileEntity te = world.getTileEntity(pos);
        if (player.capabilities.isCreativeMode) {
            world.setBlockToAir(pos);
        } else {
            block.harvestBlock(world, player, pos, state, te, stack);
        }
        world.playEvent(2001, pos, net.minecraft.block.Block.getStateId(state));
    }

    /**
     * 按工具当前的掉落模式分发掉落物：普通掉落、直接进入背包、或注入 AE 网络。
     */
    public static void handleDrops(World world, EntityPlayer player, BlockPos pos, List<ItemStack> drops, ItemStack tool) {
        if (world.isRemote || drops.isEmpty()) {
            return;
        }

        int dropMode = ItemAdvancedMEOmniTool.getDropMode(tool);
        if (dropMode == ItemAdvancedMEOmniTool.DROP_NORMAL) {
            for (ItemStack drop : drops) {
                EntityItem entityItem = new EntityItem(world,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                entityItem.setPickupDelay(10);
                world.spawnEntity(entityItem);
            }
            return;
        }

        if (dropMode == ItemAdvancedMEOmniTool.DROP_INVENTORY) {
            for (ItemStack drop : drops) {
                if (!player.inventory.addItemStackToInventory(drop)) {
                    EntityItem entityItem = new EntityItem(world, player.posX, player.posY, player.posZ, drop);
                    world.spawnEntity(entityItem);
                }
            }
        } else if (dropMode == ItemAdvancedMEOmniTool.DROP_AE) {
            IMEMonitor<IAEItemStack> monitor = WirelessTransmitterNetworkLink.getItemMonitor(tool, world);
            if (monitor == null) {
                for (ItemStack drop : drops) {
                    EntityItem entityItem = new EntityItem(world, player.posX, player.posY, player.posZ, drop);
                    world.spawnEntity(entityItem);
                }
                return;
            }

            IActionSource source = new IActionSource() {
                @Override
                public Optional<EntityPlayer> player() {
                    return Optional.of(player);
                }

                @Override
                public Optional<appeng.api.networking.security.IActionHost> machine() {
                    return Optional.empty();
                }

                @Override
                public <T> Optional<T> context(Class<T> key) {
                    return Optional.empty();
                }
            };

            for (ItemStack drop : drops) {
                IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(drop);
                if (aeStack != null) {
                    IAEItemStack leftover = monitor.injectItems(aeStack, appeng.api.config.Actionable.MODULATE, source);
                    if (leftover != null && leftover.getStackSize() > 0) {
                        ItemStack overflow = leftover.createItemStack();
                        EntityItem entityItem = new EntityItem(world, player.posX, player.posY, player.posZ, overflow);
                        world.spawnEntity(entityItem);
                    }
                } else {
                    EntityItem entityItem = new EntityItem(world, player.posX, player.posY, player.posZ, drop);
                    world.spawnEntity(entityItem);
                }
            }
        }
    }

    private static boolean breakBlockWithNBT(ItemStack stack, World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) return true;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (isBlacklisted(block)) {
            return false;
        }

        // 1. 保存 TileEntity NBT
        TileEntity te = world.getTileEntity(pos);
        NBTTagCompound teNbt = null;
        if (te != null) {
            teNbt = new NBTTagCompound();
            te.writeToNBT(teNbt);
            teNbt.removeTag("x");
            teNbt.removeTag("y");
            teNbt.removeTag("z");
            teNbt.removeTag("id");
        }

        // 2. 移除 TileEntity，防止 breakBlock 额外掉落内容物
        world.removeTileEntity(pos);

        // 3. 获取掉落物
        List<ItemStack> drops = block.getDrops(world, pos, state, ItemAdvancedMEOmniTool.getFortuneLevel(stack));
        if (drops.isEmpty()) {
            Item item = Item.getItemFromBlock(block);
            if (item != null && item != net.minecraft.init.Items.AIR) {
                drops.add(new ItemStack(item, 1, block.damageDropped(state)));
            }
        }

        // 4. 给第一个掉落物附加 NBT
        if (teNbt != null && !drops.isEmpty()) {
            ItemStack mainDrop = drops.get(0);
            NBTTagCompound tag = mainDrop.hasTagCompound() ? mainDrop.getTagCompound() : new NBTTagCompound();
            tag.setTag("BlockEntityTag", teNbt);
            mainDrop.setTagCompound(tag);
        }

        // 5. 按当前掉落模式分发掉落物
        handleDrops(world, player, pos, drops, stack);

        // 6. 破坏方块并移除
        block.breakBlock(world, pos, state);
        world.setBlockToAir(pos);
        return true;
    }

    public static boolean isBlacklisted(Block block) {
        long now = System.currentTimeMillis();
        if (blacklistCache == null || now - blacklistCacheTime > 5000L) {
            blacklistCache = new HashSet<>();
            for (String raw : AE2EnhancedConfig.omniTool.breakableBlacklist) {
                if (raw == null || raw.trim().isEmpty()) continue;
                try {
                    blacklistCache.add(new ResourceLocation(raw.trim()));
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Invalid breakable blacklist entry: {}", raw);
                }
            }
            blacklistCacheTime = now;
        }
        ResourceLocation reg = block.getRegistryName();
        return reg != null && blacklistCache.contains(reg);
    }

    private static boolean isChaosCrystal(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return "com.brandon3055.draconicevolution.blocks.ChaosCrystal".equals(block.getClass().getName());
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
