package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * CrT 不消耗配方（配方级返还,{@code .reuse()} 催化剂）的计划层测试.
 * <p>物品本身无容器物（{@code hasContainerItem=false})，不消耗语义只存在于
 * 配方级 {@code getRemainingItems}——原生计划（只认 Item 容器 API）把催化剂
 * 全额消耗，库存不足即报缺料；本模组各求解路径应按"种子保留"记账.</p>
 * <p>路径分工（生产路由）:根请求的自引用/环由 detector 路由到 SpecialCraftingJob;
 * DAG 引擎处理非特殊请求，催化剂配方以"深层循环边界"形式嵌在依赖图内
 * （CycleBoundarySolver).两侧分别覆盖.</p>
 */
public class CatalystReuseTest {

    /** 纯 CrT 风格催化剂：无容器物的普通物品，仅配方级返还. */
    private static final Item REUSED_CATALYST = new Item();

    private static IAEItemStack item(Item i) {
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
     * 自引用增殖 + 催化剂:X(reuse) + A → 2A.库存 1 份催化剂即可整批求解
     * （运行时逐次返还）;used（催化剂） = 1 份种子.
     * 对照：原生按全额消耗记账，库存不足报缺料、计划不可提交.
     */
    @Test
    public void testSelfRefCatalystReuse() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack e = item(Items.CAKE);
        IAEItemStack x = item(REUSED_CATALYST);
        env.addPattern(new ReusePatternBuilder(mult(a, 2))
                .addPreciseInput(1, a)
                .addPreciseInput(1, x)
                .reused(x)
                .build());
        // 深层边界入口:E 依赖 A,A 由催化剂自引用样板产出
        env.addPattern(new ProcessingPatternBuilder(e).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 4)); // 增殖种子
        env.addStoredItem(x); // 催化剂种子 1

        IAEItemStack request = mult(a, 10);

        // 根请求路径(生产路由 → SpecialCraftingJob)
        PlanView specialPlan = PlanView.of(env.runSpecial(request));
        assertThat(specialPlan.simulation()).as("特殊路径计划可提交").isFalse();
        assertThat(specialPlan.missingItems()).as("特殊路径无缺料").isEmpty();
        assertThat(specialPlan.usedItems().get(RecursiveCraftingHelper.canon(x)))
                .as("特殊路径 used(催化剂)=种子").isEqualTo(1L);
        assertThat(specialPlan.usedItems().get(RecursiveCraftingHelper.canon(a)))
                .as("特殊路径 used(A)=种子").isEqualTo(1L);

        // 深层边界路径(生产路由 → DAG,催化剂自引用为嵌套边界)
        PlanView dagPlan = PlanView.of(env.runDag(mult(e, 5)));
        assertThat(dagPlan.simulation()).as("DAG 计划可提交").isFalse();
        assertThat(dagPlan.missingItems()).as("DAG 无缺料").isEmpty();
        assertThat(dagPlan.usedItems().get(RecursiveCraftingHelper.canon(x)))
                .as("DAG used(催化剂)=种子").isEqualTo(1L);

