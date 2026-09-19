package com.github.aeddddd.ae2enhanced.test.lp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.test.support.PlanView;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 替代感知接管(矿词等真实替代候选)语义测试.
 * <p>口径(与原生一致):替代仅作用于<b>库存提取</b>(先编码物、后候选),
 * 缺额的合成仍走编码物;精确父样板的需求不吃候选库存(执行有效性:
 * 精确父只接受编码物,执行层 canCraft 已实证对启用替代的样板逐槽接受候选).</p>
 */
public class LpSubstituteParityTest {

    private static IAEItemStack block(net.minecraft.block.Block b) {
        return AEItemStack.fromItemStack(new ItemStack(b));
    }

    private static IAEItemStack mult(IAEItemStack template, long multiplier) {
        IAEItemStack copy = template.copy();
        copy.setStackSize(template.getStackSize() * multiplier);
        return copy;
    }

    private static ReusePatternBuilder substitutePattern(IAEItemStack output, long count,
            IAEItemStack input, IAEItemStack alt) {
        return new ReusePatternBuilder(output)
                .addPreciseInput(count, input)
                .nativeStyleSubstituteList()
                .substituteAlternatives(input, alt)
                .canSubstitute(true);
    }

    /**
     * 编码物零库存且不可合成,候选库存充足 → 候选全额顶替,计划可提交.
     * (旧实现:unclean_inputs 整单回落原生;精确口径:误报缺料)
     */
    @Test
    public void testCandidateFullyCovers() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack alt = block(Blocks.DIRT);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env.addPattern(substitutePattern(b, 1, a, alt).build());
        env.addStoredItem(mult(alt, 100));

        CraftingJob job = env.runLp(mult(b, 10));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).isFalse();
        assertThat(view.missingItems()).isEmpty();
        assertThat(view.usedItems().getOrDefault(alt, 0L)).isEqualTo(10L);
        assertThat(view.usedItems().getOrDefault(a, 0L)).isEqualTo(0L);
    }

    /**
     * 编码物部分库存 → 先吃编码物,候选补差额(原生替代列表顺序语义).
     */
    @Test
    public void testEncodedPreferredThenCandidate() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack alt = block(Blocks.DIRT);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env.addPattern(substitutePattern(b, 1, a, alt).build());
        env.addStoredItem(mult(a, 3));
        env.addStoredItem(mult(alt, 100));

        CraftingJob job = env.runLp(mult(b, 5));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).isFalse();
        assertThat(view.missingItems()).isEmpty();
        assertThat(view.usedItems().getOrDefault(a, 0L)).as("编码物优先").isEqualTo(3L);
        assertThat(view.usedItems().getOrDefault(alt, 0L)).as("候选补差额").isEqualTo(2L);
    }

    /**
     * 编码物与候选都无库存 → 缺额合成仍走编码物(原生语义:
     * 替代不产生"合成候选"的路径).
     */
    @Test
    public void testShortfallCraftsEncoded() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack alt = block(Blocks.DIRT);
        IAEItemStack x = block(Blocks.SAND);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        appeng.api.networking.crafting.ICraftingPatternDetails aPattern = new ProcessingPatternBuilder(a)
                .addPreciseInput(1, x).build();
        env.addPattern(aPattern);
        env.addPattern(substitutePattern(b, 1, a, alt).build());
        env.addStoredItem(mult(x, 100));

        CraftingJob job = env.runLp(mult(b, 5));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).isFalse();
        assertThat(view.missingItems()).isEmpty();
        assertThat(view.patternTimes().getOrDefault(aPattern, -1L))
                .as("缺额合成编码物").isEqualTo(5L);
        assertThat(view.usedItems().getOrDefault(alt, 0L)).isEqualTo(0L);
    }

    /**
     * 共享子节点的执行有效性:精确父(canSubstitute=false)的需求不得吃候选库存,
     * 只有可替代父的需求可吃——否则精确父在执行层找不到编码物而卡死.
     */
    @Test
    public void testPreciseParentNeverDrainsCandidates() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack alt = block(Blocks.DIRT);
        IAEItemStack x = block(Blocks.SAND);
        IAEItemStack p = block(Blocks.PLANKS); // 精确父产物
        IAEItemStack s = block(Blocks.GLASS); // 可替代父产物
        IAEItemStack r = block(Blocks.COBBLESTONE); // 根
        appeng.api.networking.crafting.ICraftingPatternDetails aPattern = new ProcessingPatternBuilder(a)
                .addPreciseInput(1, x).build();
        env.addPattern(aPattern);
        // 精确父:1A→1P(不启用替代)
        env.addPattern(new ReusePatternBuilder(p).addPreciseInput(1, a).canSubstitute(false).build());
        // 可替代父:1A(候选 alt)→1S
        env.addPattern(substitutePattern(s, 1, a, alt).build());
        // 根:1P + 1S → 1R
        env.addPattern(new ProcessingPatternBuilder(r).addPreciseInput(1, p).addPreciseInput(1, s).build());
        env.addStoredItem(mult(x, 100));
        env.addStoredItem(mult(alt, 100)); // 只有候选库存

        CraftingJob job = env.runLp(mult(r, 5));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).isFalse();
        assertThat(view.missingItems()).isEmpty();
        assertThat(view.usedItems().getOrDefault(alt, 0L))
                .as("候选只服务可替代父的 5 份需求;精确父的 5 份必须走编码物合成")
                .isEqualTo(5L);
        assertThat(view.patternTimes().getOrDefault(aPattern, -1L))
                .as("精确父的缺额合成编码物").isEqualTo(5L);
    }

    /**
     * 替代候选也无库存且编码物不可合成 → 缺料按编码物记账(对齐原生).
     */
    @Test
    public void testSubstituteShortfallReportsEncodedMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack alt = block(Blocks.DIRT);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        env.addPattern(substitutePattern(b, 1, a, alt).build());

        CraftingJob job = env.runLp(mult(b, 5));
        PlanView view = PlanView.of(job);
        assertThat(view.simulation()).isTrue();
        assertThat(view.missingItems().getOrDefault(a, 0L)).isEqualTo(5L);
        assertThat(view.missingItems().getOrDefault(alt, 0L)).as("缺料记编码物,不记候选").isEqualTo(0L);
    }
}
