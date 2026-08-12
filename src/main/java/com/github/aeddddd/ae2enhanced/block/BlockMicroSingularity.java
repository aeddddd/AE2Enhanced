package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * 微型奇点 — 仪式召唤的临时黑洞方块.
 * 不可破坏,发光,有较小的碰撞箱,300 秒后自动坍缩.
 * 玩家右键可主动触发黑洞合成(配方不匹配时不销毁物品);
 * 手持燃料物品右键可延长存在时间或使奇点永久存在.
 */
public class BlockMicroSingularity extends Block {

    private static final AxisAlignedBB BOX = new AxisAlignedBB(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);

    public BlockMicroSingularity() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "micro_singularity");
        setTranslationKey(AE2Enhanced.MOD_ID + ".micro_singularity");
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setLightLevel(1.0F);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOX;
    }

    @Override
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileMicroSingularity();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileMicroSingularity) {
                TileMicroSingularity singularity = (TileMicroSingularity) te;
                ItemStack held = player.getHeldItem(hand);
                if (held.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemSingularityConstrictor) {
                    convertToItem(world, pos, player, held, singularity);
                    return true;
                }
                SingularityFuelRecipe fuel = SingularityFuelRegistry.findFor(held);
                if (fuel != null) {
                    feedFuel(world, pos, player, held, singularity, fuel);
                } else {
                    singularity.activateCrafting();
                }
            }
        }
        return true;
    }

    /**
     * 奇点约束器右键：移除方块,约束器转化为携带剩余寿命与永久标记的奇点物品.
     */
    private static void convertToItem(World world, BlockPos pos, EntityPlayer player, ItemStack constrictor,
                                      TileMicroSingularity singularity) {
        ItemStack singularityStack = com.github.aeddddd.ae2enhanced.item.ItemConstrainedMicroSingularity
                .createStack(singularity.getLifetimeTicks(), singularity.isPermanent());
        if (!player.isCreative()) {
            constrictor.shrink(1);
        }
        world.setBlockToAir(pos);
        if (!player.addItemStackToInventory(singularityStack)) {
            player.dropItem(singularityStack, false);
        }
        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ENDEREYE_DEATH,
                SoundCategory.BLOCKS, 1.0f, 0.6f);
    }

    /**
     * 喂入燃料：延长存在时间,或使奇点永久存在.
     */
    private static void feedFuel(World world, BlockPos pos, EntityPlayer player, ItemStack held,
                                 TileMicroSingularity singularity, SingularityFuelRecipe fuel) {
        // 已永久存在的奇点无需再喂燃料,避免白白消耗
        if (singularity.isPermanent()) {
            return;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        if (fuel.isPermanent()) {
            singularity.setPermanent(true);
        } else {
            singularity.addLifetimeTicks(fuel.getTicks());
        }
        ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD, false,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.02);
        world.playSound(null, pos,
                SoundEvent.REGISTRY.getObject(new ResourceLocation("block.beacon.power_select")),
                SoundCategory.BLOCKS, 0.8f, 1.5f);
    }
}
