package com.github.aeddddd.ae2enhanced.tile;

import com.github.aeddddd.ae2enhanced.config.ServerConfig;
import com.github.aeddddd.ae2enhanced.structureapi.StructureDeferral;
import com.github.aeddddd.ae2enhanced.structureapi.StructureValidationResult;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * 超因果计算核心的多方块结构验证系统
 * 坐标原点为控制器位置 (0,0,0),对应 JSON 中的 (0,0,6).
 */
public class SupercausalStructure {

    public static final BlockPos CONTROLLER_REL = new BlockPos(0, 0, 0);
    public static final BlockPos ME_INTERFACE_REL = new BlockPos(0, 0, -6);

    public static final Set<BlockPos> TENSOR_CASING_SET;
    public static final Set<BlockPos> CAUSAL_ANCHOR_SET;
    public static final Set<BlockPos> SPINOR_CASING_SET;
    public static final Set<BlockPos> ALL_STRUCTURE_SET;

    static {
        Set<BlockPos> tensor = new HashSet<>();
        tensor.add(new BlockPos(-12, 0, 2));
        tensor.add(new BlockPos(-12, 0, 3));
        tensor.add(new BlockPos(-12, 0, 4));
        tensor.add(new BlockPos(-12, 0, 5));
        tensor.add(new BlockPos(-12, 0, 6));
        tensor.add(new BlockPos(-12, 0, 7));
        tensor.add(new BlockPos(-12, 0, 8));
        tensor.add(new BlockPos(-12, 0, 9));
        tensor.add(new BlockPos(-12, 0, 10));
        tensor.add(new BlockPos(-11, 0, 0));
        tensor.add(new BlockPos(-11, 0, 1));
        tensor.add(new BlockPos(-11, 0, 6));
        tensor.add(new BlockPos(-11, 0, 7));
        tensor.add(new BlockPos(-10, 0, -1));
        tensor.add(new BlockPos(-10, 0, 4));
        tensor.add(new BlockPos(-10, 0, 5));
        tensor.add(new BlockPos(-9, 0, -2));
        tensor.add(new BlockPos(-9, 0, 3));
        tensor.add(new BlockPos(-9, 0, 4));
        tensor.add(new BlockPos(-8, 0, -3));
        tensor.add(new BlockPos(-8, 0, 2));
        tensor.add(new BlockPos(-8, 0, 3));
        tensor.add(new BlockPos(-7, 0, -4));
        tensor.add(new BlockPos(-7, 0, 2));
        tensor.add(new BlockPos(-6, 0, -5));
        tensor.add(new BlockPos(-6, 0, 1));
        tensor.add(new BlockPos(-5, 0, -5));
        tensor.add(new BlockPos(-5, 0, 1));
        tensor.add(new BlockPos(-4, 0, -6));
        tensor.add(new BlockPos(-4, 0, 0));
        tensor.add(new BlockPos(-3, 0, -6));
        tensor.add(new BlockPos(-3, 0, 0));
        tensor.add(new BlockPos(-2, 0, -6));
        tensor.add(new BlockPos(-2, 0, 0));
        tensor.add(new BlockPos(-1, 0, -6));
        tensor.add(new BlockPos(-1, 0, 0));
        tensor.add(new BlockPos(0, -12, 2));
        tensor.add(new BlockPos(0, -12, 3));
        tensor.add(new BlockPos(0, -12, 4));
        tensor.add(new BlockPos(0, -12, 5));
        tensor.add(new BlockPos(0, -12, 6));
        tensor.add(new BlockPos(0, -12, 7));
        tensor.add(new BlockPos(0, -12, 8));
        tensor.add(new BlockPos(0, -12, 9));
        tensor.add(new BlockPos(0, -12, 10));
        tensor.add(new BlockPos(0, -11, 0));
        tensor.add(new BlockPos(0, -11, 1));
        tensor.add(new BlockPos(0, -11, 6));
        tensor.add(new BlockPos(0, -11, 7));
        tensor.add(new BlockPos(0, -10, -1));
        tensor.add(new BlockPos(0, -10, 4));
        tensor.add(new BlockPos(0, -10, 5));
        tensor.add(new BlockPos(0, -9, -2));
        tensor.add(new BlockPos(0, -9, 3));
        tensor.add(new BlockPos(0, -9, 4));
        tensor.add(new BlockPos(0, -8, -3));
        tensor.add(new BlockPos(0, -8, 2));
        tensor.add(new BlockPos(0, -8, 3));
        tensor.add(new BlockPos(0, -7, -4));
        tensor.add(new BlockPos(0, -7, 2));
        tensor.add(new BlockPos(0, -6, -5));
        tensor.add(new BlockPos(0, -6, 1));
        tensor.add(new BlockPos(0, -5, -5));
        tensor.add(new BlockPos(0, -5, 1));
        tensor.add(new BlockPos(0, -4, -6));
        tensor.add(new BlockPos(0, -4, 0));
        tensor.add(new BlockPos(0, -3, -6));
        tensor.add(new BlockPos(0, -3, 0));
        tensor.add(new BlockPos(0, -2, -6));
        tensor.add(new BlockPos(0, -2, 0));
        tensor.add(new BlockPos(0, -1, -6));
        tensor.add(new BlockPos(0, -1, 0));
        tensor.add(new BlockPos(0, 0, 0));
        tensor.add(new BlockPos(0, 1, -6));
        tensor.add(new BlockPos(0, 1, 0));
        tensor.add(new BlockPos(0, 2, -6));
        tensor.add(new BlockPos(0, 2, 0));
        tensor.add(new BlockPos(0, 3, -6));
        tensor.add(new BlockPos(0, 3, 0));
        tensor.add(new BlockPos(0, 4, -6));
        tensor.add(new BlockPos(0, 4, 0));
        tensor.add(new BlockPos(0, 5, -5));
        tensor.add(new BlockPos(0, 5, 1));
        tensor.add(new BlockPos(0, 6, -5));
        tensor.add(new BlockPos(0, 6, 1));
        tensor.add(new BlockPos(0, 7, -4));
        tensor.add(new BlockPos(0, 7, 2));
        tensor.add(new BlockPos(0, 8, -3));
        tensor.add(new BlockPos(0, 8, 2));
        tensor.add(new BlockPos(0, 8, 3));
        tensor.add(new BlockPos(0, 9, -2));
        tensor.add(new BlockPos(0, 9, 3));
        tensor.add(new BlockPos(0, 9, 4));
        tensor.add(new BlockPos(0, 10, -1));
        tensor.add(new BlockPos(0, 10, 4));
        tensor.add(new BlockPos(0, 10, 5));
        tensor.add(new BlockPos(0, 11, 0));
        tensor.add(new BlockPos(0, 11, 1));
        tensor.add(new BlockPos(0, 11, 6));
        tensor.add(new BlockPos(0, 11, 7));
        tensor.add(new BlockPos(0, 12, 2));
        tensor.add(new BlockPos(0, 12, 3));
        tensor.add(new BlockPos(0, 12, 4));
        tensor.add(new BlockPos(0, 12, 5));
        tensor.add(new BlockPos(0, 12, 6));
        tensor.add(new BlockPos(0, 12, 7));
        tensor.add(new BlockPos(0, 12, 8));
        tensor.add(new BlockPos(0, 12, 9));
        tensor.add(new BlockPos(0, 12, 10));
        tensor.add(new BlockPos(1, 0, -6));
        tensor.add(new BlockPos(1, 0, 0));
        tensor.add(new BlockPos(2, 0, -6));
        tensor.add(new BlockPos(2, 0, 0));
        tensor.add(new BlockPos(3, 0, -6));
        tensor.add(new BlockPos(3, 0, 0));
        tensor.add(new BlockPos(4, 0, -6));
        tensor.add(new BlockPos(4, 0, 0));
        tensor.add(new BlockPos(5, 0, -5));
        tensor.add(new BlockPos(5, 0, 1));
        tensor.add(new BlockPos(6, 0, -5));
        tensor.add(new BlockPos(6, 0, 1));
        tensor.add(new BlockPos(7, 0, -4));
        tensor.add(new BlockPos(7, 0, 2));
        tensor.add(new BlockPos(8, 0, -3));
        tensor.add(new BlockPos(8, 0, 2));
        tensor.add(new BlockPos(8, 0, 3));
        tensor.add(new BlockPos(9, 0, -2));
        tensor.add(new BlockPos(9, 0, 3));
        tensor.add(new BlockPos(9, 0, 4));
        tensor.add(new BlockPos(10, 0, -1));
        tensor.add(new BlockPos(10, 0, 4));
        tensor.add(new BlockPos(10, 0, 5));
        tensor.add(new BlockPos(11, 0, 0));
        tensor.add(new BlockPos(11, 0, 1));
        tensor.add(new BlockPos(11, 0, 6));
        tensor.add(new BlockPos(11, 0, 7));
        tensor.add(new BlockPos(12, 0, 2));
        tensor.add(new BlockPos(12, 0, 3));
        tensor.add(new BlockPos(12, 0, 4));
        tensor.add(new BlockPos(12, 0, 5));
        tensor.add(new BlockPos(12, 0, 6));
        tensor.add(new BlockPos(12, 0, 7));
        tensor.add(new BlockPos(12, 0, 8));
        tensor.add(new BlockPos(12, 0, 9));
        tensor.add(new BlockPos(12, 0, 10));
        TENSOR_CASING_SET = Collections.unmodifiableSet(tensor);

        Set<BlockPos> causal = new HashSet<>();
        causal.add(new BlockPos(-11, 0, 2));
        causal.add(new BlockPos(-11, 0, 3));
        causal.add(new BlockPos(-11, 0, 4));
        causal.add(new BlockPos(-11, 0, 5));
        causal.add(new BlockPos(-10, 0, 0));
        causal.add(new BlockPos(-10, 0, 1));
        causal.add(new BlockPos(-10, 0, 2));
        causal.add(new BlockPos(-10, 0, 3));
        causal.add(new BlockPos(-9, 0, -1));
        causal.add(new BlockPos(-9, 0, 0));
        causal.add(new BlockPos(-9, 0, 1));
        causal.add(new BlockPos(-9, 0, 2));
        causal.add(new BlockPos(-8, -1, 0));
        causal.add(new BlockPos(-8, 0, -2));
        causal.add(new BlockPos(-8, 0, -1));
        causal.add(new BlockPos(-8, 0, 0));
        causal.add(new BlockPos(-8, 0, 1));
        causal.add(new BlockPos(-8, 1, 0));
        causal.add(new BlockPos(-7, -1, -1));
        causal.add(new BlockPos(-7, 0, -3));
        causal.add(new BlockPos(-7, 0, -2));
        causal.add(new BlockPos(-7, 0, -1));
        causal.add(new BlockPos(-7, 0, 0));
        causal.add(new BlockPos(-7, 0, 1));
        causal.add(new BlockPos(-7, 1, -1));
        causal.add(new BlockPos(-6, -1, -2));
        causal.add(new BlockPos(-6, -1, -1));
        causal.add(new BlockPos(-6, 0, -4));
        causal.add(new BlockPos(-6, 0, -3));
        causal.add(new BlockPos(-6, 0, -2));
        causal.add(new BlockPos(-6, 0, -1));
        causal.add(new BlockPos(-6, 0, 0));
        causal.add(new BlockPos(-6, 1, -2));
        causal.add(new BlockPos(-6, 1, -1));
        causal.add(new BlockPos(-5, -1, -3));
        causal.add(new BlockPos(-5, -1, -2));
        causal.add(new BlockPos(-5, 0, -4));
        causal.add(new BlockPos(-5, 0, -3));
        causal.add(new BlockPos(-5, 0, -2));
        causal.add(new BlockPos(-5, 0, -1));
        causal.add(new BlockPos(-5, 0, 0));
        causal.add(new BlockPos(-5, 1, -3));
        causal.add(new BlockPos(-5, 1, -2));
        causal.add(new BlockPos(-4, -1, -3));
        causal.add(new BlockPos(-4, -1, -2));
        causal.add(new BlockPos(-4, 0, -5));
        causal.add(new BlockPos(-4, 0, -4));
        causal.add(new BlockPos(-4, 0, -3));
        causal.add(new BlockPos(-4, 0, -2));
        causal.add(new BlockPos(-4, 0, -1));
        causal.add(new BlockPos(-4, 1, -3));
        causal.add(new BlockPos(-4, 1, -2));
        causal.add(new BlockPos(-3, -2, -3));
        causal.add(new BlockPos(-3, -1, -4));
        causal.add(new BlockPos(-3, -1, -3));
        causal.add(new BlockPos(-3, -1, -2));
        causal.add(new BlockPos(-3, 0, -5));
        causal.add(new BlockPos(-3, 0, -4));
        causal.add(new BlockPos(-3, 0, -3));
        causal.add(new BlockPos(-3, 0, -2));
        causal.add(new BlockPos(-3, 0, -1));
        causal.add(new BlockPos(-3, 1, -4));
        causal.add(new BlockPos(-3, 1, -3));
        causal.add(new BlockPos(-3, 1, -2));
        causal.add(new BlockPos(-3, 2, -3));
        causal.add(new BlockPos(-2, -3, -3));
        causal.add(new BlockPos(-2, -2, -3));
        causal.add(new BlockPos(-2, -1, -4));
        causal.add(new BlockPos(-2, -1, -3));
        causal.add(new BlockPos(-2, -1, -2));
        causal.add(new BlockPos(-2, 0, -5));
        causal.add(new BlockPos(-2, 0, -4));
        causal.add(new BlockPos(-2, 0, -3));
        causal.add(new BlockPos(-2, 0, -2));
        causal.add(new BlockPos(-2, 0, -1));
        causal.add(new BlockPos(-2, 1, -4));
        causal.add(new BlockPos(-2, 1, -3));
        causal.add(new BlockPos(-2, 1, -2));
        causal.add(new BlockPos(-2, 2, -3));
        causal.add(new BlockPos(-2, 3, -3));
        causal.add(new BlockPos(-1, -9, 0));
        causal.add(new BlockPos(-1, -8, 0));
        causal.add(new BlockPos(-1, -7, -1));
        causal.add(new BlockPos(-1, -6, -2));
        causal.add(new BlockPos(-1, -6, -1));
        causal.add(new BlockPos(-1, -5, -3));
        causal.add(new BlockPos(-1, -5, -2));
        causal.add(new BlockPos(-1, -4, -3));
        causal.add(new BlockPos(-1, -4, -2));
        causal.add(new BlockPos(-1, -3, -4));
        causal.add(new BlockPos(-1, -3, -3));
        causal.add(new BlockPos(-1, -3, -2));
        causal.add(new BlockPos(-1, -2, -4));
        causal.add(new BlockPos(-1, -2, -3));
        causal.add(new BlockPos(-1, -2, -2));
        causal.add(new BlockPos(-1, -1, -4));
        causal.add(new BlockPos(-1, -1, -3));
        causal.add(new BlockPos(-1, -1, -2));
        causal.add(new BlockPos(-1, 0, -5));
        causal.add(new BlockPos(-1, 0, -4));
        causal.add(new BlockPos(-1, 0, -3));
        causal.add(new BlockPos(-1, 0, -2));
        causal.add(new BlockPos(-1, 0, -1));
        causal.add(new BlockPos(-1, 1, -4));
        causal.add(new BlockPos(-1, 1, -3));
        causal.add(new BlockPos(-1, 1, -2));
        causal.add(new BlockPos(-1, 2, -4));
        causal.add(new BlockPos(-1, 2, -3));
        causal.add(new BlockPos(-1, 2, -2));
        causal.add(new BlockPos(-1, 3, -4));
        causal.add(new BlockPos(-1, 3, -3));
        causal.add(new BlockPos(-1, 3, -2));
        causal.add(new BlockPos(-1, 4, -3));
        causal.add(new BlockPos(-1, 4, -2));
        causal.add(new BlockPos(-1, 5, -3));
        causal.add(new BlockPos(-1, 5, -2));
        causal.add(new BlockPos(-1, 6, -2));
        causal.add(new BlockPos(-1, 6, -1));
        causal.add(new BlockPos(-1, 7, -1));
        causal.add(new BlockPos(-1, 8, 0));
        causal.add(new BlockPos(-1, 9, 0));
        causal.add(new BlockPos(0, -11, 2));
        causal.add(new BlockPos(0, -11, 3));
        causal.add(new BlockPos(0, -11, 4));
        causal.add(new BlockPos(0, -11, 5));
        causal.add(new BlockPos(0, -10, 0));
        causal.add(new BlockPos(0, -10, 1));
        causal.add(new BlockPos(0, -10, 2));
        causal.add(new BlockPos(0, -10, 3));
        causal.add(new BlockPos(0, -9, -1));
        causal.add(new BlockPos(0, -9, 0));
        causal.add(new BlockPos(0, -9, 1));
        causal.add(new BlockPos(0, -9, 2));
        causal.add(new BlockPos(0, -8, -2));
        causal.add(new BlockPos(0, -8, -1));
        causal.add(new BlockPos(0, -8, 0));
        causal.add(new BlockPos(0, -8, 1));
        causal.add(new BlockPos(0, -7, -3));
        causal.add(new BlockPos(0, -7, -2));
        causal.add(new BlockPos(0, -7, -1));
        causal.add(new BlockPos(0, -7, 0));
        causal.add(new BlockPos(0, -7, 1));
        causal.add(new BlockPos(0, -6, -4));
        causal.add(new BlockPos(0, -6, -3));
        causal.add(new BlockPos(0, -6, -2));
        causal.add(new BlockPos(0, -6, -1));
        causal.add(new BlockPos(0, -6, 0));
        causal.add(new BlockPos(0, -5, -4));
        causal.add(new BlockPos(0, -5, -3));
        causal.add(new BlockPos(0, -5, -2));
        causal.add(new BlockPos(0, -5, -1));
        causal.add(new BlockPos(0, -5, 0));
        causal.add(new BlockPos(0, -4, -5));
        causal.add(new BlockPos(0, -4, -4));
        causal.add(new BlockPos(0, -4, -3));
        causal.add(new BlockPos(0, -4, -2));
        causal.add(new BlockPos(0, -4, -1));
        causal.add(new BlockPos(0, -3, -5));
        causal.add(new BlockPos(0, -3, -4));
        causal.add(new BlockPos(0, -3, -3));
        causal.add(new BlockPos(0, -3, -2));
        causal.add(new BlockPos(0, -3, -1));
        causal.add(new BlockPos(0, -2, -5));
        causal.add(new BlockPos(0, -2, -4));
        causal.add(new BlockPos(0, -2, -3));
        causal.add(new BlockPos(0, -2, -2));
        causal.add(new BlockPos(0, -2, -1));
        causal.add(new BlockPos(0, -1, -5));
        causal.add(new BlockPos(0, -1, -4));
        causal.add(new BlockPos(0, -1, -3));
        causal.add(new BlockPos(0, -1, -2));
        causal.add(new BlockPos(0, -1, -1));
        causal.add(new BlockPos(0, 0, -5));
        causal.add(new BlockPos(0, 0, -4));
        causal.add(new BlockPos(0, 0, -3));
        causal.add(new BlockPos(0, 0, -2));
        causal.add(new BlockPos(0, 0, -1));
        causal.add(new BlockPos(0, 1, -5));
        causal.add(new BlockPos(0, 1, -4));
        causal.add(new BlockPos(0, 1, -3));
        causal.add(new BlockPos(0, 1, -2));
        causal.add(new BlockPos(0, 1, -1));
        causal.add(new BlockPos(0, 2, -5));
        causal.add(new BlockPos(0, 2, -4));
        causal.add(new BlockPos(0, 2, -3));
        causal.add(new BlockPos(0, 2, -2));
        causal.add(new BlockPos(0, 2, -1));
        causal.add(new BlockPos(0, 3, -5));
        causal.add(new BlockPos(0, 3, -4));
        causal.add(new BlockPos(0, 3, -3));
        causal.add(new BlockPos(0, 3, -2));
        causal.add(new BlockPos(0, 3, -1));
        causal.add(new BlockPos(0, 4, -5));
        causal.add(new BlockPos(0, 4, -4));
        causal.add(new BlockPos(0, 4, -3));
        causal.add(new BlockPos(0, 4, -2));
        causal.add(new BlockPos(0, 4, -1));
        causal.add(new BlockPos(0, 5, -4));
        causal.add(new BlockPos(0, 5, -3));
        causal.add(new BlockPos(0, 5, -2));
        causal.add(new BlockPos(0, 5, -1));
        causal.add(new BlockPos(0, 5, 0));
        causal.add(new BlockPos(0, 6, -4));
        causal.add(new BlockPos(0, 6, -3));
        causal.add(new BlockPos(0, 6, -2));
        causal.add(new BlockPos(0, 6, -1));
        causal.add(new BlockPos(0, 6, 0));
        causal.add(new BlockPos(0, 7, -3));
        causal.add(new BlockPos(0, 7, -2));
        causal.add(new BlockPos(0, 7, -1));
        causal.add(new BlockPos(0, 7, 0));
        causal.add(new BlockPos(0, 7, 1));
        causal.add(new BlockPos(0, 8, -2));
        causal.add(new BlockPos(0, 8, -1));
        causal.add(new BlockPos(0, 8, 0));
        causal.add(new BlockPos(0, 8, 1));
        causal.add(new BlockPos(0, 9, -1));
        causal.add(new BlockPos(0, 9, 0));
        causal.add(new BlockPos(0, 9, 1));
        causal.add(new BlockPos(0, 9, 2));
        causal.add(new BlockPos(0, 10, 0));
        causal.add(new BlockPos(0, 10, 1));
        causal.add(new BlockPos(0, 10, 2));
        causal.add(new BlockPos(0, 10, 3));
        causal.add(new BlockPos(0, 11, 2));
        causal.add(new BlockPos(0, 11, 3));
        causal.add(new BlockPos(0, 11, 4));
        causal.add(new BlockPos(0, 11, 5));
        causal.add(new BlockPos(1, -8, 0));
        causal.add(new BlockPos(1, -7, -1));
        causal.add(new BlockPos(1, -6, -2));
        causal.add(new BlockPos(1, -6, -1));
        causal.add(new BlockPos(1, -5, -3));
        causal.add(new BlockPos(1, -5, -2));
        causal.add(new BlockPos(1, -4, -3));
        causal.add(new BlockPos(1, -4, -2));
        causal.add(new BlockPos(1, -3, -4));
        causal.add(new BlockPos(1, -3, -3));
        causal.add(new BlockPos(1, -3, -2));
        causal.add(new BlockPos(1, -2, -4));
        causal.add(new BlockPos(1, -2, -3));
        causal.add(new BlockPos(1, -2, -2));
        causal.add(new BlockPos(1, -1, -4));
        causal.add(new BlockPos(1, -1, -3));
        causal.add(new BlockPos(1, -1, -2));
        causal.add(new BlockPos(1, 0, -5));
        causal.add(new BlockPos(1, 0, -4));
        causal.add(new BlockPos(1, 0, -3));
        causal.add(new BlockPos(1, 0, -2));
        causal.add(new BlockPos(1, 0, -1));
        causal.add(new BlockPos(1, 1, -4));
        causal.add(new BlockPos(1, 1, -3));
        causal.add(new BlockPos(1, 1, -2));
        causal.add(new BlockPos(1, 2, -4));
        causal.add(new BlockPos(1, 2, -3));
        causal.add(new BlockPos(1, 2, -2));
        causal.add(new BlockPos(1, 3, -4));
        causal.add(new BlockPos(1, 3, -3));
        causal.add(new BlockPos(1, 3, -2));
        causal.add(new BlockPos(1, 4, -3));
        causal.add(new BlockPos(1, 4, -2));
        causal.add(new BlockPos(1, 5, -3));
        causal.add(new BlockPos(1, 5, -2));
        causal.add(new BlockPos(1, 6, -2));
        causal.add(new BlockPos(1, 6, -1));
        causal.add(new BlockPos(1, 7, -1));
        causal.add(new BlockPos(1, 8, 0));
        causal.add(new BlockPos(2, -2, -3));
        causal.add(new BlockPos(2, -1, -4));
        causal.add(new BlockPos(2, -1, -3));
        causal.add(new BlockPos(2, -1, -2));
        causal.add(new BlockPos(2, 0, -5));
        causal.add(new BlockPos(2, 0, -4));
        causal.add(new BlockPos(2, 0, -3));
        causal.add(new BlockPos(2, 0, -2));
        causal.add(new BlockPos(2, 0, -1));
        causal.add(new BlockPos(2, 1, -4));
        causal.add(new BlockPos(2, 1, -3));
        causal.add(new BlockPos(2, 1, -2));
        causal.add(new BlockPos(2, 2, -3));
        causal.add(new BlockPos(3, -1, -4));
        causal.add(new BlockPos(3, -1, -3));
        causal.add(new BlockPos(3, -1, -2));
        causal.add(new BlockPos(3, 0, -5));
        causal.add(new BlockPos(3, 0, -4));
        causal.add(new BlockPos(3, 0, -3));
        causal.add(new BlockPos(3, 0, -2));
        causal.add(new BlockPos(3, 0, -1));
        causal.add(new BlockPos(3, 1, -4));
        causal.add(new BlockPos(3, 1, -3));
        causal.add(new BlockPos(3, 1, -2));
        causal.add(new BlockPos(4, -1, -3));
        causal.add(new BlockPos(4, -1, -2));
        causal.add(new BlockPos(4, 0, -5));
        causal.add(new BlockPos(4, 0, -4));
        causal.add(new BlockPos(4, 0, -3));
        causal.add(new BlockPos(4, 0, -2));
        causal.add(new BlockPos(4, 0, -1));
        causal.add(new BlockPos(4, 1, -3));
        causal.add(new BlockPos(4, 1, -2));
        causal.add(new BlockPos(5, -1, -3));
        causal.add(new BlockPos(5, -1, -2));
        causal.add(new BlockPos(5, 0, -4));
        causal.add(new BlockPos(5, 0, -3));
        causal.add(new BlockPos(5, 0, -2));
        causal.add(new BlockPos(5, 0, -1));
        causal.add(new BlockPos(5, 0, 0));
        causal.add(new BlockPos(5, 1, -3));
        causal.add(new BlockPos(5, 1, -2));
        causal.add(new BlockPos(6, -1, -2));
        causal.add(new BlockPos(6, -1, -1));
        causal.add(new BlockPos(6, 0, -4));
        causal.add(new BlockPos(6, 0, -3));
        causal.add(new BlockPos(6, 0, -2));
        causal.add(new BlockPos(6, 0, -1));
        causal.add(new BlockPos(6, 0, 0));
        causal.add(new BlockPos(6, 1, -2));
        causal.add(new BlockPos(6, 1, -1));
        causal.add(new BlockPos(7, -1, -1));
        causal.add(new BlockPos(7, 0, -3));
        causal.add(new BlockPos(7, 0, -2));
        causal.add(new BlockPos(7, 0, -1));
        causal.add(new BlockPos(7, 0, 0));
        causal.add(new BlockPos(7, 0, 1));
        causal.add(new BlockPos(7, 1, -1));
        causal.add(new BlockPos(8, -1, 0));
        causal.add(new BlockPos(8, 0, -2));
        causal.add(new BlockPos(8, 0, -1));
        causal.add(new BlockPos(8, 0, 0));
        causal.add(new BlockPos(8, 0, 1));
        causal.add(new BlockPos(8, 1, 0));
        causal.add(new BlockPos(9, 0, -1));
        causal.add(new BlockPos(9, 0, 0));
        causal.add(new BlockPos(9, 0, 1));
        causal.add(new BlockPos(9, 0, 2));
        causal.add(new BlockPos(10, 0, 0));
        causal.add(new BlockPos(10, 0, 1));
        causal.add(new BlockPos(10, 0, 2));
        causal.add(new BlockPos(10, 0, 3));
        causal.add(new BlockPos(11, 0, 2));
        causal.add(new BlockPos(11, 0, 3));
        causal.add(new BlockPos(11, 0, 4));
        causal.add(new BlockPos(11, 0, 5));
        CAUSAL_ANCHOR_SET = Collections.unmodifiableSet(causal);

        Set<BlockPos> spinor = new HashSet<>();
        spinor.add(new BlockPos(-11, -1, 2));
        spinor.add(new BlockPos(-11, -1, 3));
        spinor.add(new BlockPos(-11, -1, 4));
        spinor.add(new BlockPos(-11, -1, 5));
        spinor.add(new BlockPos(-11, 1, 2));
        spinor.add(new BlockPos(-11, 1, 3));
        spinor.add(new BlockPos(-11, 1, 4));
        spinor.add(new BlockPos(-11, 1, 5));
        spinor.add(new BlockPos(-10, -1, 0));
        spinor.add(new BlockPos(-10, -1, 1));
        spinor.add(new BlockPos(-10, -1, 2));
        spinor.add(new BlockPos(-10, -1, 3));
        spinor.add(new BlockPos(-10, 1, 0));
        spinor.add(new BlockPos(-10, 1, 1));
        spinor.add(new BlockPos(-10, 1, 2));
        spinor.add(new BlockPos(-10, 1, 3));
        spinor.add(new BlockPos(-9, -2, 0));
        spinor.add(new BlockPos(-9, -1, -1));
        spinor.add(new BlockPos(-9, -1, 0));
        spinor.add(new BlockPos(-9, -1, 1));
        spinor.add(new BlockPos(-9, -1, 2));
        spinor.add(new BlockPos(-9, 1, -1));
        spinor.add(new BlockPos(-9, 1, 0));
        spinor.add(new BlockPos(-9, 1, 1));
        spinor.add(new BlockPos(-9, 1, 2));
        spinor.add(new BlockPos(-9, 2, 0));
        spinor.add(new BlockPos(-8, -2, 0));
        spinor.add(new BlockPos(-8, -1, -2));
        spinor.add(new BlockPos(-8, -1, -1));
        spinor.add(new BlockPos(-8, -1, 1));
        spinor.add(new BlockPos(-8, 1, -2));
        spinor.add(new BlockPos(-8, 1, -1));
        spinor.add(new BlockPos(-8, 1, 1));
        spinor.add(new BlockPos(-8, 2, 0));
        spinor.add(new BlockPos(-7, -2, -1));
        spinor.add(new BlockPos(-7, -1, -3));
        spinor.add(new BlockPos(-7, -1, -2));
        spinor.add(new BlockPos(-7, -1, 0));
        spinor.add(new BlockPos(-7, -1, 1));
        spinor.add(new BlockPos(-7, 1, -3));
        spinor.add(new BlockPos(-7, 1, -2));
        spinor.add(new BlockPos(-7, 1, 0));
        spinor.add(new BlockPos(-7, 1, 1));
        spinor.add(new BlockPos(-7, 2, -1));
        spinor.add(new BlockPos(-6, -2, -2));
        spinor.add(new BlockPos(-6, -2, -1));
        spinor.add(new BlockPos(-6, -1, -4));
        spinor.add(new BlockPos(-6, -1, -3));
        spinor.add(new BlockPos(-6, -1, 0));
        spinor.add(new BlockPos(-6, 1, -4));
        spinor.add(new BlockPos(-6, 1, -3));
        spinor.add(new BlockPos(-6, 1, 0));
        spinor.add(new BlockPos(-6, 2, -2));
        spinor.add(new BlockPos(-6, 2, -1));
        spinor.add(new BlockPos(-5, -2, -3));
        spinor.add(new BlockPos(-5, -2, -2));
        spinor.add(new BlockPos(-5, -1, -4));
        spinor.add(new BlockPos(-5, -1, -1));
        spinor.add(new BlockPos(-5, -1, 0));
        spinor.add(new BlockPos(-5, 1, -4));
        spinor.add(new BlockPos(-5, 1, -1));
        spinor.add(new BlockPos(-5, 1, 0));
        spinor.add(new BlockPos(-5, 2, -3));
        spinor.add(new BlockPos(-5, 2, -2));
        spinor.add(new BlockPos(-4, -2, -3));
        spinor.add(new BlockPos(-4, -2, -2));
        spinor.add(new BlockPos(-4, -1, -5));
        spinor.add(new BlockPos(-4, -1, -4));
        spinor.add(new BlockPos(-4, -1, -1));
        spinor.add(new BlockPos(-4, 1, -5));
        spinor.add(new BlockPos(-4, 1, -4));
        spinor.add(new BlockPos(-4, 1, -1));
        spinor.add(new BlockPos(-4, 2, -3));
        spinor.add(new BlockPos(-4, 2, -2));
        spinor.add(new BlockPos(-3, -3, -3));
        spinor.add(new BlockPos(-3, -2, -4));
        spinor.add(new BlockPos(-3, -2, -2));
        spinor.add(new BlockPos(-3, -1, -5));
        spinor.add(new BlockPos(-3, -1, -1));
        spinor.add(new BlockPos(-3, 1, -5));
        spinor.add(new BlockPos(-3, 1, -1));
        spinor.add(new BlockPos(-3, 2, -4));
        spinor.add(new BlockPos(-3, 2, -2));
        spinor.add(new BlockPos(-3, 3, -3));
        spinor.add(new BlockPos(-2, -9, 0));
        spinor.add(new BlockPos(-2, -8, 0));
        spinor.add(new BlockPos(-2, -7, -1));
        spinor.add(new BlockPos(-2, -6, -2));
        spinor.add(new BlockPos(-2, -6, -1));
        spinor.add(new BlockPos(-2, -5, -3));
        spinor.add(new BlockPos(-2, -5, -2));
        spinor.add(new BlockPos(-2, -4, -3));
        spinor.add(new BlockPos(-2, -4, -2));
        spinor.add(new BlockPos(-2, -3, -4));
        spinor.add(new BlockPos(-2, -3, -2));
        spinor.add(new BlockPos(-2, -2, -4));
        spinor.add(new BlockPos(-2, -2, -2));
        spinor.add(new BlockPos(-2, -1, -5));
        spinor.add(new BlockPos(-2, -1, -1));
        spinor.add(new BlockPos(-2, 1, -5));
        spinor.add(new BlockPos(-2, 1, -1));
        spinor.add(new BlockPos(-2, 2, -4));
        spinor.add(new BlockPos(-2, 2, -2));
        spinor.add(new BlockPos(-2, 3, -4));
        spinor.add(new BlockPos(-2, 3, -2));
        spinor.add(new BlockPos(-2, 4, -3));
        spinor.add(new BlockPos(-2, 4, -2));
        spinor.add(new BlockPos(-2, 5, -3));
        spinor.add(new BlockPos(-2, 5, -2));
        spinor.add(new BlockPos(-2, 6, -2));
        spinor.add(new BlockPos(-2, 6, -1));
        spinor.add(new BlockPos(-2, 7, -1));
        spinor.add(new BlockPos(-2, 8, 0));
        spinor.add(new BlockPos(-2, 9, 0));
        spinor.add(new BlockPos(-1, -11, 2));
        spinor.add(new BlockPos(-1, -11, 3));
        spinor.add(new BlockPos(-1, -11, 4));
        spinor.add(new BlockPos(-1, -11, 5));
        spinor.add(new BlockPos(-1, -10, 0));
        spinor.add(new BlockPos(-1, -10, 1));
        spinor.add(new BlockPos(-1, -10, 2));
        spinor.add(new BlockPos(-1, -10, 3));
        spinor.add(new BlockPos(-1, -9, -1));
        spinor.add(new BlockPos(-1, -9, 1));
        spinor.add(new BlockPos(-1, -9, 2));
        spinor.add(new BlockPos(-1, -8, -2));
        spinor.add(new BlockPos(-1, -8, -1));
        spinor.add(new BlockPos(-1, -8, 1));
        spinor.add(new BlockPos(-1, -7, -3));
        spinor.add(new BlockPos(-1, -7, -2));
        spinor.add(new BlockPos(-1, -7, 0));
        spinor.add(new BlockPos(-1, -7, 1));
        spinor.add(new BlockPos(-1, -6, -4));
        spinor.add(new BlockPos(-1, -6, -3));
        spinor.add(new BlockPos(-1, -6, 0));
        spinor.add(new BlockPos(-1, -5, -4));
        spinor.add(new BlockPos(-1, -5, -1));
        spinor.add(new BlockPos(-1, -5, 0));
        spinor.add(new BlockPos(-1, -4, -5));
        spinor.add(new BlockPos(-1, -4, -4));
        spinor.add(new BlockPos(-1, -4, -1));
        spinor.add(new BlockPos(-1, -3, -5));
        spinor.add(new BlockPos(-1, -3, -1));
        spinor.add(new BlockPos(-1, -2, -5));
        spinor.add(new BlockPos(-1, -2, -1));
        spinor.add(new BlockPos(-1, -1, -5));
        spinor.add(new BlockPos(-1, -1, -1));
        spinor.add(new BlockPos(-1, 1, -5));
        spinor.add(new BlockPos(-1, 1, -1));
        spinor.add(new BlockPos(-1, 2, -5));
        spinor.add(new BlockPos(-1, 2, -1));
        spinor.add(new BlockPos(-1, 3, -5));
        spinor.add(new BlockPos(-1, 3, -1));
        spinor.add(new BlockPos(-1, 4, -5));
        spinor.add(new BlockPos(-1, 4, -4));
        spinor.add(new BlockPos(-1, 4, -1));
        spinor.add(new BlockPos(-1, 5, -4));
        spinor.add(new BlockPos(-1, 5, -1));
        spinor.add(new BlockPos(-1, 5, 0));
        spinor.add(new BlockPos(-1, 6, -4));
        spinor.add(new BlockPos(-1, 6, -3));
        spinor.add(new BlockPos(-1, 6, 0));
        spinor.add(new BlockPos(-1, 7, -3));
        spinor.add(new BlockPos(-1, 7, -2));
        spinor.add(new BlockPos(-1, 7, 0));
        spinor.add(new BlockPos(-1, 7, 1));
        spinor.add(new BlockPos(-1, 8, -2));
        spinor.add(new BlockPos(-1, 8, -1));
        spinor.add(new BlockPos(-1, 8, 1));
        spinor.add(new BlockPos(-1, 9, -1));
        spinor.add(new BlockPos(-1, 9, 1));
        spinor.add(new BlockPos(-1, 9, 2));
        spinor.add(new BlockPos(-1, 10, 0));
        spinor.add(new BlockPos(-1, 10, 1));
        spinor.add(new BlockPos(-1, 10, 2));
        spinor.add(new BlockPos(-1, 10, 3));
        spinor.add(new BlockPos(-1, 11, 2));
        spinor.add(new BlockPos(-1, 11, 3));
        spinor.add(new BlockPos(-1, 11, 4));
        spinor.add(new BlockPos(-1, 11, 5));
        spinor.add(new BlockPos(1, -11, 2));
        spinor.add(new BlockPos(1, -11, 3));
        spinor.add(new BlockPos(1, -11, 4));
        spinor.add(new BlockPos(1, -11, 5));
        spinor.add(new BlockPos(1, -10, 0));
        spinor.add(new BlockPos(1, -10, 1));
        spinor.add(new BlockPos(1, -10, 2));
        spinor.add(new BlockPos(1, -10, 3));
        spinor.add(new BlockPos(1, -9, -1));
        spinor.add(new BlockPos(1, -9, 0));
        spinor.add(new BlockPos(1, -9, 1));
        spinor.add(new BlockPos(1, -9, 2));
        spinor.add(new BlockPos(1, -8, -2));
        spinor.add(new BlockPos(1, -8, -1));
        spinor.add(new BlockPos(1, -8, 1));
        spinor.add(new BlockPos(1, -7, -3));
        spinor.add(new BlockPos(1, -7, -2));
        spinor.add(new BlockPos(1, -7, 0));
        spinor.add(new BlockPos(1, -7, 1));
        spinor.add(new BlockPos(1, -6, -4));
        spinor.add(new BlockPos(1, -6, -3));
        spinor.add(new BlockPos(1, -6, 0));
        spinor.add(new BlockPos(1, -5, -4));
        spinor.add(new BlockPos(1, -5, -1));
        spinor.add(new BlockPos(1, -5, 0));
        spinor.add(new BlockPos(1, -4, -5));
        spinor.add(new BlockPos(1, -4, -4));
        spinor.add(new BlockPos(1, -4, -1));
        spinor.add(new BlockPos(1, -3, -5));
        spinor.add(new BlockPos(1, -3, -1));
        spinor.add(new BlockPos(1, -2, -5));
        spinor.add(new BlockPos(1, -2, -1));
        spinor.add(new BlockPos(1, -1, -5));
        spinor.add(new BlockPos(1, -1, -1));
        spinor.add(new BlockPos(1, 1, -5));
        spinor.add(new BlockPos(1, 1, -1));
        spinor.add(new BlockPos(1, 2, -5));
        spinor.add(new BlockPos(1, 2, -1));
        spinor.add(new BlockPos(1, 3, -5));
        spinor.add(new BlockPos(1, 3, -1));
        spinor.add(new BlockPos(1, 4, -5));
        spinor.add(new BlockPos(1, 4, -4));
        spinor.add(new BlockPos(1, 4, -1));
        spinor.add(new BlockPos(1, 5, -4));
        spinor.add(new BlockPos(1, 5, -1));
        spinor.add(new BlockPos(1, 5, 0));
        spinor.add(new BlockPos(1, 6, -4));
        spinor.add(new BlockPos(1, 6, -3));
        spinor.add(new BlockPos(1, 6, 0));
        spinor.add(new BlockPos(1, 7, -3));
        spinor.add(new BlockPos(1, 7, -2));
        spinor.add(new BlockPos(1, 7, 0));
        spinor.add(new BlockPos(1, 7, 1));
        spinor.add(new BlockPos(1, 8, -2));
        spinor.add(new BlockPos(1, 8, -1));
        spinor.add(new BlockPos(1, 8, 1));
        spinor.add(new BlockPos(1, 9, -1));
        spinor.add(new BlockPos(1, 9, 0));
        spinor.add(new BlockPos(1, 9, 1));
        spinor.add(new BlockPos(1, 9, 2));
        spinor.add(new BlockPos(1, 10, 0));
        spinor.add(new BlockPos(1, 10, 1));
        spinor.add(new BlockPos(1, 10, 2));
        spinor.add(new BlockPos(1, 10, 3));
        spinor.add(new BlockPos(1, 11, 2));
        spinor.add(new BlockPos(1, 11, 3));
        spinor.add(new BlockPos(1, 11, 4));
        spinor.add(new BlockPos(1, 11, 5));
        spinor.add(new BlockPos(2, -9, 0));
        spinor.add(new BlockPos(2, -8, 0));
        spinor.add(new BlockPos(2, -7, -1));
        spinor.add(new BlockPos(2, -6, -2));
        spinor.add(new BlockPos(2, -6, -1));
        spinor.add(new BlockPos(2, -5, -3));
        spinor.add(new BlockPos(2, -5, -2));
        spinor.add(new BlockPos(2, -4, -3));
        spinor.add(new BlockPos(2, -4, -2));
        spinor.add(new BlockPos(2, -3, -4));
        spinor.add(new BlockPos(2, -3, -3));
        spinor.add(new BlockPos(2, -3, -2));
        spinor.add(new BlockPos(2, -2, -4));
        spinor.add(new BlockPos(2, -2, -2));
        spinor.add(new BlockPos(2, -1, -5));
        spinor.add(new BlockPos(2, -1, -1));
        spinor.add(new BlockPos(2, 1, -5));
        spinor.add(new BlockPos(2, 1, -1));
        spinor.add(new BlockPos(2, 2, -4));
        spinor.add(new BlockPos(2, 2, -2));
        spinor.add(new BlockPos(2, 3, -4));
        spinor.add(new BlockPos(2, 3, -3));
        spinor.add(new BlockPos(2, 3, -2));
        spinor.add(new BlockPos(2, 4, -3));
        spinor.add(new BlockPos(2, 4, -2));
        spinor.add(new BlockPos(2, 5, -3));
        spinor.add(new BlockPos(2, 5, -2));
        spinor.add(new BlockPos(2, 6, -2));
        spinor.add(new BlockPos(2, 6, -1));
        spinor.add(new BlockPos(2, 7, -1));
        spinor.add(new BlockPos(2, 8, 0));
        spinor.add(new BlockPos(2, 9, 0));
        spinor.add(new BlockPos(3, -3, -3));
        spinor.add(new BlockPos(3, -2, -4));
        spinor.add(new BlockPos(3, -2, -3));
        spinor.add(new BlockPos(3, -2, -2));
        spinor.add(new BlockPos(3, -1, -5));
        spinor.add(new BlockPos(3, -1, -1));
        spinor.add(new BlockPos(3, 1, -5));
        spinor.add(new BlockPos(3, 1, -1));
        spinor.add(new BlockPos(3, 2, -4));
        spinor.add(new BlockPos(3, 2, -3));
        spinor.add(new BlockPos(3, 2, -2));
        spinor.add(new BlockPos(3, 3, -3));
        spinor.add(new BlockPos(4, -2, -3));
        spinor.add(new BlockPos(4, -2, -2));
        spinor.add(new BlockPos(4, -1, -5));
        spinor.add(new BlockPos(4, -1, -4));
        spinor.add(new BlockPos(4, -1, -1));
        spinor.add(new BlockPos(4, 1, -5));
        spinor.add(new BlockPos(4, 1, -4));
        spinor.add(new BlockPos(4, 1, -1));
        spinor.add(new BlockPos(4, 2, -3));
        spinor.add(new BlockPos(4, 2, -2));
        spinor.add(new BlockPos(5, -2, -3));
        spinor.add(new BlockPos(5, -2, -2));
        spinor.add(new BlockPos(5, -1, -4));
        spinor.add(new BlockPos(5, -1, -1));
        spinor.add(new BlockPos(5, -1, 0));
        spinor.add(new BlockPos(5, 1, -4));
        spinor.add(new BlockPos(5, 1, -1));
        spinor.add(new BlockPos(5, 1, 0));
        spinor.add(new BlockPos(5, 2, -3));
        spinor.add(new BlockPos(5, 2, -2));
        spinor.add(new BlockPos(6, -2, -2));
        spinor.add(new BlockPos(6, -2, -1));
        spinor.add(new BlockPos(6, -1, -4));
        spinor.add(new BlockPos(6, -1, -3));
        spinor.add(new BlockPos(6, -1, 0));
        spinor.add(new BlockPos(6, 1, -4));
        spinor.add(new BlockPos(6, 1, -3));
        spinor.add(new BlockPos(6, 1, 0));
        spinor.add(new BlockPos(6, 2, -2));
        spinor.add(new BlockPos(6, 2, -1));
        spinor.add(new BlockPos(7, -2, -1));
        spinor.add(new BlockPos(7, -1, -3));
        spinor.add(new BlockPos(7, -1, -2));
        spinor.add(new BlockPos(7, -1, 0));
        spinor.add(new BlockPos(7, -1, 1));
        spinor.add(new BlockPos(7, 1, -3));
        spinor.add(new BlockPos(7, 1, -2));
        spinor.add(new BlockPos(7, 1, 0));
        spinor.add(new BlockPos(7, 1, 1));
        spinor.add(new BlockPos(7, 2, -1));
        spinor.add(new BlockPos(8, -2, 0));
        spinor.add(new BlockPos(8, -1, -2));
        spinor.add(new BlockPos(8, -1, -1));
        spinor.add(new BlockPos(8, -1, 1));
        spinor.add(new BlockPos(8, 1, -2));
        spinor.add(new BlockPos(8, 1, -1));
        spinor.add(new BlockPos(8, 1, 1));
        spinor.add(new BlockPos(8, 2, 0));
        spinor.add(new BlockPos(9, -2, 0));
        spinor.add(new BlockPos(9, -1, -1));
        spinor.add(new BlockPos(9, -1, 0));
        spinor.add(new BlockPos(9, -1, 1));
        spinor.add(new BlockPos(9, -1, 2));
        spinor.add(new BlockPos(9, 1, -1));
        spinor.add(new BlockPos(9, 1, 0));
        spinor.add(new BlockPos(9, 1, 1));
        spinor.add(new BlockPos(9, 1, 2));
        spinor.add(new BlockPos(9, 2, 0));
        spinor.add(new BlockPos(10, -1, 0));
        spinor.add(new BlockPos(10, -1, 1));
        spinor.add(new BlockPos(10, -1, 2));
        spinor.add(new BlockPos(10, -1, 3));
        spinor.add(new BlockPos(10, 1, 0));
        spinor.add(new BlockPos(10, 1, 1));
        spinor.add(new BlockPos(10, 1, 2));
        spinor.add(new BlockPos(10, 1, 3));
        spinor.add(new BlockPos(11, -1, 2));
        spinor.add(new BlockPos(11, -1, 3));
        spinor.add(new BlockPos(11, -1, 4));
        spinor.add(new BlockPos(11, -1, 5));
        spinor.add(new BlockPos(11, 1, 2));
        spinor.add(new BlockPos(11, 1, 3));
        spinor.add(new BlockPos(11, 1, 4));
        spinor.add(new BlockPos(11, 1, 5));
        SPINOR_CASING_SET = Collections.unmodifiableSet(spinor);

        Set<BlockPos> all = new HashSet<>();
        all.addAll(TENSOR_CASING_SET);
        all.addAll(CAUSAL_ANCHOR_SET);
        all.addAll(SPINOR_CASING_SET);
        ALL_STRUCTURE_SET = Collections.unmodifiableSet(all);
    }

