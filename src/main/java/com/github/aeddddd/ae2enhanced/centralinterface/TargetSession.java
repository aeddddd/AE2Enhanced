package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.PushLedger;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个远程绑定目标的运行时状态封装.
 *
 * <p>把状态、输入快照、预期产物、启动时间、已推流体等全部收敛到一个对象里。</p>
 */
public class TargetSession {

    private final TargetBinding binding;
    private final DualityCentralInterface owner;

    private TargetState state;
    private long startTime;
    private IAEItemStack[] expectedOutputs;
    private List<ItemStack> inputs;
    private List<FluidStack> pushedFluids;
    private List<FluidStack> inputFluids;

    // 本批物理推料的逐槽台账（Frame 层 ItemTransferHelper 在真实插入阶段记录）。
    // 只服务于本批发配的失败/超时回退：回退时按台账精确取回"我们实际放进去的量"，
    // 避免把机器原有的同型物品一起取走。不写入 NBT，reset 时清空。
    private PushLedger pushLedger;

    // 物理发配运行时的上下文，供 dispatcher 与 handler 判断 grace 期，
    // 避免 handler 实例保存 per-target 的 map 造成并行隔离问题。
    private long pushTick = -1;
    private long lastStartProcessTick = -1;
    private int startProcessAttempts = 0;

    // Handler 可在本 session 中临时缓存配方对象，替代 handler 实例中的 recipeCache map。
    // 该字段不持久化，session reset 时自动清空。
    private Object recipeCache;

    // Handler 可在本 session 中缓存"非配方"类的临时对象（如龙研聚合核心的注入器列表），
    // 与 recipeCache 分开存放，避免同一 handler 的两类缓存互相覆盖。
    // 该字段不持久化，session reset 时自动清空。
    private Object handlerCache;

    public TargetSession(TargetBinding binding, DualityCentralInterface owner) {
        this.binding = binding;
        this.owner = owner;
        this.state = TargetState.IDLE;
    }

    public TargetBinding getBinding() {
        return binding;
    }

    public TargetState getState() {
        return state;
    }

    public boolean isIdle() {
        return state == TargetState.IDLE;
    }

    public boolean isProcessing() {
        return state == TargetState.PROCESSING;
    }

    public boolean isCollecting() {
        return state == TargetState.COLLECTING;
    }

    public boolean isUnavailable() {
        return state == TargetState.UNAVAILABLE;
    }

    public long getStartTime() {
        return startTime;
    }

    public IAEItemStack[] getExpectedOutputs() {
        return expectedOutputs;
    }

    public List<ItemStack> getInputs() {
        return inputs != null ? inputs : Collections.emptyList();
    }

    public List<FluidStack> getPushedFluids() {
        return pushedFluids != null ? pushedFluids : Collections.emptyList();
    }

    public List<FluidStack> getInputFluids() {
        return inputFluids != null ? inputFluids : Collections.emptyList();
    }

    public long getPushTick() {
        return pushTick;
    }

    public void setPushTick(long pushTick) {
        this.pushTick = pushTick;
    }

    public long getLastStartProcessTick() {
        return lastStartProcessTick;
    }

    public void setLastStartProcessTick(long lastStartProcessTick) {
        this.lastStartProcessTick = lastStartProcessTick;
    }

    public int getStartProcessAttempts() {
        return startProcessAttempts;
    }

    public void incrementStartProcessAttempts() {
        this.startProcessAttempts++;
    }

    /**
     * 判断自推料以来是否已过指定 grace tick。
     */
    public boolean isPushGraceElapsed(long currentWorldTime, int graceTicks) {
        if (pushTick < 0) {
            return true;
        }
        return currentWorldTime > pushTick + graceTicks;
    }

    /**
     * 开始一次物理推送。
     *
     * @return 成功获取坐标所有权时返回 true
     */
    public boolean beginPush(List<FluidStack> pushedFluids) {
        if (!TargetOwnershipTracker.instance().tryAcquire(binding, owner)) {
            return false;
        }
        this.state = TargetState.PUSHING;
        this.pushedFluids = pushedFluids != null ? new ArrayList<>(pushedFluids) : new ArrayList<>();
        // 新一批开始：台账只能属于当前这一批，旧的（若有）必须丢弃，
        // 否则本批推料失败时会按上一批的台账去取回物品。
        this.pushLedger = null;
        return true;
    }

    /**
     * 在清理目标输出槽之前预先登记本批预期产物。
     *
     * <p>{@link IRemoteHandler#clearOutputs} 依赖 session 中的预期产物来识别上一轮的残留产物，
     * 若等到 {@link #commitPush} 才写入，清理阶段只能看到空快照，通用处理器将无法清理任何残留。</p>
     */
    public void preCommitExpectedOutputs(IAEItemStack[] expectedOutputs) {
        if (this.state != TargetState.PUSHING) {
            throw new IllegalStateException("Cannot pre-commit outputs from state " + this.state);
        }
        this.expectedOutputs = expectedOutputs != null ? expectedOutputs.clone() : null;
    }

    /**
     * 记录本批物理推料的逐槽台账（{@code ItemTransferHelper.PushOutcome#ledger()}）。
     *
     * <p>与其它 {@code record*} 方法不同，这里<b>不做状态校验</b>：{@code pushMaterials} 由各
     * handler 自行实现，有可能在非 {@code PUSHING} 状态下被调用；台账只服务于回退，为一个
     * 辅助记录把整个发配失败是不划算的。台账跨 {@link #commitPush} 保留（处理中/收集中的超时
     * 回退同样需要它），{@link #reset()} 时清空。</p>
     */
    public void recordPushLedger(PushLedger ledger) {
        this.pushLedger = ledger;
    }

