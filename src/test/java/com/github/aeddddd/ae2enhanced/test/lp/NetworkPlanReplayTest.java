package com.github.aeddddd.ae2enhanced.test.lp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.diag.plansnapshot.PlanSnapshot;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 网络计划快照离线回放(生产 LP 计划失败的"一次性采集 → 本地完整复现").
 * <p>快照({@code logs/ae2enhanced/plansnapshot-*.json},降级/截断时自动落盘)含
 * 全部网络样板(NBT/替代/返还)+ 全网络库存 + 根请求;本测试加载后重建等价
 * SimulationEnv 并对根请求完整跑 {@link CondensationPlanner}——生产失败的订单
 * 在本地逐字节复现,供调试修复.</p>
 * <p>用法:{code ./gradlew test --tests NetworkPlanReplayTest -Dae2e.planreplay=<文件或目录>};
 * 未指定时跳过.保真边界:容器返还依赖 dummy Item 无容器行为的差异(该测试集的
 * 成环链为处理样板,返还多为空,不影响主线复现).</p>
 */
public class NetworkPlanReplayTest {

    private static File snapshotFile;

    @BeforeAll
    public static void locate() {
        String path = System.getProperty("ae2e.planreplay");
        if (path == null) {
            // 默认目录:research/plansnapshot/(取最新)
            File dir = new File("research/plansnapshot");
            File[] files = dir.isDirectory()
                    ? dir.listFiles((d, n) -> n.startsWith("plansnapshot-") && n.endsWith(".json"))
                    : null;
            if (files != null && files.length > 0) {
                Arrays.sort(files);
                path = files[files.length - 1].getPath();
            }
        }
        if (path != null) {
            snapshotFile = new File(path);
        }
    }

    @Test
    public void replay() throws Exception {
        Assumptions.assumeTrue(snapshotFile != null && snapshotFile.isFile(),
                "无计划快照(用 -Dae2e.planreplay=<文件> 或放 research/plansnapshot/),跳过");
        System.out.println("[REPLAY] 快照: " + snapshotFile.getAbsolutePath());
        PlanSnapshot snap = PlanSnapshot.load(snapshotFile);
        // 开启 specialcrafting 诊断,让失败的 LP 模型转储到 lp-dumps/(离线定位数值缺陷)
        com.github.aeddddd.ae2enhanced.diag.DiagSwitch.setOverride(
                com.github.aeddddd.ae2enhanced.diag.DiagSwitch.SPECIAL_CRAFTING, true);
        SimulationEnv env = new SimulationEnv();
        Map<String, Item> items = new HashMap<>();

        // 库存
        for (PlanSnapshot.StackRef ref : snap.stock) {
            IAEItemStack stack = SnapshotPlanSupport.toAe(ref, items);
            if (stack != null && ref.count > 0) {
                IAEItemStack s = stack.copy();
                s.setStackSize(ref.count);
                env.addStoredItem(s);
            }
        }
        // 样板
        int built = 0;
        for (PlanSnapshot.PatternEntry entry : snap.patterns) {
            env.addPattern(new SnapshotPlanSupport.SnapshotPattern(entry, items));
            built++;
        }
        IAEItemStack root = SnapshotPlanSupport.toAe(snap.root, items);
        Assumptions.assumeTrue(root != null, "根请求无法重建,跳过");
        long target = Math.max(1, snap.root.count);
        System.out.printf("[REPLAY] 样板 %d, 库存键 %d, 根 %s×%d%n", built, snap.stock.size(),
                snap.root.id, target);

        long t0 = System.nanoTime();
        CondensationPlanner.LpPlanOutcome out = CondensationPlanner.solve(env.craftingGrid(),
                com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex
                        .of(env.craftingGrid()),
                root, target, SnapshotPlanSupport.stockOf(env));
        long wall = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("[REPLAY] 完成: 单元 %d(LP %d,降级 %d),执行记录 %d,缺料 %d 种,迭代 %d,求解 %dms%n",
                out.units, out.lpUnits, out.degradedUnits, out.executions.size(), out.deficits.size(),
                out.iterations, wall);
        System.out.println("[REPLAY] 降级原因: " + out.degradedReasons);
        System.out.println("[REPLAY] 缺料明细: " + out.deficits);
        // 诊断性回放:不硬断言——本测试的价值是离线完整复现失败现场供调试,
        // 是否修复以其他回归用例为准(巨型病态单元的 LP 数值可解性单独跟进).
        if (out.degradedUnits > 0) {
            System.out.println("[REPLAY] ⚠ 仍有降级/截断单元(未修复): " + out.degradedReasons);
        }

        // 生产同口径:完整走 LpCraftingJob(含 FlowReconciler 重演对账 +
        // 物化缺料显示)——用户在游戏内看到的缺料 = 对账后 missing,而非 LP
        // 赤字;两层必须分开核对(2026-09-15 教训:LP 层 0 缺料但对账层
        // 可以另报)
        long t1 = System.nanoTime();
        IAEItemStack jobRoot = root.copy();
        jobRoot.setStackSize(target);
        appeng.crafting.CraftingJob job = env.runLp(jobRoot);
        long wallJob = (System.nanoTime() - t1) / 1_000_000;
        com.github.aeddddd.ae2enhanced.test.support.PlanView pv =
                com.github.aeddddd.ae2enhanced.test.support.PlanView.of(job);
        System.out.printf("[REPLAY] 生产路径(对账口径): 缺料 %d 种,耗时 %dms,明细: %s%n",
                pv.missingItems().size(), wallJob, pv.missingItems());
    }