    /**
     * Returns the facing used for structure rotation.
     * We use the opposite of the controller's block facing so that the structure
     * expands behind the controller, leaving the controller's front face open to air.
     */
    public static EnumFacing getControllerFacing(World world, BlockPos controllerPos) {
        net.minecraft.block.state.IBlockState state = world.getBlockState(controllerPos);
        if (TileBlockStates.is(state, TileBlockStates.COMPUTATION_CORE)) {
            return TileBlockStates.facing(state).getOpposite();
        }
        return EnumFacing.NORTH;
    }

    public static BlockPos rotate(BlockPos rel, EnumFacing facing) {
        if (facing == EnumFacing.NORTH) return rel;
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        switch (facing) {
            case SOUTH: return new BlockPos(-x, y, -z);
            case EAST:  return new BlockPos(-z, y, x);
            case WEST:  return new BlockPos(z, y, -x);
            default:    return rel;
        }
    }

    /**
     * 世界相对坐标 → 基准相对坐标的逆旋转（rotate 的逆映射）.
     * NORTH 为恒等,SOUTH 旋转 180°（自逆）,EAST/WEST 互为逆.
     */
    public static BlockPos toCanonical(BlockPos rel, EnumFacing facing) {
        switch (facing) {
            case SOUTH: return rotate(rel, EnumFacing.SOUTH);
            case EAST:  return rotate(rel, EnumFacing.WEST);
            case WEST:  return rotate(rel, EnumFacing.EAST);
            default:    return rel;
        }
    }

