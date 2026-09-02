package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.diag.DiagSwitch;
import com.github.aeddddd.ae2enhanced.diag.metrics.MetricsRegistry;
import com.github.aeddddd.ae2enhanced.integration.mmce.MMCEAdditionReflect;
import com.github.aeddddd.ae2enhanced.integration.randomcomplement.RCIntelligentBlockingReflect;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICreativeEnergyAccess;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * CPU 聚合发配：把单 tick 内对同一 task 的多次发配聚合为一次大批量推送.
 *
 * <h3>聚合模式语义</h3>
 * <ul>
 *   <li><b>原版 CPU</b>：聚合上限 = 本 tick 剩余操作配额（remainingOperations）。
 *       聚合 N 份消耗 N 次配额——协处理器数仍然决定单 tick 发配量，设定语义不失效，
 *       只是 N 次逐份推送合并为一次大批量推送，省去 (N-1) 次 pushPattern 全链路开销。</li>
 *   <li><b>计算核心（本模组 CPU）</b>：完全聚合，批量仅受任务剩余数/材料/容量约束，
 *       不再依赖协处理器配额。</li>
 * </ul>
 *
 * <h3>目标资格（阻挡等语义与既有实现一致）</h3>
 * <ul>
 *   <li>MMCE 机械样板供应器（反射判定）：仅 DEFAULT / ISOLATION_INPUT 工作模式；</li>
 *   <li>mmceaddition 样板总成：Long 缓冲，直接允许；</li>
 *   <li>ME 接口/二合一接口：非阻挡允许；阻挡仅当 RandomComplement 智能阻挡开启时允许。</li>
 * </ul>
 *
 * <h3>容量安全</h3>
 * <p>接口路径由 {@code DualityInterface.pushPattern} 的 acceptsItems 保证：仅当下游能
 * 完整容纳放大后的整批材料时才接受，否则推送失败——此时本 mixin 回滚材料、
 * 按 AIMD 把该 medium 的批量上限减半（成功触顶时翻倍回升），并交由
 * {@code MixinCraftingCPUClusterPushCooldown} 冷却 5 tick。MMCE 仓室为 int/Long 缓冲，
 * 不受常规容量限制。ae2fc packet 路径（流体/气体数量在 NBT）逐槽做 int 溢出核算。</p>
 *
 * <h3>能量</h3>
 * <p>原生每次发配预扣 1× 输入总能量（本包装点之前已扣）。聚合时按 (N-1)× 补扣；
 * 供能不足时缩减批量；网络存在创造能源元件时全部跳过（见
 * {@code MixinCraftingCPUClusterCreativeEnergy}）。推送失败不退还补扣能量，
 * 与原生"扣费后构建失败不退款"一致。</p>
 *
 * <h3>记账</h3>
 * <p>成功后按原生逐份口径补记 (N-1) 份：taskProgress / remainingItemCount /
 * waitingFor / postChange / postCraftingStatusChange，与 VirtualBatch 契约一致。</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterAggregate {

    /** 诊断开关：{@code /ae2e debug batchaggregate on} 时输出聚合判定细节（5s 节流）. */
    @Unique
    private static final Map<String, Long> AE2E_DEBUG_THROTTLE = new java.util.HashMap<>();

    /** 二分定位开关：-Dae2e.disableAggregate=true 时聚合发配完全禁用（原生逐份）. */
    @Unique
    private static final boolean AE2E_DISABLE_AGGREGATE = Boolean.getBoolean("ae2e.disableAggregate");

    /** medium 批量容量上限（AIMD：失败减半，成功触顶翻倍；缺省无上限）. */
    @Unique
    private final IdentityHashMap<ICraftingMedium, Long> ae2e$batchCeilings = new IdentityHashMap<>();

    @Unique
    private static void ae2e$debug(ICraftingMedium medium, String reason) {
        if (!DiagSwitch.isEnabled(DiagSwitch.BATCH_AGGREGATE)) {
            return;
        }
        String key = (medium == null ? "null" : medium.getClass().getName()) + "|" + reason;
        long now = System.currentTimeMillis();
        synchronized (AE2E_DEBUG_THROTTLE) {
            Long last = AE2E_DEBUG_THROTTLE.get(key);
            if (last != null && now - last < 5000) {
                return;
            }
            AE2E_DEBUG_THROTTLE.put(key, now);
        }
        AE2Enhanced.LOGGER.info("[AE2E] batch aggregate debug: medium={} reason={}",
                medium == null ? "null" : medium.getClass().getName(), reason);
    }

    @Shadow
    private Map<ICraftingPatternDetails, Object> tasks;

    @Shadow
    private long remainingItemCount;

    @Shadow
    private int remainingOperations;

    @Shadow
    private IItemList<IAEItemStack> waitingFor;

    @Shadow
    private MachineSource machineSrc;

    @Shadow
    public abstract IMEInventory<IAEItemStack> getInventory();

    @Shadow
    private void postChange(IAEItemStack diff, appeng.api.networking.security.IActionSource src) {
    }

    @Shadow
    private void postCraftingStatusChange(IAEItemStack diff) {
    }

    /**
     * L3 细分耗时插桩：executeCrafting 整体耗时（采样 1/20，/ae2e debug perf off 关闭）.
     * 指标名 ae2.craftingcpu.executeCrafting.
     */
    @Unique
    private long ae2e$ecStartNanos = -1L;

    @Inject(method = "executeCrafting", at = @At("HEAD"), require = 0)
    private void ae2e$ecHead(IEnergyGrid eg, appeng.me.cache.CraftingGridCache cc, CallbackInfo ci) {
        if (DiagSwitch.isEnabled(DiagSwitch.PERF)) {
            com.github.aeddddd.ae2enhanced.diag.metrics.Timer timer =
                    MetricsRegistry.timer("ae2.craftingcpu.executeCrafting");
            ae2e$ecStartNanos = timer.shouldSample() ? System.nanoTime() : -1L;
        } else {
            ae2e$ecStartNanos = -1L;
        }
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), require = 0)
    private void ae2e$ecReturn(IEnergyGrid eg, appeng.me.cache.CraftingGridCache cc, CallbackInfo ci) {
        if (ae2e$ecStartNanos >= 0L) {
            MetricsRegistry.timer("ae2.craftingcpu.executeCrafting")
                    .record(System.nanoTime() - ae2e$ecStartNanos);
            ae2e$ecStartNanos = -1L;
        }
    }

    /**
     * 包装 CPU executeCrafting 中对 ICraftingMedium.pushPattern 的调用：
     * 命中可聚合目标时把本 tick 的多次发配聚合为一次大批量推送；
     * 未命中/聚合为 1 时完全走原生路径.
     */
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2e$aggregateDispatch(ICraftingMedium medium, ICraftingPatternDetails details,
            InventoryCrafting table, Operation<Boolean> original, @Local IEnergyGrid eg) {
        // 特殊合成（自消耗/循环链）集群：轮配额调度依赖逐次推送节奏，聚合会破坏其语义，跳过
        if (SpecialCraftingRuntime.isSpecialCluster((CraftingCPUCluster) (Object) this)) {
            return original.call(medium, details, table);
        }
        long batch = 1;
        boolean coreCluster = ((IComputationCoreAccess) this).ae2enhanced$getComputationCore() != null;
        if (!details.isCraftable()) {
            try {
                if (ae2e$isBatchTarget(medium)) {
                    batch = ae2e$computeBatch(details, table, medium);
                }
            } catch (Throwable t) {
                AE2Enhanced.LOGGER.error("[AE2E] batch aggregate check failed: {}", t.toString(), t);
                batch = 1;
            }
        }
        if (batch > 1) {
            // 原版 CPU：聚合上限 = 本 tick 剩余配额（协处理器语义保持）；计算核心：不限制
            if (!coreCluster) {
                batch = Math.min(batch, Math.max(1L, this.remainingOperations));
            }
            // AIMD 容量上限（此前失败收敛得出）
            Long ceiling = this.ae2e$batchCeilings.get(medium);
            if (ceiling != null) {
                batch = Math.min(batch, ceiling);
            }
        }

        // 能量：批量需对额外份数补扣 (batch-1)×；供能不足时缩减批量；
        // 网络存在创造能源元件时全部跳过
        double perSet = 0;
        boolean creativeEnergy = eg != null && ((ICreativeEnergyAccess) this).ae2e$hasCreativeEnergy(eg);
        if (batch > 1 && !creativeEnergy) {
            for (IAEItemStack in : details.getInputs()) {
                if (in != null) {
                    perSet += (double) in.getStackSize();
                }
            }
            if (perSet > 0 && eg != null) {
                double affordable = eg.extractAEPower(perSet * (batch - 1), Actionable.SIMULATE,
                        PowerMultiplier.CONFIG);
                long affordableBatch = 1 + (long) (affordable / perSet);
                if (affordableBatch < batch) {
                    batch = affordableBatch;
                }
            }
        }
        if (batch <= 1) {
            return original.call(medium, details, table);
        }

        IMEInventory<IAEItemStack> inv = this.getInventory();
        // 提取额外 (batch-1) 份材料（下单时已预提整单到 CPU 库存，正常必然满足）。
        // 必须按凝缩输入（同种物品跨槽聚合）提取：逐槽独立提取时，同一物品占多个
        // 槽位的配方（如 9×锭→块）会重复扣取，与 computeBatch 的聚合探测口径不一致。
        List<IAEItemStack> extras = new ArrayList<>();
        boolean extractedAll = true;
        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack need = input.copy();
            need.setStackSize(input.getStackSize() * (batch - 1));
            IAEItemStack got = inv.extractItems(need, Actionable.MODULATE, this.machineSrc);
            long gotSize = got == null ? 0 : got.getStackSize();
            if (gotSize > 0) {
                extras.add(got);
                this.postChange(got.copy(), this.machineSrc);
            }
            if (gotSize < need.getStackSize()) {
                extractedAll = false;
                break;
            }
        }
        if (!extractedAll) {
            // 材料不足（不应发生，SIMULATE 已核算）：回滚并退化为原生逐份
            for (IAEItemStack e : extras) {
                inv.injectItems(e, Actionable.MODULATE, this.machineSrc);
            }
            ae2e$debug(medium, "extras-extraction-shortfall");
            return original.call(medium, details, table);
        }

        // 补扣 (batch-1)× 能量（材料已就位；若推送失败不退还，与原生一致）
        if (!creativeEnergy && perSet > 0 && eg != null) {
            eg.extractAEPower(perSet * (batch - 1), Actionable.MODULATE, PowerMultiplier.CONFIG);
        }

        boolean result = original.call(medium, details, ae2e$scaleTable(table, batch));
        if (!result) {
            // 推送失败（下游容量不足等）：回滚材料 + AIMD 减半该 medium 批量上限。
            // 5 tick 重试冷却由 MixinCraftingCPUClusterPushCooldown 承担。
            this.ae2e$batchCeilings.put(medium, Math.max(1L, batch / 2));
            for (IAEItemStack e : extras) {
                inv.injectItems(e, Actionable.MODULATE, this.machineSrc);
            }
            ae2e$debug(medium, "push-failed batch=" + batch);
            return false;
        }

        // 成功：原版 CPU 消耗 (batch-1) 次额外配额（原生随后自减 1，合计 batch 次）；
        // 计算核心不消耗配额
        if (!coreCluster) {
            this.remainingOperations -= (int) Math.min(batch - 1, this.remainingOperations);
        }
        // AIMD：批量触顶时翻倍回升，跟踪下游容量变化
        Long ceiling = this.ae2e$batchCeilings.get(medium);
        if (ceiling != null && batch >= ceiling && ceiling < (1L << 60)) {
            this.ae2e$batchCeilings.put(medium, Math.min(ceiling * 2, 1L << 60));
        }
        ae2e$correctCounts(details, batch - 1);
        return true;
    }

    /**
     * 判定 medium 是否为可聚合发配的目标（全部反射/接口判定，任何环境安全）.
     */
    @Unique
    private boolean ae2e$isBatchTarget(ICraftingMedium medium) {
        // MMCE 机械样板供应器：仅 DEFAULT / ISOLATION_INPUT 工作模式（反射判定）
        if (MMCEAdditionReflect.isBatchablePatternProvider(medium)) {
            return true;
        }
        // mmceaddition 样板总成：Long 缓冲，直接允许（反射判定）
        if (MMCEAdditionReflect.isPatternAssembly(medium)) {
            return true;
        }
        // ME 接口/二合一接口：非阻挡允许；阻挡仅 RandomComplement 智能阻挡开启时允许。
        // 注意：原生接口经 provideCrafting 以 DualityInterface 本体注册为 medium
        // （DualityInterface 不实现 IInterfaceHost），必须直接判定 DualityInterface；
        // IInterfaceHost 分支仅兜底第三方以 host 本体注册 medium 的实现。
        // 容量安全由 pushPattern 的 acceptsItems 保证（不完整容纳则失败 → 回滚 + AIMD 收敛）
        DualityInterface duality;
        if (medium instanceof DualityInterface) {
            duality = (DualityInterface) medium;
        } else if (medium instanceof IInterfaceHost) {
            duality = ((IInterfaceHost) medium).getInterfaceDuality();
        } else {
            return false;
        }
        if (duality == null) {
            ae2e$debug(medium, "duality-null");
            return false;
        }
        boolean allowed = duality.getConfigManager().getSetting(Settings.BLOCK) != YesNo.YES
                || RCIntelligentBlockingReflect.isIntelligentBlockingOpen(duality);
        if (!allowed) {
            ae2e$debug(medium, "blocking-on-without-intelligent-blocking");
        }
        return allowed;
    }

    /**
     * 计算批量大小：min(任务剩余数, int 安全上限, CPU 库存材料可用量).
     */
    @Unique
    private long ae2e$computeBatch(ICraftingPatternDetails details, InventoryCrafting table, ICraftingMedium medium) {
        Object progress = this.tasks.get(details);
        if (progress == null) {
            ae2e$debug(medium, "no-task-progress");
            return 1;
        }
        long remaining = ((ITaskProgressAccessor) progress).ae2e$getValue();
        if (remaining <= 1) {
            return 1;
        }
        long n = remaining;
        // int 安全：每槽有效数量 × n 不得溢出 int。
        // 注意 ae2fc 会把 CPU 的 InventoryCrafting 包装成 FluidConvertingInventoryCrafting，
        // 流体/气体以 packet 形式存放（count=1，真实数量在 NBT 的 FluidStack.Amount /
        // GasStack.amount 中），必须按 NBT 数量做上限核算。
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack s = table.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            long perSlot = ae2e$getEffectiveSlotAmount(s);
            if (perSlot > 0) {
                n = Math.min(n, Integer.MAX_VALUE / perSlot);
            }
        }
        if (n <= 1) {
            return 1;
        }
        IMEInventory<IAEItemStack> inv = this.getInventory();
        // 按凝缩输入聚合探测：同一物品占多个输入槽时，逐槽独立 SIMULATE 会各自
        // 通过（模拟不消耗库存），把批量高估为"库存/单槽用量"而非"库存/总用量"，
        // 导致实际提取不足→回滚→退化为 1× 逐份推送的每 tick 空转循环。
        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack probe = input.copy();
            probe.setStackSize(input.getStackSize() * n);
            IAEItemStack avail = inv.extractItems(probe, Actionable.SIMULATE, this.machineSrc);
            long availSize = avail == null ? 0 : avail.getStackSize();
            n = Math.min(n, availSize / input.getStackSize());
            if (n <= 1) {
                ae2e$debug(medium, "materials-insufficient per=" + input.getStackSize() + " avail=" + availSize);
                return 1;
            }
        }
        return n;
    }

    /**
     * 槽位的有效数量：ae2fc 流体/气体 packet 取 NBT 中的真实数量，其余取 count.
     */
    @Unique
    private static long ae2e$getEffectiveSlotAmount(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            if (tag.hasKey("FluidStack", 10)) {
                return tag.getCompoundTag("FluidStack").getInteger("Amount");
            }
            if (tag.hasKey("GasStack", 10)) {
                return tag.getCompoundTag("GasStack").getInteger("amount");
            }
        }
        return stack.getCount();
    }

    /**
     * 构造放大 table（原 table 保持 1 份不变，供 pushPattern 失败路径由原生代码原样回灌）.
     * 普通物品 count × batch；ae2fc 流体/气体 packet 放大 NBT 中的真实数量.
     */
    @Unique
    private static InventoryCrafting ae2e$scaleTable(InventoryCrafting table, long batch) {
        int size = table.getSizeInventory();
        int dim = (int) Math.ceil(Math.sqrt(size));
        if (dim < 3) {
            dim = 3;
        }
        if (dim > 10) {
            dim = 10;
        }
        InventoryCrafting scaled = new InventoryCrafting(new ContainerNull(), dim, dim);
        for (int i = 0; i < size && i < scaled.getSizeInventory(); i++) {
            ItemStack s = table.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack c = s.copy();
            if (!ae2e$scalePacketAmount(c, batch)) {
                c.setCount((int) (s.getCount() * batch));
            }
            scaled.setInventorySlotContents(i, c);
        }
        return scaled;
    }

    /**
     * 放大 ae2fc 流体/气体 packet 的 NBT 数量.返回 true 表示该栈是 packet（已按 NBT 缩放，
     * 不应再缩放 count）。上限已由 computeBatch 的逐槽核算保证不溢出 int，此处 clamp 仅兜底。
     */
    @Unique
    private static boolean ae2e$scalePacketAmount(ItemStack stack, long batch) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return false;
        }
        if (tag.hasKey("FluidStack", 10)) {
            NBTTagCompound fs = tag.getCompoundTag("FluidStack");
            long amount = fs.getInteger("Amount") * batch;
            fs.setInteger("Amount", (int) Math.min(amount, Integer.MAX_VALUE));
            return true;
        }
        if (tag.hasKey("GasStack", 10)) {
            NBTTagCompound gs = tag.getCompoundTag("GasStack");
            long amount = gs.getInteger("amount") * batch;
            gs.setInteger("amount", (int) Math.min(amount, Integer.MAX_VALUE));
            return true;
        }
        return false;
    }

    /**
     * 按原生逐份记账口径补记 extra 份产出预期，并扣除任务剩余数.
     *
     * <p>注意：不得触碰 remainingItemCount——原生发配时不递减它，其递减发生在产物
     * 回流（injectItems）时；物理发配的 extra 份产物同样会回流并触发递减，
     * 发配侧递减会导致双重计数（进度计数漂移为负）。</p>
     */
    @Unique
    private void ae2e$correctCounts(ICraftingPatternDetails details, long extra) {
        Object progress = this.tasks.get(details);
        if (progress != null) {
            long remaining = ((ITaskProgressAccessor) progress).ae2e$getValue();
            ((ITaskProgressAccessor) progress).ae2e$setValue(Math.max(0, remaining - extra));
        }
        IItemList<IAEItemStack> waitingFor = this.waitingFor;
        if (waitingFor == null) {
            return;
        }
        for (IAEItemStack out : details.getCondensedOutputs()) {
            if (out == null || out.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack extraOut = out.copy();
            extraOut.setStackSize(out.getStackSize() * extra);
            this.postChange(extraOut.copy(), this.machineSrc);
            waitingFor.add(extraOut);
            this.postCraftingStatusChange(extraOut.copy());
        }
    }
}
