package com.github.aeddddd.ae2enhanced.structure;

import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * 超因果计算核心的多方块结构验证系统
 * 坐标原点为控制器位置 (0,0,0)，对应 JSON 中的 (0,0,6)。
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
        if (state.getBlock() instanceof com.github.aeddddd.ae2enhanced.block.BlockComputationCore) {
            return state.getValue(com.github.aeddddd.ae2enhanced.block.BlockComputationCore.FACING).getOpposite();
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
     * 验证结构完整性。
     * @return 验证结果，包含是否通过、缺失方块统计、因果锚定核心数量和计算出的并行上限。
     */
    public static ValidationResult validate(World world, BlockPos controllerPos) {
        EnumFacing facing = getControllerFacing(world, controllerPos);
        Map<Block, Integer> missing = new LinkedHashMap<>();
        int causalCount = 0;

        // 验证恒定张量场外壳
        for (BlockPos rel : TENSOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_TENSOR_FIELD_CASING) {
                if (actual.equals(controllerPos)) continue; // 控制器位置由核心方块占用，跳过
                missing.put(ModBlocks.CONSTANT_TENSOR_FIELD_CASING, missing.getOrDefault(ModBlocks.CONSTANT_TENSOR_FIELD_CASING, 0) + 1);
            }
        }

        // 验证因果锚定核心
        for (BlockPos rel : CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() == ModBlocks.CAUSAL_ANCHOR_CORE) {
                causalCount++;
            } else {
                missing.put(ModBlocks.CAUSAL_ANCHOR_CORE, missing.getOrDefault(ModBlocks.CAUSAL_ANCHOR_CORE, 0) + 1);
            }
        }

        // 验证恒定旋量场外壳
        for (BlockPos rel : SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_SPINOR_FIELD_CASING) {
                missing.put(ModBlocks.CONSTANT_SPINOR_FIELD_CASING, missing.getOrDefault(ModBlocks.CONSTANT_SPINOR_FIELD_CASING, 0) + 1);
            }
        }

        // 验证 ME 接口
        BlockPos meInterfacePos = controllerPos.add(rotate(ME_INTERFACE_REL, facing));
        boolean meInterfaceValid = false;
        if (world.isBlockLoaded(meInterfacePos)) {
            meInterfaceValid = world.getBlockState(meInterfacePos).getBlock() == ModBlocks.SUPER_CRAFTING_INTERFACE;
        }
        if (!meInterfaceValid) {
            missing.put(ModBlocks.SUPER_CRAFTING_INTERFACE, missing.getOrDefault(ModBlocks.SUPER_CRAFTING_INTERFACE, 0) + 1);
        }

        boolean passed = missing.isEmpty();
        int parallel = passed ? computeParallel(causalCount) : 0;

        return new ValidationResult(passed, missing, causalCount, parallel);
    }

    /**
     * 根据因果锚定核心数量计算并行上限。
     * 当前直接返回配置值 maxParallel（默认 16384）。
     */
    public static int computeParallel(int causalCount) {
        return AE2EnhancedConfig.crafting.maxParallel;
    }

    public static void assemble(World world, BlockPos controllerPos) {
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(controllerPos);
        if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileComputationCore) {
            com.github.aeddddd.ae2enhanced.tile.TileComputationCore tile =
                (com.github.aeddddd.ae2enhanced.tile.TileComputationCore) te;
            ValidationResult result = validate(world, controllerPos);
            if (result.passed) {
                tile.assemble(result.parallelLimit);
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

    /**
     * Creative mode: place all missing blocks instantly.
     */
    public static void placeMissingBlocks(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return;
        EnumFacing facing = getControllerFacing(world, controllerPos);

        placeBlocks(world, controllerPos, TENSOR_CASING_SET, ModBlocks.CONSTANT_TENSOR_FIELD_CASING, facing, player);
        placeBlocks(world, controllerPos, CAUSAL_ANCHOR_SET, ModBlocks.CAUSAL_ANCHOR_CORE, facing, player);
        placeBlocks(world, controllerPos, SPINOR_CASING_SET, ModBlocks.CONSTANT_SPINOR_FIELD_CASING, facing, player);

        BlockPos meInterfacePos = controllerPos.add(rotate(ME_INTERFACE_REL, facing));
        if (world.getBlockState(meInterfacePos).getBlock() != ModBlocks.SUPER_CRAFTING_INTERFACE) {
            world.setBlockState(meInterfacePos, ModBlocks.SUPER_CRAFTING_INTERFACE.getDefaultState());
        }

        assemble(world, controllerPos);
    }

    private static void placeBlocks(World world, BlockPos controllerPos, Set<BlockPos> set, Block block, EnumFacing facing, net.minecraft.entity.player.EntityPlayer player) {
        for (BlockPos rel : set) {
            if (rel.equals(CONTROLLER_REL)) continue; // skip controller position
            BlockPos pos = controllerPos.add(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() == block) continue;
            // avoid suffocating player
            if (player != null && pos.equals(player.getPosition())) {
                movePlayerToSafety(world, controllerPos, player);
            }
            world.setBlockState(pos, block.getDefaultState());
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

    /**
     * Survival mode: check inventory, consume materials, place missing blocks.
     * @return true if successful
     */
    public static boolean tryConsumeAndPlace(World world, BlockPos controllerPos, net.minecraft.entity.player.EntityPlayer player) {
        if (world.isRemote) return false;
        EnumFacing facing = getControllerFacing(world, controllerPos);

        Map<Block, Integer> missing = new LinkedHashMap<>();
        for (BlockPos rel : TENSOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_TENSOR_FIELD_CASING) {
                missing.put(ModBlocks.CONSTANT_TENSOR_FIELD_CASING, missing.getOrDefault(ModBlocks.CONSTANT_TENSOR_FIELD_CASING, 0) + 1);
            }
        }
        for (BlockPos rel : CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CAUSAL_ANCHOR_CORE) {
                missing.put(ModBlocks.CAUSAL_ANCHOR_CORE, missing.getOrDefault(ModBlocks.CAUSAL_ANCHOR_CORE, 0) + 1);
            }
        }
        for (BlockPos rel : SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, facing));
            if (!world.isBlockLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_SPINOR_FIELD_CASING) {
                missing.put(ModBlocks.CONSTANT_SPINOR_FIELD_CASING, missing.getOrDefault(ModBlocks.CONSTANT_SPINOR_FIELD_CASING, 0) + 1);
            }
        }
        BlockPos meInterfacePos = controllerPos.add(rotate(ME_INTERFACE_REL, facing));
        if (world.isBlockLoaded(meInterfacePos) && world.getBlockState(meInterfacePos).getBlock() != ModBlocks.SUPER_CRAFTING_INTERFACE) {
            missing.put(ModBlocks.SUPER_CRAFTING_INTERFACE, missing.getOrDefault(ModBlocks.SUPER_CRAFTING_INTERFACE, 0) + 1);
        }

        if (missing.isEmpty()) {
            assemble(world, controllerPos);
            return true;
        }

        net.minecraft.entity.player.InventoryPlayer inv = player.inventory;
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (net.minecraft.item.ItemStack stack : inv.mainInventory) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == net.minecraft.item.Item.getItemFromBlock(block)) {
                    int need = entry.getValue();
                    int have = stack.getCount();
                    if (have >= need) {
                        entry.setValue(0);
                    } else {
                        entry.setValue(need - have);
                    }
                    break;
                }
            }
        }

        for (int count : needed.values()) {
            if (count > 0) return false;
        }

        // Consume materials
        for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
            Block block = entry.getKey();
            int remaining = entry.getValue();
            net.minecraft.item.Item item = net.minecraft.item.Item.getItemFromBlock(block);
            for (int i = 0; i < inv.mainInventory.size() && remaining > 0; i++) {
                net.minecraft.item.ItemStack stack = inv.mainInventory.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.decrStackSize(i, take).getCount();
                    remaining -= removed;
                }
            }
        }

        placeMissingBlocks(world, controllerPos, player);
        return true;
    }

    public static class ValidationResult {
        public final boolean passed;
        public final Map<Block, Integer> missing;
        public final int causalAnchorCount;
        public final int parallelLimit;

        public ValidationResult(boolean passed, Map<Block, Integer> missing, int causalAnchorCount, int parallelLimit) {
            this.passed = passed;
            this.missing = Collections.unmodifiableMap(missing);
            this.causalAnchorCount = causalAnchorCount;
            this.parallelLimit = parallelLimit;
        }
    }
}