    /** 重建库存映射/物品重建/快照样板:统一由 {@link SnapshotPlanSupport} 提供. */

    /**
     * 回环自测(常跑,不依赖快照):合成环境(含 NBT 变体键 + 成环)采集 → 落盘 →
     * 读取 → 回放,断言回放结果与原环境求解一致——验证采集/回放 harness 的保真度.
     */
    @Test
    public void roundTripSelfTest() throws Exception {
        // 原环境:NBT 变体键 + 自增环 + 催化
        SimulationEnv env = new SimulationEnv();
        Map<String, Item> items = new HashMap<>();
        IAEItemStack fA = nbtItem("test:fluid_a", "{fluid:\"a\"}");
        IAEItemStack fB = nbtItem("test:fluid_b", "{fluid:\"b\"}");
        IAEItemStack metal = nbtItem("test:metal", null);
        // 自增环 1 FA → 4 FA;转换 2 FA → 1 FB;催化 1 FB + 1 metal → 1 out + 1 FB
        env.addPattern(new com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder(
                com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult(fA, 4))
                .addPreciseInput(1, fA).build());
        env.addPattern(new com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder(fB)
                .addPreciseInput(2, fA).build());
        IAEItemStack out = nbtItem("test:out", null);
        env.addPattern(new com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder(out, fB)
                .addPreciseInput(1, fB).addPreciseInput(1, metal).build());
        IAEItemStack seedA = fA.copy();
        seedA.setStackSize(3);
        env.addStoredItem(seedA);
        IAEItemStack metalStock = metal.copy();
        metalStock.setStackSize(1000000);
        env.addStoredItem(metalStock);

        // 采集 → 落盘到临时文件 → 读取
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (IAEItemStack s : env.networkStorage()) {
            if (s.getStackSize() > 0) {
                stock.merge(RecursiveCraftingHelper.canon(s), s.getStackSize(), Long::sum);
            }
        }
        com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index =
                com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex
                        .of(env.craftingGrid());
        PlanSnapshot snap = PlanSnapshot.capture(env.craftingGrid(), index, out, stock);
        File tmp = File.createTempFile("plansnapshot-roundtrip", ".json");
        snap.save(tmp);
        PlanSnapshot loaded = PlanSnapshot.load(tmp);

        // 原环境求解(参考值)
        CondensationPlanner.LpPlanOutcome direct = CondensationPlanner.solve(env.craftingGrid(),
                index, out, 16, stock);

        // 回放环境重建
        SimulationEnv replayEnv = new SimulationEnv();
        Map<String, Item> replayItems = new HashMap<>();
        for (PlanSnapshot.StackRef ref : loaded.stock) {
            IAEItemStack s = SnapshotPlanSupport.toAe(ref, replayItems);
            if (s != null && ref.count > 0) {
                s.setStackSize(ref.count);
                replayEnv.addStoredItem(s);
            }
        }
        for (PlanSnapshot.PatternEntry entry : loaded.patterns) {
            replayEnv.addPattern(new SnapshotPlanSupport.SnapshotPattern(entry, replayItems));
        }
        IAEItemStack replayRoot = SnapshotPlanSupport.toAe(loaded.root, replayItems);
        org.junit.jupiter.api.Assertions.assertNotNull(replayRoot, "根请求重建失败");
        Map<IAEItemStack, Long> replayStock = new HashMap<>();
        for (IAEItemStack s : replayEnv.networkStorage()) {
            if (s.getStackSize() > 0) {
                replayStock.merge(RecursiveCraftingHelper.canon(s), s.getStackSize(), Long::sum);
            }
        }
        CondensationPlanner.LpPlanOutcome replayed = CondensationPlanner.solve(replayEnv.craftingGrid(),
                com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex
                        .of(replayEnv.craftingGrid()),
                replayRoot, 16, replayStock);

        System.out.printf("[ROUNDTRIP] 直接: 缺料 %d 种,执行 %d; 回放: 缺料 %d 种,执行 %d%n",
                direct.deficits.size(), direct.executions.size(), replayed.deficits.size(),
                replayed.executions.size());
        org.junit.jupiter.api.Assertions.assertEquals(direct.deficits.size(), replayed.deficits.size(),
                "回放缺料种数应与直接求解一致: 直接=" + direct.deficits + " 回放=" + replayed.deficits);
        org.junit.jupiter.api.Assertions.assertEquals(direct.executions.size(), replayed.executions.size(),
                "回放执行记录数应与直接求解一致");
        org.junit.jupiter.api.Assertions.assertEquals(direct.degradedUnits, replayed.degradedUnits,
                "回放降级数应与直接求解一致");
        tmp.delete();
    }

    /** 构造带 NBT 的测试物品键(count 1). */
    private static IAEItemStack nbtItem(String id, String snbt) {
        Item item = new Item();
        item.setRegistryName(new ResourceLocation(id));
        NBTTagCompound nbt = null;
        if (snbt != null) {
            try {
                nbt = JsonToNBT.getTagFromJson(snbt);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return AEItemStack.fromItemStack(new ItemStack(item, 1, 0, nbt));
    }

}
