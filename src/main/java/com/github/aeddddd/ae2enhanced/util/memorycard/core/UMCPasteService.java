package com.github.aeddddd.ae2enhanced.util.memorycard.core;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.List;

/**
 * UMC 粘贴逻辑服务(含批量粘贴).
 */
public class UMCPasteService {

    public static void handlePaste(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        if (!ItemUniversalMemoryCard.hasConfig(stack)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.no_config"));
            return;
        }

        NBTTagCompound config = ItemUniversalMemoryCard.getConfig(stack);
        NBTTagCompound data = config.getCompoundTag("data");

        World world = player.world;
        Object target = findTarget(world, pos, face);
        if (target == null) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.paste_invalid"));
            return;
        }

        List<ItemUniversalMemoryCard.SelectionEntry> selections = ItemUniversalMemoryCard.getSelections(stack);
        boolean isBulk = false;
        for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
            if (entry.dim == world.provider.getDimension() && entry.pos.equals(pos)) {
                isBulk = true;
                break;
            }
        }

        if (isBulk) {
            int success = 0;
            int failed = 0;
            for (ItemUniversalMemoryCard.SelectionEntry entry : selections) {
                if (entry.dim != world.provider.getDimension()) continue;
                Object bulkTarget = resolveTarget(world, entry);
                if (bulkTarget == null) continue;
                IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(bulkTarget);
                if (handler == null) continue;
                PasteResult result = handler.paste(bulkTarget, data, player);
                if (result == PasteResult.SUCCESS) success++;
                else failed++;
            }
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.bulk_success", success, failed));
        } else {
            IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
            if (handler == null) {
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.paste_unsupported"));
                return;
            }
            PasteResult result = handler.paste(target, data, player);
            switch (result) {
                case SUCCESS:
                    player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.paste_success", handler.getDisplayName(target)));
                    break;
                case MISSING_UPGRADES:
                    StringBuilder req = new StringBuilder();
                    appendUpgradeNames(req, data, "ae2e:upgrades");
                    appendUpgradeNames(req, data, "eio:upgrades");
                    appendUpgradeNames(req, data, "Augments");
                    player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.missing_upgrades", req.toString()));
                    break;
                case INVALID_MACHINE:
                    player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.invalid_machine"));
                    break;
                case FAILED:
                    player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.paste_failed"));
                    break;
            }
        }
    }

    private static void appendUpgradeNames(StringBuilder req, NBTTagCompound data, String key) {
        if (!data.hasKey(key)) return;
        NBTTagList upgList = data.getTagList(key, 10);
        for (int i = 0; i < upgList.tagCount(); i++) {
            NBTTagCompound tag = upgList.getCompoundTagAt(i);
            ItemStack upg = new ItemStack(tag);
            if (!upg.isEmpty()) {
                if (req.length() > 0) req.append(", ");
                req.append(upg.getDisplayName());
                if (upg.getCount() > 1) req.append("×").append(upg.getCount());
            }
        }
    }

    private static Object findTarget(World world, BlockPos pos, EnumFacing face) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;
            IPart part = host.getPart(AEPartLocation.fromFacing(face));
            if (part != null) return part;
        }
        if (te != null) return te;
        return null;
    }

    private static Object resolveTarget(World world, ItemUniversalMemoryCard.SelectionEntry entry) {
        if (entry.dim != world.provider.getDimension()) return null;
        if (!world.isBlockLoaded(entry.pos)) return null;
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(entry.pos);
        if (te == null) return null;
        if (entry.side >= 0 && te instanceof IPartHost) {
            IPart part = ((IPartHost) te).getPart(AEPartLocation.fromOrdinal(entry.side));
            if (part != null) return part;
        }
        return te;
    }
}
