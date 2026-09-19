package com.github.aeddddd.ae2enhanced.util.memorycard.handler.industrialforegoing;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

import java.util.List;

/**
 * Industrial Foregoing 机器的配置复制粘贴 Handler.
 *
 * <p>IF 机器基于 TeslaCoreLib 的 SidedTileEntity/ElectricMachine,配置通过
 * SyncTileEntity 的 syncParts 机制持久化在 NBT 顶层:</p>
 * <ul>
 *   <li>{@code side_config} (list) - 侧面颜色配置</li>
 *   <li>{@code redstone} (string) - 红石模式(AlwaysActive/RedstoneOn/RedstoneOff)</li>
 *   <li>{@code paused} (byte) - 暂停状态</li>
 *   <li>{@code addonItems} (compound, ItemStackHandler 格式) - 升级插件槽</li>
 * </ul>
 *
 * <p>实现全程仅操作 NBT,不引用任何 IF/TeslaCoreLib 类,满足反射隔离约定.</p>
 */
public class IndustrialForegoingMachineHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    static {
        AVAILABLE = Loader.isModLoaded("industrialforegoing");
    }

    @Override
    public boolean canHandle(Object target) {
        if (!AVAILABLE || !(target instanceof TileEntity)) return false;
        return target.getClass().getName().startsWith("com.buuz135.industrial.tile.");
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            NBTTagCompound fullNbt = tile.writeToNBT(new NBTTagCompound());

            for (String key : new String[]{"side_config", "redstone", "paused"}) {
                if (fullNbt.hasKey(key)) {
                    output.setTag(key, fullNbt.getTag(key));
                }
            }

            // 升级插件:addonItems 的 Items 列表与 ae2e:upgrades 格式一致(Slot + ItemStack)
            if (fullNbt.hasKey("addonItems")) {
                NBTTagList items = fullNbt.getCompoundTag("addonItems").getTagList("Items", 10);
                if (!items.isEmpty()) {
                    output.setTag("ae2e:upgrades", items.copy());
                }
            }

            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy IF machine config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            // IF 机器配置结构差异大,要求完全同类
            if (data.hasKey("dataType") && !data.getString("dataType").equals(target.getClass().getName())) {
                return PasteResult.INVALID_MACHINE;
            }

            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // ===== 升级插件(先验证,再弹出旧插件,消耗新插件) =====
            if (data.hasKey("ae2e:upgrades")) {
                NBTTagList upgradeList = data.getTagList("ae2e:upgrades", 10);
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(upgradeList);
                if (!needed.isEmpty()) {
                    if (!MemoryCardUpgradeHelper.ensureAvailable(player, needed)) {
                        return PasteResult.MISSING_UPGRADES;
                    }

                    // 弹出旧插件
                    if (currentNbt.hasKey("addonItems")) {
                        NBTTagList oldItems = currentNbt.getCompoundTag("addonItems").getTagList("Items", 10);
                        for (int i = 0; i < oldItems.tagCount(); i++) {
                            ItemStack old = new ItemStack(oldItems.getCompoundTagAt(i));
                            if (!old.isEmpty() && !player.addItemStackToInventory(old)) {
                                player.world.spawnEntity(new EntityItem(player.world,
                                        player.posX, player.posY, player.posZ, old));
                            }
                        }
                    }

                    // 消耗并写入新插件
                    for (ItemStack need : needed) {
                        MemoryCardUpgradeHelper.consumeFromInventory(player, need);
                    }
                    NBTTagCompound addonItems = currentNbt.hasKey("addonItems")
                            ? currentNbt.getCompoundTag("addonItems")
                            : new NBTTagCompound();
                    NBTTagList newItems = new NBTTagList();
                    int slot = 0;
                    for (ItemStack need : needed) {
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setInteger("Slot", slot++);
                        need.writeToNBT(tag);
                        newItems.appendTag(tag);
                    }
                    addonItems.setTag("Items", newItems);
                    if (!addonItems.hasKey("Size")) {
                        addonItems.setInteger("Size", Math.max(4, needed.size()));
                    }
                    currentNbt.setTag("addonItems", addonItems);
                }
            }

            // ===== 其余配置 =====
            for (String key : new String[]{"side_config", "redstone", "paused"}) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            tile.readFromNBT(currentNbt);
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste IF machine config", e);
            return PasteResult.FAILED;
        }
    }

    // ===== 键分类声明(粘贴选项过滤用) =====

    @Override
    public java.util.Set<String> getUpgradeKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("ae2e:upgrades", "addonItems"));
    }

    @Override
    public java.util.Set<String> getSideKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("side_config"));
    }

    @Override
    public java.util.Set<String> getRedstoneKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("redstone"));
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
