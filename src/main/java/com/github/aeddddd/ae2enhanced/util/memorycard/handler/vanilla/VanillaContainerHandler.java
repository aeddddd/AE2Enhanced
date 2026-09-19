package com.github.aeddddd.ae2enhanced.util.memorycard.handler.vanilla;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardUpgradeHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版容器(箱子/漏斗/熔炉/发射器等 IInventory)的内容快照复制粘贴 Handler.
 *
 * <p>复制: 记录每个槽位的物品与数量(快照).
 * 粘贴(只补缺失): 对每个快照槽位,若容器内同类物品少于快照,从绑定的
 * AE 网络(安全终端)或玩家背包拉取差额填入; 被其他物品占用的槽位跳过,
 * 容器内多余物品不做处理.</p>
 *
 * <p>功能由配置 {@code memoryCard.vanillaContainerCopy} 控制(默认开启).
 * 本 handler 注册在处理器列表末尾,模组设备优先由各自的 handler 处理.</p>
 */
public class VanillaContainerHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        if (!AE2EnhancedConfig.memoryCard.vanillaContainerCopy) return false;
        return target instanceof IInventory && target instanceof TileEntity;
    }

    @Override
    public NBTTagCompound copy(Object target) {
        IInventory inv = (IInventory) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            NBTTagList contents = new NBTTagList();
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Slot", i);
                stack.writeToNBT(tag);
                contents.appendTag(tag);
            }
            if (!contents.isEmpty()) {
                output.setTag("vanilla:contents", contents);
            }
            output.setInteger("vanilla:slots", inv.getSizeInventory());
            TileEntity tile = (TileEntity) target;
            output.setString("dataType", tile.getBlockType().getRegistryName().toString());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy vanilla container contents", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        IInventory inv = (IInventory) target;

        try {
            TileEntity tile = (TileEntity) target;
            if (data.hasKey("dataType")
                    && !data.getString("dataType").equals(tile.getBlockType().getRegistryName().toString())) {
                return PasteResult.INVALID_MACHINE;
            }
            if (data.getInteger("vanilla:slots") != inv.getSizeInventory()) {
                return PasteResult.INVALID_MACHINE;
            }
            if (!data.hasKey("vanilla:contents")) {
                return PasteResult.FAILED;
            }

            NBTTagList contents = data.getTagList("vanilla:contents", 10);
            int filledStacks = 0;
            long filledTotal = 0;
            int missingStacks = 0;

            for (int i = 0; i < contents.tagCount(); i++) {
                NBTTagCompound tag = contents.getCompoundTagAt(i);
                int slot = tag.getInteger("Slot");
                if (slot < 0 || slot >= inv.getSizeInventory()) continue;
                ItemStack snapshot = new ItemStack(tag);
                if (snapshot.isEmpty()) continue;

                ItemStack current = inv.getStackInSlot(slot);
                int need;
                if (current.isEmpty()) {
                    need = snapshot.getCount();
                } else if (ItemStack.areItemsEqual(current, snapshot)
                        && ItemStack.areItemStackTagsEqual(current, snapshot)) {
                    need = snapshot.getCount() - current.getCount();
                } else {
                    // 槽位被其他物品占用,跳过(不移位/不弹出)
                    continue;
                }
                int limit = Math.min(inv.getInventoryStackLimit(), snapshot.getMaxStackSize());
                need = Math.min(need, limit - (current.isEmpty() ? 0 : current.getCount()));
                if (need <= 0) continue;

                // 拉取(背包 + 绑定的 AE 网络)
                ItemStack want = snapshot.copy();
                want.setCount(need);
                List<ItemStack> wanted = new ArrayList<>();
                wanted.add(want);
                if (!MemoryCardUpgradeHelper.ensureAvailable(player, wanted)) {
                    missingStacks++;
                    continue;
                }
                MemoryCardUpgradeHelper.consumeFromInventory(player, want);

                if (current.isEmpty()) {
                    inv.setInventorySlotContents(slot, want.copy());
                } else {
                    current.grow(need);
                }
                filledStacks++;
                filledTotal += need;
            }

            inv.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            if (missingStacks > 0) {
                player.sendMessage(new TextComponentTranslation(
                        "gui.ae2enhanced.umc.msg.container_paste_partial", filledStacks, missingStacks));
            } else {
                player.sendMessage(new TextComponentTranslation(
                        "gui.ae2enhanced.umc.msg.container_paste", filledTotal));
            }
            return PasteResult.SUCCESS_CUSTOM;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste vanilla container contents", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
