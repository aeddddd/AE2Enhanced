package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 物理发配调度器.
 *
 * <p>负责把单个样板的材料实际推送到目标机器，并在失败时统一回退材料和流体。
 * 成功后目标进入 {@link TargetSession} 的 PROCESSING 状态，由
 * {@link DualityCentralInterface#tickingRequest} 继续 tick 收集产物。</p>
 *
 * <p>核心设计原则：</p>
 * <ul>
 *   <li><b>异常隔离</b>：所有 handler 调用都包 try/catch，单个 handler 异常不会扩散到整个 tile。</li>
 *   <li><b>推料原子性</b>：{@code pushMaterials} 返回 true 时必须保证全部材料已进入目标；否则由
 *       {@link #revertSession} 统一回退。</li>
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
    public boolean dispatch(AENetworkProxy proxy,
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

        if (!handler.isValidTarget(world, target.pos)) {
            session.setUnavailable();
            return false;
        }

        // 复制 table，避免一个 target 的失败影响其他 target
        InventoryCrafting table = owner.copyInventoryCrafting(originalTable);
        IActionSource source = new MachineSource(owner.host);

        // 获取全局坐标所有权，进入 PUSHING 状态
        List<FluidStack> pushedFluids = new ArrayList<>();
        if (!session.beginPush(pushedFluids)) {
            return false;
        }

        boolean success = false;
        try {
            // 1. 发配前回收目标输出槽残留内容，防止残留产物干扰新材料推送
            List<ItemStack> clearedOutputs = safeClearOutputs(handler, world, target.pos, source, session);
            if (!clearedOutputs.isEmpty()) {
                stashOrInject(proxy, world, clearedOutputs);
            }

            // 2. 推送流体输入（如果配方包含流体）
            if (!owner.pushFluidInputs(world, target.pos, table, pushedFluids)) {
                revertSession(session, "pushFluidInputs failed", source);
                return false;
            }

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
        } catch (Exception e) {
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
    public boolean tick(AENetworkProxy proxy, World world, int timeoutTicks) {
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

            IActionSource source = new MachineSource(owner.host);
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

            IActionSource source = new MachineSource(owner.host);
            IAEItemStack[] expected = session.getExpectedOutputs();
            List<ItemStack> inputs = session.getInputs();

            List<ItemStack> products = safeCollectProducts(handler, world, target.pos, expected, inputs, source, session);
            if (products == null) {
                products = new ArrayList<>();
            }

            List<ItemStack> fluidProducts = owner.collectFluidProducts(world, target.pos, session);
            if (!fluidProducts.isEmpty()) {
                products.addAll(fluidProducts);
            }

            if (!products.isEmpty()) {
                if (stashOrInject(proxy, world, products)) {
                    didWork = true;
                }
            }

            Boolean finished = safeHasFinished(handler, world, target.pos, inputs, session);
            if (finished == null) {
                // hasFinished 异常，安全结束会话避免无限循环
                finished = true;
            }
            session.finishCollect(finished);
            if (!products.isEmpty()) {
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

            IActionSource source = new MachineSource(owner.host);
            revertSession(session, "emergency collect", source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Emergency collect failed for binding {}: {}", binding.pos, e.toString());
            // 最后兜底：至少释放所有权
            session.reset();
        }
    }

    /**
     * 统一回退一个 session：收集产物、回退材料、回退流体、释放所有权。
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
                    // 先尝试收集可能已产生的产物
                    List<ItemStack> products = safeCollectProducts(handler, world, target.pos,
                            session.getExpectedOutputs(), session.getInputs(), source, session);
                    if (products != null && !products.isEmpty()) {
                        stashOrInject(owner.host.getProxy(), world, products);
                    }

                    // 回退尚未消耗的材料
                    List<ItemStack> reverted = safeRevertMaterials(handler, world, target.pos, source, session);
                    if (reverted != null && !reverted.isEmpty()) {
                        stashOrInject(owner.host.getProxy(), world, reverted);
                    }

                    // 回退已推流体
                    owner.revertPushedFluids(world, target.pos, session.getInputFluids());
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] revertSession failed: {}", e.toString());
        } finally {
            session.reset();
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
                IActionSource source = new MachineSource(owner.host);
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
    private boolean stashOrInject(AENetworkProxy proxy, World world, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        if (proxy != null && owner.injectItemsToNetwork(proxy, world, items)) {
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
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler clearOutputs threw at {}: {}", pos, e.toString());
            return new ArrayList<>();
        }
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler isIdle threw at {}: {}", pos, e.toString());
            return null;
        }
    }

    private List<ItemStack> safeCollectProducts(IRemoteHandler handler, World world, BlockPos pos,
                                                IAEItemStack[] expectedOutputs, List<ItemStack> inputs,
                                                IActionSource source, TargetSession session) {
        try {
            return handler.collectProducts(world, pos, expectedOutputs, inputs, source, session);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler collectProducts threw at {}: {}", pos, e.toString());
            return null;
        }
    }

    private List<ItemStack> safeRevertMaterials(IRemoteHandler handler, World world, BlockPos pos, IActionSource source, TargetSession session) {
        try {
            return handler.revertMaterials(world, pos, source, session);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler revertMaterials threw at {}: {}", pos, e.toString());
            return new ArrayList<>();
        }
    }

    private Boolean safeHasFinished(IRemoteHandler handler, World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        try {
            return handler.hasFinished(world, pos, inputs, session);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Handler hasFinished threw at {}: {}", pos, e.toString());
            return null;
        }
    }
}