        // 对照:原生无法识别配方级返还,按全额消耗报缺料
        PlanView nativePlan = PlanView.of(env.runNative(request));
        assertThat(nativePlan.simulation()).as("原生判为不可提交").isTrue();
        assertThat(nativePlan.missingItems()).as("原生有缺料记录").isNotEmpty();
    }

    /**
     * 跨样板增殖环中的催化剂步骤：1A→1B、1B + X(reuse) → 2A.
     * 环求解器批量模拟中，环外催化剂输入应按种子记账而非全额消耗.
     */
    @Test
    public void testCycleStepCatalystReuse() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack b = block(Blocks.COBBLESTONE);
        IAEItemStack e = item(Items.CAKE);
        IAEItemStack x = item(REUSED_CATALYST);
        env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
        env.addPattern(new ReusePatternBuilder(mult(a, 2))
                .addPreciseInput(1, b)
                .addPreciseInput(1, x)
                .reused(x)
                .build());
        // 深层边界入口:E 依赖环键 A
        env.addPattern(new ProcessingPatternBuilder(e).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 2)); // 环键种子
        env.addStoredItem(x); // 催化剂种子 1

        // 根请求路径(环求解)
        PlanView specialPlan = PlanView.of(env.runSpecial(mult(a, 10)));
        assertThat(specialPlan.simulation()).as("环求解计划可提交").isFalse();
        assertThat(specialPlan.missingItems()).as("环求解无缺料").isEmpty();
        assertThat(specialPlan.usedItems().get(RecursiveCraftingHelper.canon(x)))
                .as("环求解 used(催化剂)=种子").isEqualTo(1L);

        // 深层边界路径(请求 5E → 边界 5A)
        PlanView dagPlan = PlanView.of(env.runDag(mult(e, 5)));
        assertThat(dagPlan.simulation()).as("DAG 计划可提交").isFalse();
        assertThat(dagPlan.missingItems()).as("DAG 无缺料").isEmpty();
        assertThat(dagPlan.usedItems().get(RecursiveCraftingHelper.canon(x)))
                .as("DAG used(催化剂)=种子").isEqualTo(1L);
    }

    /**
     * 催化剂零库存：首份无法自供，必须判不可提交且有缺料记录
     * （不能靠虚拟返还凭空自举——与容器物高水位语义一致）.
     */
    @Test
    public void testCatalystZeroStockMissing() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack e = item(Items.CAKE);
        IAEItemStack x = item(REUSED_CATALYST);
        env.addPattern(new ReusePatternBuilder(mult(a, 2))
                .addPreciseInput(1, a)
                .addPreciseInput(1, x)
                .reused(x)
                .build());
        env.addPattern(new ProcessingPatternBuilder(e).addPreciseInput(1, a).build());
        env.addStoredItem(mult(a, 4)); // 有增殖种子,但催化剂为零

        PlanView specialPlan = PlanView.of(env.runSpecial(mult(a, 10)));
        assertThat(specialPlan.simulation()).as("特殊路径判为不可提交").isTrue();
        assertThat(specialPlan.missingItems().get(RecursiveCraftingHelper.canon(x)))
                .as("特殊路径缺料含催化剂").isNotNull();

        // 深层边界路径同样必须报缺料(虚拟返还不凭空自举首份)
        PlanView dagPlan = PlanView.of(env.runDag(mult(e, 10)));
        assertThat(dagPlan.simulation()).as("DAG 判为不可提交").isTrue();
        assertThat(dagPlan.missingItems().get(RecursiveCraftingHelper.canon(x)))
                .as("DAG 缺料含催化剂").isNotNull();
    }

    /**
     * 配方级特异性（本 Issue 核心）：同一物品 X 在配方 1 中被消耗（无返还）、
     * 在配方 2 中为催化剂（reuse).请求走配方 2 时按种子记账可提交；
     * 走配方 1 时按全额消耗记账，库存不足报缺料.
     */
    @Test
    public void testItemReusedOnlyInSpecificRecipe() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack a = block(Blocks.STONE);
        IAEItemStack d = block(Blocks.DIRT);
        IAEItemStack e = item(Items.CAKE);
        IAEItemStack f = item(Items.BREAD);
        IAEItemStack x = item(REUSED_CATALYST);
        // 配方 1:X + D → E,X 被消耗（配方无返还）
        env.addPattern(new ReusePatternBuilder(e)
                .addPreciseInput(1, x)
                .addPreciseInput(1, d)
                .build());
        // 配方 2:X(reuse) + A → 2A,X 不消耗
        env.addPattern(new ReusePatternBuilder(mult(a, 2))
                .addPreciseInput(1, a)
                .addPreciseInput(1, x)
                .reused(x)
                .build());
        // 深层边界入口:F 依赖 A
        env.addPattern(new ProcessingPatternBuilder(f).addPreciseInput(1, a).build());
        env.addStoredItem(x); // 仅 1 份
        env.addStoredItem(mult(d, 64));
        env.addStoredItem(mult(a, 2));

        // 走配方 1(消耗型):5 份需求 vs 1 份库存 → 缺 4
        PlanView consumePlan = PlanView.of(env.runDag(mult(e, 5)));
        assertThat(consumePlan.simulation()).as("消耗型配方判为不可提交").isTrue();
        assertThat(consumePlan.missingItems().get(RecursiveCraftingHelper.canon(x)))
                .as("消耗型配方缺料 X=4").isEqualTo(4L);

        // 走配方 2(催化剂型,深层边界):同一物品按种子记账,可提交
        PlanView reusePlan = PlanView.of(env.runDag(mult(f, 10)));
        assertThat(reusePlan.simulation()).as("催化剂配方计划可提交").isFalse();
        assertThat(reusePlan.missingItems()).as("催化剂配方无缺料").isEmpty();
        assertThat(reusePlan.usedItems().get(RecursiveCraftingHelper.canon(x)))
                .as("催化剂配方 used(X)=种子").isEqualTo(1L);
    }
}
