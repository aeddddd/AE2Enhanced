package com.github.aeddddd.ae2enhanced.test.bench;

import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 下单路径服务器线程同步片段的量化诊断(纯观测,零断言).
 * <p>不纳入默认测试运行:Gradle test 任务排除 {@code test/bench/**},
 * 手动运行:{@code ./gradlew test -Pbench}(建议叠加 {@code AE2E_BENCH=1}
 * 环境变量以获得 3g 堆与基准输出直通).</p>
 * <p>背景:生产案例——1k 份 × 单份约 5w 节点的下单在看门狗 180s 崩服,且崩在
 * "计算中"阶段.计算本身在线程池线程(实测 5w 节点 ×1000 ≈ 0.5s),故瓶颈必在
 * 服务器线程的同步片段.本基准逐一量化这些片段在大规模下的耗时:</p>
 * <ul>
 * <li>① 样板索引冷构建(beginCraftingJob → 首次访问全量构建);</li>
 * <li>② job 构造器的网络库存快照拷贝(O(全网物品种类),CraftingJob 构造器固有);</li>
 * </ul>
 */
public class ServerThreadScaleDiagnosticTest {

    private static IAEItemStack key(int id) {
        return AEItemStack.fromItemStack(new ItemStack(Items.STICK, 1, id));
    }

    @Test
    public void quantifyServerThreadSegments() {
        // ① 大库存快照拷贝:模拟巨型网络(20 万种物品),对应 CraftingJob 构造器的
        // new MECraftingInventory(getStorageList()) 逐条 add 成本(服务器线程,原生固有)
        SimulationEnv storageEnv = new SimulationEnv();
        int typeCount = 200_000;
        for (int i = 0; i < typeCount; i++) {
            IAEItemStack s = key(i);
            if (s == null) {
                System.out.printf("[BENCH] 诊断: key(%d) 为 null(stack empty?)%n", i);
                break;
            }
            s.setStackSize(64);
            storageEnv.addStoredItem(s);
        }
        IItemList<IAEItemStack> liveList = storageEnv.networkStorage();
        long t0 = System.nanoTime();
        IItemList<IAEItemStack> snapshot = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        for (IAEItemStack is : liveList) {
            snapshot.add(is.copy());
        }
        long snapshotNanos = System.nanoTime() - t0;
        System.out.printf("[BENCH] ① 库存快照拷贝(%,d 种): %,.1f ms%n", typeCount, snapshotNanos / 1e6);

        // ② 大样板网络(5w 样板)的索引冷构建(beginCraftingJob 同步段)
        SimulationEnv env = new SimulationEnv();
        java.util.Random rng = new java.util.Random(7);
        int patterns = 50_000;
        int width = 500;
        for (int i = 0; i < width; i++) {
            IAEItemStack raw = key(i);
            raw.setStackSize(10_000_000_000L);
            env.addStoredItem(raw);
        }
        for (int id = width; id < patterns; id++) {
            env.addPattern(new com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder(
                    key(id))
                    .addPreciseInput(1, key(rng.nextInt(id)))
                    .addPreciseInput(1, key(rng.nextInt(id)))
                    .build());
        }
        t0 = System.nanoTime();
        NetworkPatternIndex index = NetworkPatternIndex.build(env.craftingGrid());
        long buildNanos = System.nanoTime() - t0;
        System.out.printf("[BENCH] ② 样板索引冷构建(%,d 样板): %,.1f ms%n", patterns, buildNanos / 1e6);
    }
}
