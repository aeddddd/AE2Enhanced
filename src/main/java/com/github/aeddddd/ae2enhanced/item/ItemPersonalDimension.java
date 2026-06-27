package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * 个人维度核心：右键空气打开配置，右键方块传送，Shift+右键方块绑定进入点。
 */
public class ItemPersonalDimension extends Item {

    public ItemPersonalDimension() {
        setRegistryName(AE2Enhanced.MOD_ID, "personal_dimension");
        setTranslationKey(AE2Enhanced.MOD_ID + ".personal_dimension");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            player.openGui(AE2Enhanced.instance, GuiHandler.GUI_PERSONAL_DIMENSION_CONFIG,
                    world, 0, 0, 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        if (player.isSneaking()) {
            return bindEntryPoint(player, world, pos);
        }
        return teleport(player, world);
    }

    private EnumActionResult bindEntryPoint(EntityPlayer player, World world, BlockPos pos) {
        int dimId = player.dimension;
        if (!PersonalDimensionManager.isPersonalDimension(dimId)) {
            player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.bind_only_in_dim"));
            return EnumActionResult.FAIL;
        }
        PersonalDimensionManager.setEntryPoint(player, pos.up());
        player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.bound",
                pos.getX(), pos.getY() + 1, pos.getZ()));
        return EnumActionResult.SUCCESS;
    }

    private EnumActionResult teleport(EntityPlayer player, World world) {
        if (!(player instanceof net.minecraft.entity.player.EntityPlayerMP)) {
            return EnumActionResult.SUCCESS;
        }
        net.minecraft.entity.player.EntityPlayerMP mp = (net.minecraft.entity.player.EntityPlayerMP) player;
        int dimId = PersonalDimensionManager.getOrCreateDimension(mp);
        if (dimId == Integer.MIN_VALUE) {
            player.sendMessage(new TextComponentTranslation("chat.ae2enhanced.personal_dimension.create_failed"));
            return EnumActionResult.FAIL;
        }

        if (player.dimension == dimId) {
            // 已在个人维度，返回上一次位置
            PersonalDimensionManager.teleportToReturnPoint(mp);
        } else {
            // 记录当前位置并进入个人维度
            PersonalDimensionManager.setReturnPoint(mp);
            PersonalDimensionManager.teleportToDimension(mp, dimId);
        }
        return EnumActionResult.SUCCESS;
    }
}
