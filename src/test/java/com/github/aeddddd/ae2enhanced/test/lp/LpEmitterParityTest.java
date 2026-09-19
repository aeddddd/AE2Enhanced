package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 发射台供料的 LP 处理回归测试.
 * <p>原生语义({@code CraftingTreeNode}):可发射物品<b>不展开任何样板分支</b>
 * (addNode 直接返回),提取库存后剩余量记为"发射"免费满足——不算缺料、
 * 不传播原料需求、即使有可用样板也不走合成.旧 LP 对发射节点整单回落原生,
 * 大网络(发射台常见)下即触发原生递归卡死链路.</p>
 */
public class LpEmitterParityTest {

    private static IAEItemStack item(net.minecraft.item.Item i) {
        return AEItemStack.fromItemStack(new ItemStack(i));
    }

    private static IAEItemStack block(net.minecraft.block.Block b) {
        return AEItemStack.fromItemStack(new ItemStack(b));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(template.getStackSize() * multiplier);
        return copy;
    }

    /**
     * 子节点发射:emitable 物品提取库存后剩余量免费满足(不记缺料),
     * 且即使有可用样板也不走合成(对齐原生).
     */
    @Test
    public void testEmitterChildNoMissingNoCrafting() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack e = item(Items.DIAMOND); // 发射台供料
        IAEItemStack x = block(Blocks.STONE);
        IAEItemStack b = item(Items.EMERALD);
        // E 有样板(1X→1E),但原生语义下 emitable 不走样板
        env.addPattern(new ProcessingPatternBuilder(e).addPreciseInput(1, x).build());
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(2, e).build());
        env.addEmitable(e);
        env.addStoredItem(mult(e, 3)); // 库存 3,需求 2×5=10 → 提取 3 + 发射 7
        env.addStoredItem(mult(x, 1000));

        CraftingJob job = env.runLp(mult(b, 5));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).as("发射覆盖剩余量,计划可提交").isFalse();
        assertThat(view.missingItems()).as("发射不计缺料").isEmpty();
        assertThat(view.usedItems().getOrDefault(e, 0L)).as("库存部分照常实取").isEqualTo(3L);
        // emitable 物品不走样板合成(对齐原生):计划的样板调用中没有任何产出 E 的样板
        for (Map.Entry<appeng.api.networking.crafting.ICraftingPatternDetails, Long> entry : view.patternTimes()
                .entrySet()) {
            for (IAEItemStack output : entry.getKey().getCondensedOutputs()) {
                if (output != null) {
                    assertThat(output.isSameType(e)).as("emitable 物品不走样板").isFalse();
                }
            }
        }
    }

    /**
     * 根节点发射:直接下单 emitable 物品——提取 + 发射满足,计划可提交.
     * (旧实现 emitter_root 整单回落原生)
     */
    @Test
    public void testEmitterRootSuccess() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack e = item(Items.DIAMOND);
        env.addEmitable(e);
        env.addStoredItem(mult(e, 3));

        CraftingJob job = env.runLp(mult(e, 10));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).as("发射根计划可提交").isFalse();
        assertThat(view.missingItems()).isEmpty();
        // 原生 CraftingJob.ignore(output) 语义:请求物自身库存不参与计划,
        // 10 份全部由发射满足,库存 3 不动
        assertThat(view.usedItems().getOrDefault(e, 0L)).isEqualTo(0L);
        assertThat(view.patternTimes()).as("发射根无任何合成任务").isEmpty();
    }

    /**
     * 对照:非 emitable 终端仍按缺料记账(发射修复不影响缺料语义).
     */
    @Test
    public void testNonEmitterTerminalStillMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack t = item(Items.DIAMOND); // 无样板、非发射 → 终端
        IAEItemStack b = item(Items.EMERALD);
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, t).build());

        CraftingJob job = env.runLp(mult(b, 5));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).as("缺料计划应为模拟态").isTrue();
        assertThat(view.missingItems().getOrDefault(t, 0L)).isEqualTo(5L);
    }
}
