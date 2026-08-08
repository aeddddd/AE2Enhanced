package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraftExtension;

import io.netty.buffer.Unpooled;

/**
 * 特殊计划显示信息(SpecialPlanInfo)计算与编解码测试.
 */
@ExtendWith(BootstrapMinecraftExtension.class)
public class SpecialPlanInfoTest {

    /** 编解码路径依赖 AE2 key type 注册表(纯 JUnit 环境需手动引导,幂等). */
    @BeforeAll
    static void bootstrapKeyTypes() {
        com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap.bootstrap();
    }

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }

    /** 自增殖计划:kind=SELF_DUP,每次 1→2,总次数与种子正确. */
    @Test
    public void testSelfDupInfo() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(stone);

        var plan = env.runSpecialSimulation(mult(stone, 10), CalculationStrategy.REPORT_MISSING_ITEMS);
        var info = SpecialPlanInfo.compute(plan);

        var entry = info.entryFor(stone.what());
        assertThat(entry).isNotNull();
        assertThat(entry.kind()).isEqualTo(SpecialPlanInfo.KIND_SELF_DUP);
        assertThat(entry.perRoundConsume()).isEqualTo(1);
        assertThat(entry.perRoundProduce()).isEqualTo(2);
        assertThat(entry.totalCrafts()).isEqualTo(10);
        assertThat(entry.initialExtract()).isEqualTo(1);
        assertThat(info.callCountOf(stone.what())).isEqualTo(10);
    }

    /** θ 循环计划:kind=CYCLE,轮次=GCD 恢复,每轮消耗/产出精确. */
    @Test
    public void testCycleInfo() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 8));
        env.addStoredItem(sand);

        var plan = env.runSpecialSimulation(mult(stone, 8), CalculationStrategy.REPORT_MISSING_ITEMS);
        var info = SpecialPlanInfo.compute(plan);

        var stoneEntry = info.entryFor(stone.what());
        assertThat(stoneEntry).isNotNull();
        assertThat(stoneEntry.kind()).isEqualTo(SpecialPlanInfo.KIND_CYCLE);
        assertThat(stoneEntry.rounds()).isEqualTo(4); // 总 4 次 / 每轮 1 次
        assertThat(stoneEntry.perRoundConsume()).isEqualTo(2); // crush 1 + charge 1
        assertThat(stoneEntry.perRoundProduce()).isEqualTo(4); // back ×1
        assertThat(stoneEntry.initialExtract()).isEqualTo(2);

        var cobbleEntry = info.entryFor(cobble.what());
        assertThat(cobbleEntry).isNotNull();
        assertThat(cobbleEntry.perRoundProduce()).isEqualTo(1);
        assertThat(cobbleEntry.perRoundConsume()).isEqualTo(1);
    }

    /** 普通计划:无特殊标注,但调用次数表完整(客户端显示"调用 N 次"). */
    @Test
    public void testNormalPlanYieldsCallCounts() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        env.addPattern(new ProcessingPatternBuilder(mult(stick, 4)).addPreciseInput(2, stone).build());
        env.addStoredItem(mult(stone, 64));

        var plan = env.runSpecialSimulation(mult(stick, 8), CalculationStrategy.REPORT_MISSING_ITEMS);
        var info = SpecialPlanInfo.compute(plan);
        assertThat(info.entries()).isEmpty(); // 无自增殖/循环标注
        assertThat(info.callCountOf(stick.what())).isEqualTo(2); // 8 木棍 ÷ 每次 4 个 = 2 次调用
    }

    /** 编解码往返:字节级一致. */
    @Test
    public void testCodecRoundTrip() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var cobble = item(Items.COBBLESTONE);
        var sand = item(Items.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 8));
        env.addStoredItem(sand);

        var plan = env.runSpecialSimulation(mult(stone, 8), CalculationStrategy.REPORT_MISSING_ITEMS);
        var original = SpecialPlanInfo.compute(plan);

        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        original.write(buffer);
        var decoded = SpecialPlanInfo.read(buffer);

        assertThat(decoded.entries()).isEqualTo(original.entries());
        buffer.release();
    }
}
