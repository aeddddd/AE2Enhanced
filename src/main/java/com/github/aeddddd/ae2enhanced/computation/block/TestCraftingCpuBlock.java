package com.github.aeddddd.ae2enhanced.computation.block;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.computation.blockentity.TestCraftingCpuBlockEntity;

/**
 * 【仅开发环境】测试用单方块合成 CPU.
 * <p>替代临时下线的超因果计算核心多方块：放置并接入 ME 网络后,
 * 作为一个拥有无限存储与 16 协处理器的 AE2 Crafting CPU 参与自动合成调度.
 * 该方块只在开发环境注册（见 {@link com.github.aeddddd.ae2enhanced.util.DevEnvironment}）.</p>
 */
public class TestCraftingCpuBlock extends Block implements EntityBlock {

    public TestCraftingCpuBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestCraftingCpuBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null : (lvl, p, st, be) -> {
            if (be instanceof TestCraftingCpuBlockEntity cpu) {
                cpu.serverTick();
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.ae2enhanced.test_crafting_cpu")
                .withStyle(ChatFormatting.RED));
    }
}
