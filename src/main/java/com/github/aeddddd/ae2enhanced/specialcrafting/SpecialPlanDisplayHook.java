package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSpecialPlanInfo;

/**
 * 普通计划的调用次数显示钩子（与 1.20.1 的 SpecialPlanInfo 全计划覆盖对齐）.
 * <p>由 MixinCraftingJob 在原生 {@code CraftingJob.run()} 返回时调用:
 * 遍历合成树统计各主产出键的样板调用次数,向发起玩家发送显示包.
 * 特殊计划的完整信息由 {@link LpCraftingJob} 自行发送（含结构标注）.</p>
 */
public final class SpecialPlanDisplayHook {

    private SpecialPlanDisplayHook() {
    }

    /**
     * 计算完成后发送显示信息（仅玩家发起的请求）.
     * <p>统一经 {@link SpecialPlanInfo#compute} 从树自恢复:特殊/DAG 计划含结构标注,
     * 普通计划含调用次数;空信息也发送以清空客户端上一次计划的缓存.</p>
     */
    public static void sendPlanInfo(CraftingJob job) {
        try {
            IActionSource src = Ae2CraftingReflect.getActionSrc(job);
            if (src == null || !src.player().isPresent()) {
                return;
            }
            EntityPlayer player = src.player().get();
            if (!(player instanceof EntityPlayerMP)) {
                return;
            }
            SpecialPlanInfo info = SpecialPlanInfo.compute(job);
            AE2Enhanced.network.sendTo(new PacketSpecialPlanInfo(job.getOutput(), info), (EntityPlayerMP) player);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.debug("[特殊配方] 计划显示钩子异常: {}", t.toString());
        }
    }

    /**
     * 原生 job 计算完成后发送调用次数表（仅玩家发起的请求）.
     *
     * @deprecated 由 {@link #sendPlanInfo} 取代(统一全计划覆盖 + 缓存清空).
     */
    @Deprecated
    public static void sendCallCounts(CraftingJob job) {
        sendPlanInfo(job);
    }

    /**
     * 从 job 的合成树统计各主产出键的样板调用次数（普通计划显示用,测试可直接断言）.
     */
    public static Map<IAEItemStack, Long> computeCallCounts(CraftingJob job) {
        Map<IAEItemStack, Long> callCounts = new LinkedHashMap<>();
        collectCallCounts(job.getTree(), callCounts);
        return callCounts;
    }

    private static void collectCallCounts(CraftingTreeNode node, Map<IAEItemStack, Long> callCounts) {
        if (node == null) {
            return;
        }
        for (CraftingTreeProcess pro : Ae2CraftingReflect.getNodeProcesses(node)) {
            long crafts = Ae2CraftingReflect.getProcessCrafts(pro);
            if (crafts > 0) {
                ICraftingPatternDetails details = Ae2CraftingReflect.getProcessDetails(pro);
                IAEItemStack primary = details.getPrimaryOutput();
                if (primary != null) {
                    IAEItemStack key = RecursiveCraftingHelper.canon(primary);
                    callCounts.merge(key, crafts, Long::sum);
                }
            }
            for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
                collectCallCounts(child, callCounts);
            }
        }
    }
}
