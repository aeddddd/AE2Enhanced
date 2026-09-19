package com.github.aeddddd.ae2enhanced.test.execution;

import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.block;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.item;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;

import com.github.aeddddd.ae2enhanced.network.packet.PacketSpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanDisplayHook;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 特殊计划显示信息(SpecialPlanInfo)计算与编解码测试,1.12.2 移植版.
 * <p>1.12.2 差异:显示信息由 {@link SpecialPlanInfo#compute} 从合成树自恢复,普通计划的调用次数表由
 * {@link SpecialPlanDisplayHook#computeCallCounts} 从合成树恢复;编解码走
 * {@link PacketSpecialPlanInfo} 的 ByteBuf 序列化.</p>
 */
public class SpecialPlanInfoTest {

    /** 自增殖计划:kind=SELF_DUP,每次 1→2,总次数与种子正确.
     * 全额生产语义:根库存不抵交付,净需 10 → 10 次调用(种子 1 点火,期末返还). */
    @Test
    public void testSelfDupInfo() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());
        env.addStoredItem(stone);

        SpecialPlanInfo info = SpecialPlanInfo.compute(env.runLp(mult(stone, 10)));

        SpecialPlanInfo.Entry entry = info.entryFor(RecursiveCraftingHelper.canon(stone));
        assertThat(entry).isNotNull();
        assertThat(entry.kind).isEqualTo(SpecialPlanInfo.KIND_SELF_DUP);
        assertThat(entry.perRoundConsume).isEqualTo(1);
        assertThat(entry.perRoundProduce).isEqualTo(2);
        assertThat(entry.totalCrafts).isEqualTo(10);
        assertThat(entry.initialExtract).isEqualTo(1);
        assertThat(info.callCountOf(RecursiveCraftingHelper.canon(stone))).isEqualTo(10);
    }

    /** θ 循环计划:kind=CYCLE,轮次=GCD 恢复,每轮消耗/产出精确.
     * 全额生产语义:净需 8 → 4 轮(种子 2 点火,期末返还). */
    @Test
    public void testCycleInfo() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2)); // 种子 2(LP 根库存语义下计入交付)
        env.addStoredItem(sand);

        SpecialPlanInfo info = SpecialPlanInfo.compute(env.runLp(mult(stone, 8)));

        SpecialPlanInfo.Entry stoneEntry = info.entryFor(RecursiveCraftingHelper.canon(stone));
        assertThat(stoneEntry).isNotNull();
        assertThat(stoneEntry.kind).isEqualTo(SpecialPlanInfo.KIND_CYCLE);
        assertThat(stoneEntry.rounds).isEqualTo(4); // 净产 2/轮,全额生产 8 → 4 轮
        assertThat(stoneEntry.perRoundConsume).isEqualTo(2); // crush 1 + charge 1
        assertThat(stoneEntry.perRoundProduce).isEqualTo(4); // back ×1
        assertThat(stoneEntry.initialExtract).isEqualTo(2);

        SpecialPlanInfo.Entry cobbleEntry = info.entryFor(RecursiveCraftingHelper.canon(cobble));
        assertThat(cobbleEntry).isNotNull();
        assertThat(cobbleEntry.perRoundProduce).isEqualTo(1);
        assertThat(cobbleEntry.perRoundConsume).isEqualTo(1);
    }

    /** 普通计划:无特殊标注,但调用次数表完整(客户端显示"调用 N 次"). */
    @Test
    public void testNormalPlanYieldsCallCounts() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack stick = item(Items.STICK);
        env.addPattern(new ProcessingPatternBuilder(mult(stick, 4)).addPreciseInput(2, stone).build());
        env.addStoredItem(mult(stone, 64));

        CraftingJob job = env.runNative(mult(stick, 8));
        Map<IAEItemStack, Long> callCounts = SpecialPlanDisplayHook.computeCallCounts(job);
        assertThat(callCounts).hasSize(1);
        assertThat(callCounts.get(RecursiveCraftingHelper.canon(stick)))
                .as("8 木棍 ÷ 每次 4 个 = 2 次调用")
                .isEqualTo(2);
    }

    /** 编解码往返:字段级一致(canon 键经 getDefinition 序列化不丢失). */
    @Test
    public void testCodecRoundTrip() {
        SimulationEnv env = new SimulationEnv();
        IAEItemStack stone = block(Blocks.STONE);
        IAEItemStack cobble = block(Blocks.COBBLESTONE);
        IAEItemStack sand = block(Blocks.SAND);
        env.addPattern(new ProcessingPatternBuilder(cobble).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(sand).addPreciseInput(1, stone).build());
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 4))
                .addPreciseInput(1, cobble)
                .addPreciseInput(1, sand)
                .build());
        env.addStoredItem(mult(stone, 2)); // 种子 2(点火种子,不抵交付,期末返还)
        env.addStoredItem(sand);

        CraftingJob job = env.runLp(mult(stone, 8));
        SpecialPlanInfo original = SpecialPlanInfo.compute(job);
        assertThat(original.isEmpty()).isFalse();

        PacketSpecialPlanInfo packet = new PacketSpecialPlanInfo(job.getOutput(), original);
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.toBytes(buffer);
            PacketSpecialPlanInfo decoded = new PacketSpecialPlanInfo();
            decoded.fromBytes(buffer);

            SpecialPlanInfo decodedInfo = infoOf(decoded);
            assertThat(decodedInfo.entries).hasSameSizeAs(original.entries);
            for (Map.Entry<IAEItemStack, SpecialPlanInfo.Entry> e : original.entries.entrySet()) {
                SpecialPlanInfo.Entry actual = decodedInfo.entryFor(e.getKey());
                assertThat(actual).as("解码后缺少条目 %s", e.getKey()).isNotNull();
                assertThat(actual.kind).isEqualTo(e.getValue().kind);
                assertThat(actual.rounds).isEqualTo(e.getValue().rounds);
                assertThat(actual.perRoundProduce).isEqualTo(e.getValue().perRoundProduce);
                assertThat(actual.perRoundConsume).isEqualTo(e.getValue().perRoundConsume);
                assertThat(actual.totalCrafts).isEqualTo(e.getValue().totalCrafts);
                assertThat(actual.initialExtract).isEqualTo(e.getValue().initialExtract);
            }
            assertThat(decodedInfo.callCounts).hasSameSizeAs(original.callCounts);
            for (Map.Entry<IAEItemStack, Long> e : original.callCounts.entrySet()) {
                assertThat(decodedInfo.callCountOf(e.getKey())).isEqualTo(e.getValue());
            }
        } finally {
            buffer.release();
        }
    }

    /** 反射读取包的私有 info 字段(包未提供访问器,测试只关心编解码保真). */
    private static SpecialPlanInfo infoOf(PacketSpecialPlanInfo packet) {
        try {
            java.lang.reflect.Field f = PacketSpecialPlanInfo.class.getDeclaredField("info");
            f.setAccessible(true);
            return (SpecialPlanInfo) f.get(packet);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
