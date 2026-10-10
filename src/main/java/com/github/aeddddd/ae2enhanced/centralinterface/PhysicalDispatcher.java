package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物理发配调度器.
 *
 * <p>负责把单个样板的材料实际推送到目标机器，并在失败时统一回退材料；流体不被抽回，
 * 只按"送不走就留在机器里"记录（见 {@link #logPushedFluids}）。
 * 成功后目标进入 {@link TargetSession} 的 PROCESSING 状态，由
 * {@link DualityCentralInterface#tickingRequest} 继续 tick 收集产物。</p>
 *
 * <p>核心设计原则：</p>
 * <ul>
 *   <li><b>异常隔离</b>：所有 handler 调用都包 try/catch，单个 handler 异常不会扩散到整个 tile。</li>
 *   <li><b>推料原子性</b>：{@code pushMaterials} 返回 true 时必须保证全部材料已进入目标；否则由
 *       {@link #revertSession} 统一回退。</li>
 *   <li><b>流体先确认送得走再动手抽</b>：输出罐回收前先模拟注入 ME 流体通道，收不下就整批留在
 *       机器里（见 {@link #outputFluidIsSendable}）；已经抽出来的流体网络不收时只有两处可去——
 *       推回目标（撤销误抽）或接口流体缓冲，任何路径都不把流体转成物品、也不丢弃
 *       （见 {@link #retainFluids}）。</li>
 *   <li><b>物理并行隔离</b>：每个 session 的失败只回退自己的目标、流体和输出清理，不影响其他 session。</li>
 *   <li><b>条件启动节流</b>：对未 idle 的目标不会每 tick 无脑调用 {@code startProcess}，
 *       而是按冷却和最大尝试次数限制。</li>
 * </ul>
 */
public class PhysicalDispatcher {

    /**
     * 在 tick 阶段对同一 PROCESSING session 调用 startProcess 的最小间隔（tick）。
     * 避免条件启动型机器被每 tick 反复扫描。
     */
    private static final int START_PROCESS_COOLDOWN_TICKS = 4;

    /**
     * 在 tick 阶段对同一 PROCESSING session 调用 startProcess 的最大次数。
     * 超过此次数仍无法启动则视为超时，走统一回退。
     */
    private static final int MAX_START_PROCESS_ATTEMPTS = 40;

    private final DualityCentralInterface owner;

    public PhysicalDispatcher(DualityCentralInterface owner) {
        this.owner = owner;
    }

    /**
     * 尝试对指定目标执行一次物理发配。
     *
     * @return true 表示推送成功
     */
    public boolean dispatch(IGridConnection grid,
                            ICraftingPatternDetails patternDetails,
                            InventoryCrafting originalTable,
                            TargetBinding target,
                            IRemoteHandler handler) {
        World world = owner.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            return false;
        }

        TargetSession session = owner.getOrCreateSession(target);

        // 每个 target 同一时刻只能有一份材料在途
        if (!session.isIdle()) {
            return false;
        }

        // handler 反射可能因目标 mod 类结构变化而抛异常/Error,必须隔离,
        // 否则异常会逃逸到 AE2 CPU tick 导致服务端崩溃
        boolean validTarget;
        try {
            validTarget = handler.isValidTarget(world, target.pos);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] isValidTarget threw for {} at {}: {}",
                    target.blockId, target.pos, t.toString());
            validTarget = false;
        }
        if (!validTarget) {
            session.setUnavailable();
            return false;
        }

        // 复制 table，避免一个 target 的失败影响其他 target
        InventoryCrafting table = owner.copyInventoryCrafting(originalTable);
        IActionSource source = NetworkAccess.machineSource(owner.host);

        // 获取全局坐标所有权，进入 PUSHING 状态
        List<FluidStack> pushedFluids = new ArrayList<>();
        if (!session.beginPush(pushedFluids)) {
            return false;
        }

        boolean success = false;
        try {
            // 先登记本批预期产物：clearOutputs 依赖 session 中的预期产物识别上一轮残留，
            // 否则通用处理器在清理阶段只能看到空快照，残留产物会一直留在目标中。
            session.preCommitExpectedOutputs(patternDetails.getOutputs());

            // 1. 发配前回收目标输出槽残留内容，防止残留产物干扰新材料推送
            List<ItemStack> clearedOutputs = safeClearOutputs(handler, world, target.pos, source, session);
            if (!clearedOutputs.isEmpty()) {
                stashOrInject(grid, world, clearedOutputs);
            }

            // 1b. 回收目标输出罐的残留流体：残留会占满输出罐，使机器拒绝开始新配方。
            // 次序是先确认送得走、再动手抽：ME 流体通道收不下的整批留在机器里、跳过清理。
            // 对"产物就是流体、槽位为空"的机器（如 TE 熔岩炉），抽走的正是上一批成品，
            // 而它的输出罐 fill() 恒返回 0 —— 抽出来就再也回不到机器里。
            List<FluidStack> batchFluids = FluidTransferHelper.peekFluidInputs(table);
            if (outputFluidIsSendable(grid, world, target.pos, batchFluids)) {
                List<FluidStack> clearedFluids = safeClearOutputFluids(handler, world, target.pos, source, session, batchFluids);
                if (!clearedFluids.isEmpty()) {
                    injectFluidsOrKeep(grid, world, target.pos, clearedFluids);
                }
            }

            // 2. 推送流体输入（如果配方包含流体）
            if (!FluidTransferHelper.pushFluidInputs(world, target.pos, table, pushedFluids)) {
                revertSession(session, "pushFluidInputs failed", source);
                return false;
            }

            // 记录本批材料快照：发配在 commitPush 之前失败时，PUSHING 阶段的
            // revertMaterials 只能依靠这份快照识别并取回已推入目标的材料。
            session.recordPushedBatch(snapshotItemInputs(table), pushedFluids);

            // 3. 检查目标是否可以开始处理本次配方
            if (!safeCanStart(handler, world, target.pos, table, session)) {
                revertSession(session, "canStart failed", source);
                return false;
            }

            // 4. 推送物品材料。handler 必须保证原子性：全部进入目标或全部回退。
            if (!safePushMaterials(handler, world, target.pos, table, source, session)) {
                revertSession(session, "pushMaterials failed", source);
                return false;
            }

            // 5. 启动机器处理流程
            if (!safeStartProcess(handler, world, target.pos, source, session)) {
                revertSession(session, "startProcess failed", source);
                return false;
            }

            // 6. 保存输入材料快照并提交会话
            IAEItemStack[] outputs = patternDetails.getOutputs();
            List<ItemStack> inputSnapshot = snapshotItemInputs(table);
            session.commitPush(outputs, inputSnapshot, pushedFluids, world.getTotalWorldTime());
            success = true;
            owner.tryWakeTickDevice();
            return true;
        } catch (Throwable e) {
            // 必须捕获 Throwable：第三方 handler 的 NoClassDefFoundError/NoSuchMethodError
            // 等 Error 逃逸至 AE2 CPU tick 会导致服务端崩溃
            AE2Enhanced.LOGGER.warn("[AE2E] Physical dispatch exception for {} at {}: {}",
                    target.blockId, target.pos, e.toString());
            revertSession(session, "dispatch exception: " + e.toString(), source);
            return false;
        } finally {
            if (!success) {
                // 任何非提交路径都应已调用 revertSession；这里作为兜底再次释放所有权
                session.reset();
            }
        }
    }

    /**
     * 处理所有处于 PROCESSING / COLLECTING 状态的物理 session。
     *
     * @return 是否在本 tick 中实际做了收集/推进工作
     */
    public boolean tick(IGridConnection grid, World world, int timeoutTicks) {
        boolean didWork = false;

        // 阶段 1：PROCESSING -> COLLECTING
        for (TargetSession session : new ArrayList<>(owner.sessions.values())) {
            if (!session.isProcessing()) continue;

            TargetBinding target = session.getBinding();
            if (checkSessionTimeout(session, world, timeoutTicks, "PROCESSING timeout")) continue;
            if (world.provider.getDimension() != target.dimension) continue;
            if (!world.isBlockLoaded(target.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                session.reset();
                continue;
            }

            IActionSource source = NetworkAccess.machineSource(owner.host);
            List<ItemStack> inputs = session.getInputs();

            Boolean idle = safeIsIdle(handler, world, target.pos, inputs, session);
            if (idle == null) {
                // isIdle 本身抛异常，视为目标异常，回退并释放
                revertSession(session, "isIdle exception", source);
                continue;
            }

            if (idle) {
                session.beginCollect();
                didWork = true;
                continue;
            }

            // 未 idle：按冷却/次数限制尝试条件启动
            if (shouldAttemptStartProcess(session, world.getTotalWorldTime())) {
                session.setLastStartProcessTick(world.getTotalWorldTime());
                session.incrementStartProcessAttempts();
                Boolean started = safeStartProcess(handler, world, target.pos, source, session);
                if (started != null && !started) {
                    // startProcess 在 tick 阶段返回 false 视为无法继续，安全回退
                    revertSession(session, "startProcess returned false in tick", source);
                }
            }
        }

        // 阶段 2：收集 COLLECTING 产物
        for (TargetSession session : new ArrayList<>(owner.sessions.values())) {
            if (!session.isCollecting()) continue;

            TargetBinding target = session.getBinding();
            if (checkSessionTimeout(session, world, timeoutTicks, "COLLECTING timeout")) continue;
            if (world.provider.getDimension() != target.dimension) continue;
            if (!world.isBlockLoaded(target.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                session.reset();
                continue;
            }

            IActionSource source = NetworkAccess.machineSource(owner.host);
            IAEItemStack[] expected = session.getExpectedOutputs();
            List<ItemStack> inputs = session.getInputs();

            List<ItemStack> products = safeCollectProducts(handler, world, target.pos, expected, inputs, source, session);
            if (products == null) {
                products = new ArrayList<>();
            }

            List<FluidStack> fluidProducts = FluidTransferHelper.collectFluidProducts(world, target.pos, session);
            if (!fluidProducts.isEmpty()) {
                List<FluidStack> overflow = safeInjectFluids(grid, fluidProducts);
                if (overflow.isEmpty()) {
                    didWork = true;
                } else {
                    // 流体通道收不下的部分进接口流体缓冲，逐 tick 重试流体通道。
                    // 产物<b>不</b>回灌机器：机器可能把它当原料吃掉——那是比丢弃更隐蔽的销毁。
                    retainFluids(world, target.pos, overflow, "product collection", false);
                }
            }

            if (!products.isEmpty()) {
                if (stashOrInject(grid, world, products)) {
                    didWork = true;
                }
            }

            Boolean finished = safeHasFinished(handler, world, target.pos, inputs, session);
            if (finished == null) {
                // hasFinished 异常时不能当作已完成：那会在产物尚未收完时关闭会话，
                // 残留产物只能等下一批发配的 clearOutputs 才被清理。
                // 这里保持会话继续收集，由 processingTimeoutTicks 超时回退兜底。
                finished = false;
            }
            session.finishCollect(finished);
            if (!products.isEmpty() || !fluidProducts.isEmpty()) {
                didWork = true;
            }
        }

        return didWork;
    }

    /**
     * 紧急收集指定目标的产物（用于移除绑定前清理、超时清理、异常清理）。
     */
    public void emergencyCollect(TargetBinding binding) {
        TargetSession session = owner.sessions.get(binding);
        if (session == null) return;

        try {
            World world = owner.host.getTileEntity().getWorld();
            if (world == null || world.provider.getDimension() != binding.dimension) return;
            if (!world.isBlockLoaded(binding.pos)) return;

            IRemoteHandler handler = HandlerRegistry.findHandler(binding.blockId);
            if (handler == null) return;

            IActionSource source = NetworkAccess.machineSource(owner.host);
            revertSession(session, "emergency collect", source);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Emergency collect failed for binding {}: {}", binding.pos, e.toString());
            // 最后兜底：至少释放所有权
            session.reset();
        }
    }

    /**
     * 统一回退一个 session：收集产物、回退材料、记录流体、释放所有权。
     *
     * @param reason 回退原因，仅用于日志
     */
    private void revertSession(TargetSession session, String reason, IActionSource source) {
        try {
            TargetBinding target = session.getBinding();
            World world = owner.host.getTileEntity().getWorld();
            if (world != null && world.provider.getDimension() == target.dimension && world.isBlockLoaded(target.pos)) {
                IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
                if (handler != null) {
                    // 仅对已提交(处理中/收集中)的批次做产物扫描。
                    // PUSHING 阶段本批材料尚未开始处理，此刻的全量扫描会把机器里
                    // 上一轮的残留、燃料以及玩家放入的物品一并收进网络。
                    if (session.getState() != TargetState.PUSHING) {
                        List<ItemStack> products = safeCollectProducts(handler, world, target.pos,
                                session.getExpectedOutputs(), session.getInputs(), source, session);
                        if (products != null && !products.isEmpty()) {
                            stashOrInject(owner.gridConnection(), world, products);
                        }
                    }

                    // 回退尚未消耗的材料(按本批输入快照识别)
                    List<ItemStack> reverted = safeRevertMaterials(handler, world, target.pos, source, session);
                    if (reverted != null && !reverted.isEmpty()) {
                        stashOrInject(owner.gridConnection(), world, reverted);
                    }

                    // 流体只记录不抽取：回退的对象是"尚未消耗的材料"，流体留在目标内本来就是安全的，
                    // 再抽一次只是把它搬到接口缓冲里、多引入一次去向决定。这里留下回退记录即可。
                    logPushedFluids(session, target, reason);
                }
            }
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] revertSession failed: {}", e.toString());
        } finally {
            session.reset();
        }
    }

    /**
     * 记录本次回退涉及但保留在目标内的流体，不执行抽取。
     */
    private void logPushedFluids(TargetSession session, TargetBinding target, String reason) {
        List<FluidStack> fluids = session.getInputFluids();
        if (fluids.isEmpty()) {
            fluids = session.getPushedFluids();
        }
        if (fluids.isEmpty()) {
            return;
        }
        long total = 0;
        for (FluidStack fluid : fluids) {
            if (fluid != null) {
                total += fluid.amount;
            }
        }
        AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface revert kept {} mb of fluid inside {} at {} (reason: {})",
                total, target.blockId, target.pos, reason);
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.amount <= 0) {
                continue;
            }
            FluidFlowDiag.logAt(FluidFlowDiag.REVERT_KEEP, fluid, target.blockId, target.pos,
                    "kept inside target on revert (fluid is NOT extracted on revert, so it is not lost)",
                    "reason=" + reason);
        }
    }

    /**
     * 检查指定 session 是否已超时。超时则紧急收集并强制回到 IDLE。
     *
     * @return true 表示已处理超时，调用方无需继续处理该 session
     */
    private boolean checkSessionTimeout(TargetSession session, World world, int timeoutTicks, String reason) {
        if (!session.isTimedOut(world.getTotalWorldTime(), timeoutTicks)) {
            return false;
        }

        TargetBinding target = session.getBinding();
        if (world.provider.getDimension() == target.dimension && world.isBlockLoaded(target.pos)) {
            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler != null) {
                IActionSource source = NetworkAccess.machineSource(owner.host);
                revertSession(session, reason, source);
                return true;
            }
        }
        session.reset();
        return true;
    }

    /**
     * 判断当前是否应当再次对未 idle 的目标调用 startProcess。
     */
    private boolean shouldAttemptStartProcess(TargetSession session, long currentWorldTime) {
        long last = session.getLastStartProcessTick();
        if (last < 0) {
            return true;
        }
        if (currentWorldTime - last < START_PROCESS_COOLDOWN_TICKS) {
            return false;
        }
        return session.getStartProcessAttempts() < MAX_START_PROCESS_ATTEMPTS;
    }

    /**
     * 把产物/回退物品注入网络，失败则暂存到 storage slots。
     *
     * @return 是否有任何物品成功进入网络或 storage
     */
    private boolean stashOrInject(IGridConnection grid, World world, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        if (grid != null && owner.injectItemsToNetwork(grid, world, items)) {
            return true;
        }
        owner.stashItemsToStorage(world, items);
        return !items.isEmpty();
    }

    private List<ItemStack> snapshotItemInputs(InventoryCrafting table) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (!stack.isEmpty()) {
                snapshot.add(stack.copy());
            }
        }
        return snapshot;
    }

    // ---- 带异常隔离的 handler 调用包装 ----

    private List<ItemStack> safeClearOutputs(IRemoteHandler handler, World world, BlockPos pos, IActionSource source, TargetSession session) {
        try {
            return handler.clearOutputs(world, pos, source, session);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler clearOutputs threw at {}: {}", pos, e.toString());
            return new ArrayList<>();
        }
    }

    private List<FluidStack> safeClearOutputFluids(IRemoteHandler handler, World world, BlockPos pos,
                                                   IActionSource source, TargetSession session, List<FluidStack> batchFluids) {
        try {
            List<FluidStack> fluids = handler.clearOutputFluids(world, pos, source, session, batchFluids);
            return fluids != null ? fluids : new ArrayList<>();
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler clearOutputFluids threw at {}: {}", pos, e.toString());
            return new ArrayList<>();
        }
    }

    /**
     * 发配前清理的预检：探察输出罐里会被回收的流体，逐条模拟注入 ME 流体通道，
     * 全部收得下才允许真抽.
     *
     * <p>判据取"全收"而不是"部分可收"：清理的目的是清掉挡住机器开工的残留，
     * 抽走一部分既解不开阻塞，又让这部分流体白白离开机器。收不下就整批不抽，
     * 机器的阻塞状态对玩家保持可见，会话照常走超时/回退路径。</p>
     *
     * <p>探察（{@link FluidTransferHelper#peekExtractableFluids}）与随后
     * {@code clearOutputFluids} 的真实回收扫的是同一批罐、同一判据，因此模拟的整批
     * 与回收的整批是同一条流体清单。</p>
     *
     * @return true 表示网络能收下全部残留（可以执行真正的回收）
     */
    private boolean outputFluidIsSendable(IGridConnection grid, World world, BlockPos pos, List<FluidStack> batchFluids) {
        List<FluidStack> residue;
        try {
            residue = FluidTransferHelper.peekExtractableFluids(world, pos, batchFluids);
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to inspect output tank fluid at {}: {}", pos, t.toString());
            return false;
        }
        if (residue.isEmpty()) {
            return true;
        }
        try {
            for (FluidStack fluid : residue) {
                List<FluidStack> rejected = NetworkAccess.simulateInjectFluidsToNetwork(
                        grid, owner.host, Collections.singletonList(fluid));
                if (!rejected.isEmpty()) {
                    FluidFlowDiag.logAt(FluidFlowDiag.PRE_CLEAN, fluid, blockIdAt(world, pos), pos,
                            "pre-clean skipped, ME fluid channel refused it", "(fluid stays inside the machine)");
                    AE2Enhanced.LOGGER.info("[AE2E] CentralInterface pre-clean skipped at {} ({}): {} mb of {} cannot enter the ME fluid channel; it stays inside the machine",
                            pos, blockIdAt(world, pos), fluid.amount, FluidFlowDiag.fluidName(fluid));
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to simulate fluid injection at {}: {}", pos, t.toString());
            return false;
        }
    }

    /**
     * 将回收到的残留流体注入网络，网络收不下的部分退回到它还剩的去处.
     *
     * <p>本场景的流体来自发配前的输出罐回收，可能包含被误抽的<b>输入</b>流体，
     * 因此允许把流体推回目标（撤销误抽）。</p>
     */
    private void injectFluidsOrKeep(IGridConnection grid, World world, BlockPos pos, List<FluidStack> fluids) {
        List<FluidStack> overflow = safeInjectFluids(grid, fluids);
        if (overflow.isEmpty()) {
            return;
        }
        retainFluids(world, pos, overflow, "dispatch residue", true);
    }

    /**
     * 逐条把流体注入 AE 流体网络，并隔离单条流体的注入异常.
     *
     * <p>逐条而不是整批调用 {@code injectFluidsToNetwork}：整批调用时若第 N 条抛异常
     * （例如流体存储通道未注册导致的 NPE），前面已注入的流体也会被一并当成"未注入"
     * 再走一次兜底 → 注入的部分在网络上出现两份。逐条调用把异常的影响面收敛到出错的那一条。</p>
     *
     * <p>异常本身必须吞掉：本方法在 {@code tick} 的 COLLECTING 阶段被调用，而该阶段没有外层
     * try/catch，异常逃逸会打断整个网格 tick（此时目标罐里的流体已经被抽走，丢失将不可逆）。</p>
     *
     * @return 未能注入网络的流体（含注入抛异常的整条）
     */
    private List<FluidStack> safeInjectFluids(IGridConnection grid, List<FluidStack> fluids) {
        List<FluidStack> overflow = new ArrayList<>();
        if (fluids == null || fluids.isEmpty()) {
            return overflow;
        }
        for (FluidStack fluid : fluids) {
            if (fluid == null || fluid.amount <= 0) {
                continue;
            }
            FluidFlowDiag.logFluid(FluidFlowDiag.INJECT, fluid,
                    "injecting {}mb {} into ME fluid channel", fluid.amount, FluidFlowDiag.fluidName(fluid));
            try {
                List<FluidStack> rejected = NetworkAccess.injectFluidsToNetwork(
                        grid, owner.host, Collections.singletonList(fluid));
                if (rejected.isEmpty()) {
                    FluidFlowDiag.logFluid(FluidFlowDiag.INJECT_OK, fluid,
                            "fluid channel ACCEPTED {}mb {}", fluid.amount, FluidFlowDiag.fluidName(fluid));
                } else {
                    long rejectedAmount = 0;
                    for (FluidStack r : rejected) {
                        if (r != null) {
                            rejectedAmount += r.amount;
                        }
                    }
                    FluidFlowDiag.logFluid(FluidFlowDiag.INJECT_REJECT, fluid,
                            "fluid channel REJECTED {}mb of {} (accepted {}mb)",
                            rejectedAmount, FluidFlowDiag.fluidName(fluid), fluid.amount - rejectedAmount);
                }
                overflow.addAll(rejected);
            } catch (Throwable t) {
                AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface fluid injection failed for {} mb of {}: {}",
                        fluid.amount, fluid.getFluid() != null ? fluid.getFluid().getName() : "unknown", t.toString());
                FluidFlowDiag.logFluid(FluidFlowDiag.INJECT_THROW, fluid,
                        "fluid channel THREW for {}mb {}: {}",
                        fluid.amount, FluidFlowDiag.fluidName(fluid), t.toString());
                overflow.add(fluid.copy());
            }
        }
        return overflow;
    }

    /**
     * 网络收不下的流体的去处：先尝试推回目标机器（撤销误抽），其余进接口流体缓冲.
     *
     * <p>出口只有这两处——留在机器里，或留在接口缓冲里等网络能收时再送。
     * 不转成流体假物品、也不丢弃：流体一旦离开流体通道就基本回不来，
     * 玩家看到的正是"熔岩凭空消失"。</p>
     *
     * @param context       调用场景，仅用于日志（如 {@code dispatch residue} / {@code product collection}）
     * @param allowPushBack 是否允许把流体推回目标机器（只有在"撤销误抽"语义成立时才应为 true）
     */
    private void retainFluids(World world, BlockPos pos, List<FluidStack> overflow, String context, boolean allowPushBack) {
        if (overflow == null || overflow.isEmpty()) {
            return;
        }
        List<FluidStack> remaining = overflow;
        if (allowPushBack) {
            remaining = FluidTransferHelper.pushFluidsToTarget(world, pos, overflow);
            if (remaining.isEmpty()) {
                return;
            }
        }

        CentralFluidBuffer buffer = owner.fluidBuffer();
        for (FluidStack fluid : remaining) {
            buffer.add(fluid);
            FluidFlowDiag.logAt(FluidFlowDiag.BUFFER, fluid, blockIdAt(world, pos), pos,
                    "stashed into interface fluid buffer", "(" + context + ", retried every tick)");
        }
    }

    /**
     * 重试接口流体缓冲里的流体：每 tick 调一次，成功注入<b>流体通道</b>即出队.
     *
     * <p>重试不设放弃线：缓冲是这些流体唯一的落脚点，网络恢复（补上流体存储、能量回充、
     * 区块重新加载）后自然会送出去。重试到 {@link CentralFluidBuffer#MAX_RETRY_ATTEMPTS} 次
     * 仍收不下说明网络长期没有流体通道，按 (节点, 流体) 节流记一次，不刷屏。</p>
     *
     * @return 本 tick 是否做了实际工作（用于 tick 速率调制）
     */
    public boolean retryBufferedFluids(IGridConnection grid) {
        CentralFluidBuffer buffer = owner.fluidBuffer();
        if (buffer.isEmpty()) {
            return false;
        }
        boolean didWork = false;
        for (CentralFluidBuffer.Entry entry : buffer.entries()) {
            FluidStack fluid = entry.fluid();
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
                buffer.remove(entry);
                didWork = true;
                continue;
            }
            entry.countAttempt();
            List<FluidStack> rejected = safeInjectFluids(grid, Collections.singletonList(fluid));
            long rejectedAmount = 0;
            for (FluidStack r : rejected) {
                if (r != null) {
                    rejectedAmount += r.amount;
                }
            }
            if (rejectedAmount < fluid.amount) {
                FluidFlowDiag.logFluid(FluidFlowDiag.BUFFER_FLUSH, fluid,
                        "buffer flushed {}mb {} into ME fluid channel (attempt {})",
                        fluid.amount - rejectedAmount, FluidFlowDiag.fluidName(fluid), entry.attempts());
            }
            if (rejectedAmount <= 0) {
                buffer.remove(entry);
                didWork = true;
                continue;
            }
            if (rejected.get(0) != null && rejected.get(0).amount < fluid.amount) {
                // 部分被接受：保留剩余量，重试计数不清零（避免持续半接受时的无限滞留）
                entry.setFluid(rejected.get(0).copy());
            }
            if (entry.attempts() == buffer.maxAttempts()) {
                // 只提示一次：条目继续留在缓冲里重试，不换去处（换去处只有转物品或丢弃两条，
                // 对已经从机器里抽出来的流体都是真正的消失）。
                FluidStack remaining = entry.fluid();
                FluidFlowDiag.logFluid(FluidFlowDiag.BUFFER, remaining,
                        "still buffered after {} retries: ME fluid channel has not taken {}mb {} yet",
                        entry.attempts(), remaining.amount, FluidFlowDiag.fluidName(remaining));
            }
        }
        return didWork;
    }

    /** 目标方块的注册名，仅用于诊断日志；取不到时返回 {@code null}. */
    private String blockIdAt(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        return block == null || block.getRegistryName() == null ? null : block.getRegistryName().toString();
    }

    private boolean safeCanStart(IRemoteHandler handler, World world, BlockPos pos, InventoryCrafting table, TargetSession session) {
        try {
            return handler.canStart(world, pos, table, session);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler canStart threw at {}: {}", pos, e.toString());
            return false;
        }
    }

    private boolean safePushMaterials(IRemoteHandler handler, World world, BlockPos pos,
                                      InventoryCrafting table, IActionSource source, TargetSession session) {
        try {
            return handler.pushMaterials(world, pos, table, source, session);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler pushMaterials threw at {}: {}", pos, e.toString());
            return false;
        }
    }

    private Boolean safeStartProcess(IRemoteHandler handler, World world, BlockPos pos, IActionSource source, TargetSession session) {
        try {
            return handler.startProcess(world, pos, source, session);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler startProcess threw at {}: {}", pos, e.toString());
            return false;
        }
    }

    private Boolean safeIsIdle(IRemoteHandler handler, World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        try {
            return handler.isIdle(world, pos, inputs, session);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler isIdle threw at {}: {}", pos, e.toString());
            return null;
        }
    }

    private List<ItemStack> safeCollectProducts(IRemoteHandler handler, World world, BlockPos pos,
                                                IAEItemStack[] expectedOutputs, List<ItemStack> inputs,
                                                IActionSource source, TargetSession session) {
        try {
            return handler.collectProducts(world, pos, expectedOutputs, inputs, source, session);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler collectProducts threw at {}: {}", pos, e.toString());
            return null;
        }
    }

    private List<ItemStack> safeRevertMaterials(IRemoteHandler handler, World world, BlockPos pos, IActionSource source, TargetSession session) {
        try {
            return handler.revertMaterials(world, pos, source, session);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler revertMaterials threw at {}: {}", pos, e.toString());
            return new ArrayList<>();
        }
    }

    private Boolean safeHasFinished(IRemoteHandler handler, World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        try {
            return handler.hasFinished(world, pos, inputs, session);
        } catch (Throwable e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler hasFinished threw at {}: {}", pos, e.toString());
            return null;
        }
    }
}
