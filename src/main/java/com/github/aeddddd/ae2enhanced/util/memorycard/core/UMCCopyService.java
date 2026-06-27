package com.github.aeddddd.ae2enhanced.util.memorycard.core;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * UMC 复制逻辑服务.
 */
public class UMCCopyService {

    public static void handleCopy(EntityPlayer player, ItemStack stack, BlockPos pos, EnumFacing face) {
        World world = player.world;
        Object target = findTarget(world, pos, face);
        if (target == null) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_invalid"));
            return;
        }

        IMemoryCardHandler handler = MemoryCardHandlerRegistry.findHandler(target);
        if (handler == null) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_unsupported"));
            return;
        }

        NBTTagCompound data = handler.copy(target);
        if (data == null || data.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_empty", handler.getDisplayName(target)));
            return;
        }

        String handlerId;
        if (target instanceof appeng.parts.AEBasePart) handlerId = "ae2_part";
        else if (target instanceof appeng.tile.AEBaseTile) handlerId = "ae2_tile";
        else handlerId = "ae2e_custom";

        ItemUniversalMemoryCard.setConfig(stack, handlerId, handler.getDisplayName(target), data);
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_success", handler.getDisplayName(target)));
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
}
