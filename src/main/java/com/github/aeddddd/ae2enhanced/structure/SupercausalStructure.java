package com.github.aeddddd.ae2enhanced.structure;

import java.util.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.computation.block.ComputationControllerBlock;
import com.github.aeddddd.ae2enhanced.computation.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlock;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

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
    public static Direction getControllerFacing(Level world, BlockPos controllerPos) {
        BlockState state = world.getBlockState(controllerPos);
        if (state.getBlock() instanceof ComputationControllerBlock) {
            return state.getValue(ComputationControllerBlock.FACING).getOpposite();
        }
        return Direction.NORTH;
    }

    public static BlockPos rotate(BlockPos rel, Direction facing) {
        if (facing == Direction.NORTH) return rel;
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
     * 验证结构完整性.
     * @return 验证结果,包含是否通过、缺失方块统计、因果锚定核心数量和计算出的并行上限.
     */
    public static ValidationResult validate(Level world, BlockPos controllerPos) {
        Direction facing = getControllerFacing(world, controllerPos);
        Map<Block, Integer> missing = new LinkedHashMap<>();
        int causalCount = 0;

        // 验证恒定张量场外壳
        for (BlockPos rel : TENSOR_CASING_SET) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!world.isLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get()) {
                if (actual.equals(controllerPos)) continue; // 控制器位置由核心方块占用,跳过
                missing.put(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), missing.getOrDefault(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), 0) + 1);
            }
        }

        // 验证因果锚定核心
        for (BlockPos rel : CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!world.isLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() == ModBlocks.CAUSAL_ANCHOR_CORE.get()) {
                causalCount++;
            } else {
                missing.put(ModBlocks.CAUSAL_ANCHOR_CORE.get(), missing.getOrDefault(ModBlocks.CAUSAL_ANCHOR_CORE.get(), 0) + 1);
            }
        }

        // 验证恒定旋量场外壳
        for (BlockPos rel : SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!world.isLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get()) {
                missing.put(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), missing.getOrDefault(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), 0) + 1);
            }
        }

        // 验证 ME 接口
        BlockPos meInterfacePos = controllerPos.offset(rotate(ME_INTERFACE_REL, facing));
        boolean meInterfaceValid = false;
        if (world.isLoaded(meInterfacePos)) {
            meInterfaceValid = world.getBlockState(meInterfacePos).getBlock() == ModBlocks.MULTIBLOCK_ME_INTERFACE.get();
        }
        if (!meInterfaceValid) {
            missing.put(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), missing.getOrDefault(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), 0) + 1);
        }

        boolean passed = missing.isEmpty();
        int parallel = passed ? computeParallel() : 0;

        return new ValidationResult(passed, missing, causalCount, parallel);
    }

    /**
     * 返回每个虚拟 CPU 的并行合成上限（由配置决定）。
     */
    public static int computeParallel() {
        return AE2EnhancedConfig.COMMON.computationMaxParallel.get();
    }

    public static void assemble(Level world, BlockPos controllerPos) {
        if (world.isClientSide()) return;
        BlockEntity te = world.getBlockEntity(controllerPos);
        if (te instanceof ComputationCoreBlockEntity tile) {
            ValidationResult result = validate(world, controllerPos);
            if (result.passed) {
                BlockPos interfacePos = getMeInterfacePos(world, controllerPos);
                updateMeInterfaceState(world, controllerPos, true);
                tile.assemble(result.parallelLimit, interfacePos);
            }
        }
    }

    public static void disassemble(Level world, BlockPos controllerPos) {
        if (world.isClientSide()) return;
        BlockEntity te = world.getBlockEntity(controllerPos);
        if (te instanceof ComputationCoreBlockEntity tile) {
            tile.disassemble();
        }
        updateMeInterfaceState(world, controllerPos, false);
    }

    private static BlockPos getMeInterfacePos(Level world, BlockPos controllerPos) {
        Direction facing = getControllerFacing(world, controllerPos);
        return controllerPos.offset(rotate(ME_INTERFACE_REL, facing));
    }

    private static void updateMeInterfaceState(Level world, BlockPos controllerPos, boolean formed) {
        BlockPos interfacePos = getMeInterfacePos(world, controllerPos);
        if (!world.isLoaded(interfacePos)) {
            return;
        }
        BlockState state = ModBlocks.MULTIBLOCK_ME_INTERFACE.get().defaultBlockState()
                .setValue(MultiblockMeInterfaceBlock.FORMED, formed);
        if (world.getBlockState(interfacePos).getBlock() == ModBlocks.MULTIBLOCK_ME_INTERFACE.get()) {
            world.setBlock(interfacePos, state, Block.UPDATE_ALL);
            BlockEntity te = world.getBlockEntity(interfacePos);
            if (te instanceof com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity me) {
                me.setControllerPos(formed ? controllerPos : null);
            }
        }
    }

    /**
     * Creative mode: place all missing blocks instantly.
     */
    public static void placeMissingBlocks(Level world, BlockPos controllerPos, Player player) {
        if (world.isClientSide()) return;
        Direction facing = getControllerFacing(world, controllerPos);

        placeBlocks(world, controllerPos, TENSOR_CASING_SET, ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), facing, player);
        placeBlocks(world, controllerPos, CAUSAL_ANCHOR_SET, ModBlocks.CAUSAL_ANCHOR_CORE.get(), facing, player);
        placeBlocks(world, controllerPos, SPINOR_CASING_SET, ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), facing, player);

        BlockPos meInterfacePos = controllerPos.offset(rotate(ME_INTERFACE_REL, facing));
        if (world.getBlockState(meInterfacePos).getBlock() != ModBlocks.MULTIBLOCK_ME_INTERFACE.get()) {
            world.setBlock(meInterfacePos, ModBlocks.MULTIBLOCK_ME_INTERFACE.get().defaultBlockState(), Block.UPDATE_ALL);
        }

        assemble(world, controllerPos);
    }

    private static void placeBlocks(Level world, BlockPos controllerPos, Set<BlockPos> set, Block block, Direction facing, Player player) {
        for (BlockPos rel : set) {
            if (rel.equals(CONTROLLER_REL)) continue; // skip controller position
            BlockPos pos = controllerPos.offset(rotate(rel, facing));
            if (world.getBlockState(pos).getBlock() == block) continue;
            // avoid suffocating player
            if (player != null && pos.equals(player.blockPosition())) {
                movePlayerToSafety(world, controllerPos, player);
            }
            world.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void movePlayerToSafety(Level world, BlockPos controllerPos, Player player) {
        BlockPos safe = controllerPos.above(2);
        for (int dy = 2; dy < 10; dy++) {
            BlockPos candidate = controllerPos.above(dy);
            if (world.isEmptyBlock(candidate) && world.isEmptyBlock(candidate.above())) {
                safe = candidate;
                break;
            }
        }
        player.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
    }

    /**
     * Survival mode: check inventory, consume materials, place missing blocks.
     * @return true if successful
     */
    public static boolean tryConsumeAndPlace(Level world, BlockPos controllerPos, Player player) {
        if (world.isClientSide()) return false;
        Direction facing = getControllerFacing(world, controllerPos);

        Map<Block, Integer> missing = new LinkedHashMap<>();
        for (BlockPos rel : TENSOR_CASING_SET) {
            if (rel.equals(CONTROLLER_REL)) continue; // skip controller position
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!world.isLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get()) {
                missing.put(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), missing.getOrDefault(ModBlocks.CONSTANT_TENSOR_FIELD_CASING.get(), 0) + 1);
            }
        }
        for (BlockPos rel : CAUSAL_ANCHOR_SET) {
            if (rel.equals(CONTROLLER_REL)) continue; // skip controller position
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!world.isLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CAUSAL_ANCHOR_CORE.get()) {
                missing.put(ModBlocks.CAUSAL_ANCHOR_CORE.get(), missing.getOrDefault(ModBlocks.CAUSAL_ANCHOR_CORE.get(), 0) + 1);
            }
        }
        for (BlockPos rel : SPINOR_CASING_SET) {
            if (rel.equals(CONTROLLER_REL)) continue; // skip controller position
            BlockPos actual = controllerPos.offset(rotate(rel, facing));
            if (!world.isLoaded(actual)) continue;
            if (world.getBlockState(actual).getBlock() != ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get()) {
                missing.put(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), missing.getOrDefault(ModBlocks.CONSTANT_SPINOR_FIELD_CASING.get(), 0) + 1);
            }
        }
        BlockPos meInterfacePos = controllerPos.offset(rotate(ME_INTERFACE_REL, facing));
        if (world.isLoaded(meInterfacePos) && world.getBlockState(meInterfacePos).getBlock() != ModBlocks.MULTIBLOCK_ME_INTERFACE.get()) {
            missing.put(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), missing.getOrDefault(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), 0) + 1);
        }

        if (missing.isEmpty()) {
            assemble(world, controllerPos);
            return true;
        }

        Inventory inv = player.getInventory();
        Map<Block, Integer> needed = new LinkedHashMap<>(missing);

        for (ItemStack stack : inv.items) {
            if (stack.isEmpty()) continue;
            for (Map.Entry<Block, Integer> entry : needed.entrySet()) {
                Block block = entry.getKey();
                if (stack.getItem() == block.asItem()) {
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
            Item item = block.asItem();
            for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
                ItemStack stack = inv.items.get(i);
                if (stack.getItem() == item) {
                    int take = Math.min(stack.getCount(), remaining);
                    int removed = inv.removeItem(i, take).getCount();
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