    /**
     * 验证结构完整性.
     * @return 验证结果,包含是否通过、缺失方块统计、因果锚定核心数量和计算出的并行上限.
     */
    public static ValidationResult validate(World world, BlockPos controllerPos) {
        Map<Block, Integer> missing = new LinkedHashMap<>();
        if (!world.isBlockLoaded(controllerPos)) {
            return new ValidationResult(StructureValidationResult.DEFER_UNLOADED, missing, 0, 0);
        }
        EnumFacing facing = getControllerFacing(world, controllerPos);
        int causalCount = 0;
        boolean deferred = false;

        for (BlockPos rel : TENSOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                deferred = true;
                continue;
            }
            if (world.getBlockState(actual).getBlock() != TileBlockStates.block(TileBlockStates.CONSTANT_TENSOR_FIELD_CASING)) {
                if (actual.equals(controllerPos)) continue;
                missing.put(TileBlockStates.block(TileBlockStates.CONSTANT_TENSOR_FIELD_CASING), missing.getOrDefault(TileBlockStates.block(TileBlockStates.CONSTANT_TENSOR_FIELD_CASING), 0) + 1);
            }
        }

        for (BlockPos rel : CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                deferred = true;
                continue;
            }
            if (world.getBlockState(actual).getBlock() == TileBlockStates.block(TileBlockStates.CAUSAL_ANCHOR_CORE)) {
                causalCount++;
            } else {
                missing.put(TileBlockStates.block(TileBlockStates.CAUSAL_ANCHOR_CORE), missing.getOrDefault(TileBlockStates.block(TileBlockStates.CAUSAL_ANCHOR_CORE), 0) + 1);
            }
        }

        for (BlockPos rel : SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) {
                deferred = true;
                continue;
            }
            if (world.getBlockState(actual).getBlock() != TileBlockStates.block(TileBlockStates.CONSTANT_SPINOR_FIELD_CASING)) {
                missing.put(TileBlockStates.block(TileBlockStates.CONSTANT_SPINOR_FIELD_CASING), missing.getOrDefault(TileBlockStates.block(TileBlockStates.CONSTANT_SPINOR_FIELD_CASING), 0) + 1);
            }
        }

        BlockPos meInterfacePos = controllerPos.add(rotate(ME_INTERFACE_REL, facing));
        if (!world.isBlockLoaded(meInterfacePos)) {
            deferred = true;
        } else if (world.getBlockState(meInterfacePos).getBlock() != TileBlockStates.block(TileBlockStates.SUPER_CRAFTING_INTERFACE)) {
            missing.put(TileBlockStates.block(TileBlockStates.SUPER_CRAFTING_INTERFACE), missing.getOrDefault(TileBlockStates.block(TileBlockStates.SUPER_CRAFTING_INTERFACE), 0) + 1);
        }

        StructureValidationResult status = !missing.isEmpty() ? StructureValidationResult.INVALID
                : deferred ? StructureValidationResult.DEFER_UNLOADED : StructureValidationResult.VALID;
        int parallel = status == StructureValidationResult.VALID ? computeParallel() : 0;
        return new ValidationResult(status, missing, causalCount, parallel);
    }

    /**
     * 返回配置的并行合成上限(默认 16384).
     */
    public static int computeParallel() {
        return ServerConfig.current().crafting.maxParallel;
    }

    public static void assemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileComputationCore) {
            com.github.aeddddd.ae2enhanced.tile.TileComputationCore tile =
                (com.github.aeddddd.ae2enhanced.tile.TileComputationCore) te;
            ValidationResult result = validate(world, controllerPos);
            if (result.status == StructureValidationResult.VALID) {
                tile.assemble(result.parallelLimit);
            } else if (result.status == StructureValidationResult.DEFER_UNLOADED) {
                StructureDeferral.defer(world, controllerPos);
            }
        }
    }

    public static void disassemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileComputationCore) {
            com.github.aeddddd.ae2enhanced.tile.TileComputationCore tile =
                (com.github.aeddddd.ae2enhanced.tile.TileComputationCore) te;
            tile.disassemble();
        }
    }

    /** 创造模式：完整预检后一次放置，玩家仅在预检通过后移动到安全位置。 */
    public static void placeMissingBlocks(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return;
        com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan plan = preparePlacement(world, controllerPos, player);
        if (plan == null) return;
        if (com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.execute(plan,
                () -> validate(world, controllerPos).status == StructureValidationResult.VALID)) {
            assemble(world, controllerPos);
        }
    }

    private static com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan preparePlacement(
            World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        Map<BlockPos, Block> targets = new LinkedHashMap<>();
        addTargets(targets, controllerPos, TENSOR_CASING_SET,
                TileBlockStates.block(TileBlockStates.CONSTANT_TENSOR_FIELD_CASING), facing);
        addTargets(targets, controllerPos, CAUSAL_ANCHOR_SET,
                TileBlockStates.block(TileBlockStates.CAUSAL_ANCHOR_CORE), facing);
        addTargets(targets, controllerPos, SPINOR_CASING_SET,
                TileBlockStates.block(TileBlockStates.CONSTANT_SPINOR_FIELD_CASING), facing);
        targets.put(controllerPos.add(rotate(ME_INTERFACE_REL, facing)),
                TileBlockStates.block(TileBlockStates.SUPER_CRAFTING_INTERFACE));
        com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan plan =
                com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.prepare(world, targets);
        if (plan != null && player != null && plan.occupies(player.getPosition())) {
            movePlayerToSafety(world, controllerPos, player);
        }
        return plan;
    }

    private static void addTargets(Map<BlockPos, Block> targets, BlockPos origin, Set<BlockPos> set,
            Block block, EnumFacing facing) {
        for (BlockPos rel : set) {
            if (!rel.equals(CONTROLLER_REL)) targets.put(origin.add(rotate(rel, facing)), block);
        }
    }

    private static void movePlayerToSafety(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        BlockPos safe = controllerPos.up(2);
        for (int dy = 2; dy < 10; dy++) {
            BlockPos candidate = controllerPos.up(dy);
            if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up())) {
                safe = candidate;
                break;
            }
        }
        player.setPositionAndUpdate(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
    }

    /** 生存模式：完整预检、一次扣料、放置复验，任一步失败均回滚。 */
    public static boolean tryConsumeAndPlace(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return false;
        com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.Plan plan = preparePlacement(world, controllerPos, player);
        if (plan == null) return false;
        boolean success = com.github.aeddddd.ae2enhanced.structure.StructurePlacementService.consume(player.inventory, plan,
                () -> validate(world, controllerPos).status == StructureValidationResult.VALID);
        if (success) assemble(world, controllerPos);
        return success;
    }

    public static class ValidationResult {
        public final StructureValidationResult status;
        public final boolean passed;
        public final Map<Block, Integer> missing;
        public final int causalAnchorCount;
        public final int parallelLimit;

        public ValidationResult(StructureValidationResult status, Map<Block, Integer> missing,
                int causalAnchorCount, int parallelLimit) {
            this.status = status;
            this.passed = status == StructureValidationResult.VALID;
            this.missing = Collections.unmodifiableMap(missing);
            this.causalAnchorCount = causalAnchorCount;
            this.parallelLimit = parallelLimit;
        }
    }
}