    /**
     * 本批推料的逐槽台账；未记录（handler 自行推料、或尚未推料）时为 {@code null}。
     */
    public PushLedger getPushLedger() {
        return this.pushLedger;
    }

    /**
     * 记录本批已推入目标的物品输入快照与流体。
     *
     * <p>发配在 {@link #commitPush} 之前失败时，会话仍处于 {@code PUSHING}，
     * 此处的快照是 {@link IRemoteHandler#revertMaterials} 识别“本批材料”的唯一依据，
     * 同时也是回退日志中已推流体的记录来源。</p>
     */
    public void recordPushedBatch(List<ItemStack> inputs, List<FluidStack> fluids) {
        if (this.state != TargetState.PUSHING) {
            throw new IllegalStateException("Cannot record pushed batch from state " + this.state);
        }
        this.inputs = inputs != null ? new ArrayList<>(inputs) : null;
        this.pushedFluids = fluids != null ? new ArrayList<>(fluids) : null;
    }

    /**
     * 推送成功，进入处理中状态。
     */
    public void commitPush(IAEItemStack[] expectedOutputs, List<ItemStack> inputs, List<FluidStack> inputFluids, long startTime) {
        if (this.state != TargetState.PUSHING) {
            throw new IllegalStateException("Cannot commit push from state " + this.state);
        }
        this.state = TargetState.PROCESSING;
        this.expectedOutputs = expectedOutputs != null ? expectedOutputs.clone() : null;
        this.inputs = inputs != null ? new ArrayList<>(inputs) : null;
        this.inputFluids = inputFluids != null ? new ArrayList<>(inputFluids) : null;
        this.startTime = startTime;
        this.pushTick = startTime;
        this.lastStartProcessTick = -1;
        this.startProcessAttempts = 0;
        this.pushedFluids = null;
    }

    /**
     * 开始收集产物。
     */
    public void beginCollect() {
        if (this.state != TargetState.PROCESSING) {
            throw new IllegalStateException("Cannot begin collect from state " + this.state);
        }
        this.state = TargetState.COLLECTING;
    }

    /**
     * 收集完成，根据是否还有后续产物决定回到空闲还是继续处理。
     *
     * @param finished true 表示该次发配已完全结束
     */
    public void finishCollect(boolean finished) {
        if (this.state != TargetState.COLLECTING) {
            throw new IllegalStateException("Cannot finish collect from state " + this.state);
        }
        if (finished) {
            reset();
        } else {
            this.state = TargetState.PROCESSING;
        }
    }

    /**
     * 重置为 IDLE 并释放所有权、清空运行时数据。
     */
    public void reset() {
        TargetOwnershipTracker.instance().release(binding, owner);
        this.state = TargetState.IDLE;
        this.startTime = 0;
        this.expectedOutputs = null;
        this.inputs = null;
        this.pushedFluids = null;
        this.inputFluids = null;
        this.pushLedger = null;
        this.pushTick = -1;
        this.lastStartProcessTick = -1;
        this.startProcessAttempts = 0;
        this.recipeCache = null;
        this.handlerCache = null;
    }

    /**
     * 标记目标不可用，释放所有权。
     */
    public void setUnavailable() {
        TargetOwnershipTracker.instance().release(binding, owner);
        this.state = TargetState.UNAVAILABLE;
    }

    /**
     * 从不可用恢复为空闲。
     */
    public void recoverFromUnavailable() {
        if (this.state != TargetState.UNAVAILABLE) {
            return;
        }
        this.state = TargetState.IDLE;
    }

    /**
     * 获取 handler 在本 session 中缓存的临时对象（如配方）。
     */
    public Object getRecipeCache() {
        return recipeCache;
    }

    /**
     * 设置 handler 在本 session 中缓存的临时对象。
     */
    public void setRecipeCache(Object recipeCache) {
        this.recipeCache = recipeCache;
    }

    /**
     * 清空本 session 的配方缓存。
     */
    public void clearRecipeCache() {
        this.recipeCache = null;
    }

    /**
     * 获取 handler 在本 session 中缓存的临时对象（非配方类，如注入器列表）。
     */
    public Object getHandlerCache() {
        return handlerCache;
    }

    /**
     * 设置 handler 在本 session 中缓存的临时对象（非配方类）。
     */
    public void setHandlerCache(Object handlerCache) {
        this.handlerCache = handlerCache;
    }

    /**
     * 清空本 session 的非配方类临时缓存。
     */
    public void clearHandlerCache() {
        this.handlerCache = null;
    }

    /**
     * 检查当前处理是否已超时。
     */
    public boolean isTimedOut(long currentWorldTime, int timeoutTicks) {
        if (this.state != TargetState.PROCESSING && this.state != TargetState.COLLECTING) {
            return false;
        }
        long elapsed = currentWorldTime - this.startTime;
        return elapsed >= timeoutTicks;
    }

    @Override
    public String toString() {
        return "TargetSession{" +
                "binding=" + binding +
                ", state=" + state +
                ", startTime=" + startTime +
                '}';
    }
}
