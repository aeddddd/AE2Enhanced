package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/** Tile 侧读取方块状态，不反向依赖具体 Block 实现。 */
final class TileBlockStates {

    static final String ASSEMBLY_CONTROLLER = "ae2enhanced:assembly_controller";
    static final String ASSEMBLY_ME_INTERFACE = "ae2enhanced:assembly_me_interface";
    static final String ASSEMBLY_CASING = "ae2enhanced:assembly_casing";
    static final String ASSEMBLY_INNER_WALL = "ae2enhanced:assembly_inner_wall";
    static final String ASSEMBLY_STABILIZER = "ae2enhanced:assembly_stabilizer";
    static final String CONSTANT_TENSOR_FIELD_CASING = "ae2enhanced:constant_tensor_field_casing";
    static final String CONSTANT_SPINOR_FIELD_CASING = "ae2enhanced:constant_spinor_field_casing";
    static final String CAUSAL_ANCHOR_CORE = "ae2enhanced:causal_anchor_core";
    static final String COMPUTATION_CORE = "ae2enhanced:computation_core";
    static final String HYPERDIMENSIONAL_CONTROLLER = "ae2enhanced:hyperdimensional_controller";
    static final String SUPER_CRAFTING_INTERFACE = "ae2enhanced:super_crafting_interface";
    static final String DISPLAY_PANEL = "ae2enhanced:display_panel";
    static final String DISPLAY_FRAME_LIGHT = "ae2enhanced:display_frame_light";
    static final String DISPLAY_FRAME_DARK = "ae2enhanced:display_frame_dark";

    private TileBlockStates() {
    }

    static boolean is(IBlockState state, String registryName) {
        return state.getBlock().getRegistryName() != null
                && registryName.equals(state.getBlock().getRegistryName().toString());
    }

    static EnumFacing facing(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("facing".equals(property.getName())) {
                Object value = state.getValue(property);
                if (value instanceof EnumFacing) {
                    return (EnumFacing) value;
                }
            }
        }
        return EnumFacing.NORTH;
    }

    static boolean formed(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("formed".equals(property.getName())) {
                Object value = state.getValue(property);
                return value instanceof Boolean && (Boolean) value;
            }
        }
        return false;
    }

    static IBlockState formed(IBlockState state, boolean formed) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("formed".equals(property.getName()) && property.getValueClass() == Boolean.class) {
                return state.withProperty((IProperty<Boolean>) property, formed);
            }
        }
        return state;
    }

    static Boolean frameLight(IBlockState state) {
        if (is(state, DISPLAY_FRAME_LIGHT)) {
            return Boolean.TRUE;
        }
        if (is(state, DISPLAY_FRAME_DARK)) {
            return Boolean.FALSE;
        }
        return null;
    }

    static net.minecraft.block.Block block(String registryName) {
        return net.minecraft.block.Block.getBlockFromName(registryName);
    }
}
