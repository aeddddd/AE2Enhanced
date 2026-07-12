package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.util.StructureUtils;

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

    private static AbstractMultiblockStructure INSTANCE;
    private static boolean initialized = false;

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
     * 在方块注册完成后初始化 {@link AbstractMultiblockStructure} 实例。
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        StructureDefinition definition = StructureDefinition.builder()
                .addAll(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), TENSOR_CASING_SET)
                .addAll(ModBlocks.CAUSAL_ANCHOR_CORE.get(), CAUSAL_ANCHOR_SET)
                .addAll(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), SPINOR_CASING_SET)
                .add(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), ME_INTERFACE_REL)
                .interfacePos(ME_INTERFACE_REL)
                .build();
        INSTANCE = new Impl(definition);
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "SupercausalStructure has not been initialized. Call init() during FMLCommonSetupEvent.");
        }
    }

    public static AbstractMultiblockStructure getInstance() {
        ensureInitialized();
        return INSTANCE;
    }

    public static ValidationResult validate(Level level, BlockPos controllerPos) {
        return getInstance().validateDetailed(level, controllerPos);
    }

    public static ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
        return getInstance().validateDetailed(level, controllerPos);
    }

    public static Set<BlockPos> getAllSet() {
        return ALL_STRUCTURE_SET;
    }

    public static Direction getControllerFacing(Level level, BlockPos controllerPos) {
        return getInstance().getRotation(level, controllerPos);
    }

    public static Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos) {
        return getInstance().getExpectedBlocks(level, controllerPos);
    }

    public static Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
        return getInstance().getMissingMap(level, controllerPos);
    }

    public static void assemble(Level level, BlockPos controllerPos) {
        getInstance().assemble(level, controllerPos);
    }

    public static void disassemble(Level level, BlockPos controllerPos) {
        getInstance().disassemble(level, controllerPos);
    }

    public static void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
        getInstance().placeMissingBlocks(level, controllerPos, player);
    }

    public static boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player) {
        return getInstance().tryConsumeAndPlace(level, controllerPos, player);
    }

    /**
     * 返回每个虚拟 CPU 的并行合成上限（由配置决定）。
     */
    public static int computeParallel() {
        return AE2EnhancedConfig.COMMON.computationMaxParallel.get();
    }

    private static class Impl extends AbstractMultiblockStructure {

        private Impl(StructureDefinition definition) {
            super(definition);
        }

        /**
         * Returns the facing used for structure rotation.
         * We use the opposite of the controller's block facing so that the structure
         * expands behind the controller, leaving the controller's front face open to air.
         */
        @Override
        public Direction getRotation(Level level, BlockPos controllerPos) {
            BlockState state = level.getBlockState(controllerPos);
            if (state.getBlock() instanceof ComputationControllerBlock) {
                return state.getValue(ComputationControllerBlock.FACING).getOpposite();
            }
            return Direction.NORTH;
        }

        @Override
        public ValidationResult validateDetailed(Level level, BlockPos controllerPos) {
            Map<Block, Integer> missing = new LinkedHashMap<>();
            boolean allChunksLoaded = true;
            int causalCount = 0;
            Direction facing = getRotation(level, controllerPos);

            for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
                BlockPos rel = entry.getKey();
                Block expected = entry.getValue();
                // 控制器位置由核心方块占用，跳过张量外壳检查
                if (rel.equals(CONTROLLER_REL) && expected == ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get()) {
                    continue;
                }
                BlockPos actual = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (!level.isLoaded(actual)) {
                    allChunksLoaded = false;
                    missing.merge(expected, 1, Integer::sum);
                    continue;
                }
                if (level.getBlockState(actual).getBlock() != expected) {
                    missing.merge(expected, 1, Integer::sum);
                } else if (expected == ModBlocks.CAUSAL_ANCHOR_CORE.get()) {
                    causalCount++;
                }
            }

            boolean passed = missing.isEmpty() && allChunksLoaded;
            int parallel = passed ? computeParallel() : 0;
            return new ValidationResult(passed, missing, allChunksLoaded, causalCount, parallel);
        }

        @Override
        public Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos) {
            Map<Block, Integer> missing = new LinkedHashMap<>();
            Direction facing = getRotation(level, controllerPos);
            for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
                BlockPos rel = entry.getKey();
                Block expected = entry.getValue();
                if (rel.equals(CONTROLLER_REL) && expected == ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get()) {
                    continue;
                }
                BlockPos actual = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (!level.isLoaded(actual)) {
                    continue;
                }
                if (level.getBlockState(actual).getBlock() != expected) {
                    missing.merge(expected, 1, Integer::sum);
                }
            }
            return missing;
        }

        @Override
        public void placeMissingBlocks(Level level, BlockPos controllerPos, Player player) {
            if (level.isClientSide()) {
                return;
            }
            Direction facing = getRotation(level, controllerPos);
            for (Map.Entry<BlockPos, Block> entry : definition.getExpectedBlocks()) {
                BlockPos rel = entry.getKey();
                Block block = entry.getValue();
                if (isControllerTensorOverlap(rel, block)) {
                    continue;
                }
                BlockPos pos = controllerPos.offset(StructureUtils.rotate(rel, facing));
                if (level.getBlockState(pos).getBlock() == block) {
                    continue;
                }
                if (player != null && pos.equals(player.blockPosition())) {
                    movePlayerToSafety(level, controllerPos, player);
                }
                level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            }
            assemble(level, controllerPos);
        }

        private static boolean isControllerTensorOverlap(BlockPos rel, Block expected) {
            return rel.equals(CONTROLLER_REL) && expected == ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get();
        }

        private static void movePlayerToSafety(Level level, BlockPos controllerPos, Player player) {
            BlockPos safe = controllerPos.above(2);
            for (int dy = 2; dy < 10; dy++) {
                BlockPos candidate = controllerPos.above(dy);
                if (level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above())) {
                    safe = candidate;
                    break;
                }
            }
            player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        }
    }
}
