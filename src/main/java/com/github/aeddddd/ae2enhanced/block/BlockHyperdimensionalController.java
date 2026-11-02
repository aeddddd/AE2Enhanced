package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guiapi.GuiCatalog;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;

public class BlockHyperdimensionalController extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockHyperdimensionalController() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "hyperdimensional_controller");
        setTranslationKey(AE2Enhanced.MOD_ID + ".hyperdimensional_controller");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileHyperdimensionalController();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) {
            ControllerIndex index = ControllerIndex.get(world);
            if (index != null) {
                index.add(pos);
            }
        }
    }

    public static UUID readNexusId(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return null;
        if (tag.hasUniqueId("nexusId")) return tag.getUniqueId("nexusId");
        if (tag.hasKey("BlockEntityTag", 10)) {
            NBTTagCompound tileTag = tag.getCompoundTag("BlockEntityTag");
            if (tileTag.hasUniqueId("nexusId")) return tileTag.getUniqueId("nexusId");
        }
        return null;
    }

    @Override
    public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, net.minecraft.world.IBlockAccess world,
                         BlockPos pos, IBlockState state, int fortune) {
        ItemStack stack = new ItemStack(this);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileHyperdimensionalController) {
            UUID nexusId = ((TileHyperdimensionalController) te).getNexusId();
            if (nexusId != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setUniqueId("nexusId", nexusId);
                stack.setTagCompound(tag);
            }
        }
        drops.add(stack);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileHyperdimensionalController)) return;
        UUID nexusId = readNexusId(stack);
        if (nexusId != null) {
            ((TileHyperdimensionalController) te).setNexusId(nexusId);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            ControllerIndex index = ControllerIndex.get(world);
            if (index != null) {
                index.remove(pos);
            }
            HyperdimensionalStructure.disassemble(world, pos);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileHyperdimensionalController) {
                TileHyperdimensionalController tile = (TileHyperdimensionalController) te;
                com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult result = HyperdimensionalStructure.validate(world, pos);
                if (result == com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult.VALID && !tile.isFormed()) {
                    HyperdimensionalStructure.assemble(world, pos);
                } else if (result == com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult.INVALID && tile.isFormed()) {
                    HyperdimensionalStructure.disassemble(world, pos);
                } else if (result == com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult.DEFER_UNLOADED) {
                    StructureEventHandler.defer(world, pos);
                }
                int gui = tile.isFormed() ? GuiCatalog.GUI_HYPERDIMENSIONAL_NEXUS : GuiCatalog.GUI_HYPERDIMENSIONAL_UNFORMED;
                player.openGui(AE2Enhanced.instance, gui, world, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }
}
