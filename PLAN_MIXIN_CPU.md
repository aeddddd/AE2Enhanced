# AE2Enhanced — 超因果计算核心 Mixin 集成规划

> **状态**：待审阅  
> **版本**：v1.0-plan | 2026-04-29  
> **前提**：TileComputationCore 已清理 ICraftingProvider/ICraftingMedium，编译通过

---

## 一、技术调研结论

### 1.1 AE2-UEL CraftingGridCache 的硬绑定

通过反编译 `ae2-uel-v0.56.7.jar` 确认：

```java
// CraftingGridCache.addNode(IGridNode, IGridHost)
public void addNode(IGridNode node, IGridHost host) {
    if (host instanceof TileCraftingTile) {        // 硬编码类型检查
        TileCraftingStorageTile storage = (TileCraftingStorageTile) host;
        CraftingCPUCluster cluster = (CraftingCPUCluster) storage.getCluster();
        if (cluster != null) {
            this.craftingCPUClusters.add(cluster);
        }
    }
    // ... 其他 provider/watcher 处理
}
```

- `craftingCPUClusters` 字段类型为 `Set<CraftingCPUCluster>`（**非 `Set<ICraftingCPU>`**）
- `getCpus()` 通过 `ActiveCpuIterator` 包装该集合返回 `ImmutableSet<ICraftingCPU>`
- `submitJob()` 内部遍历 `craftingCPUClusters`，按 `getAvailableStorage()` / `getCoProcessors()` 排序选址
- `hasCpu()` 直接 `instanceof CraftingCPUCluster` + `Set.contains()`

**结论**：计算核心**无法**通过单纯实现 `ICraftingCPU` 被 AE2 原生识别。必须在 `CraftingGridCache` 层面通过 Mixin 注入。

### 1.2 ICraftingCPU 接口清单（公共 API）

```java
public interface ICraftingCPU extends IBaseMonitor<IAEItemStack> {
    boolean isBusy();
    IActionSource getActionSource();
    long getAvailableStorage();     // 合成存储字节容量
    int getCoProcessors();          // 协处理器数 = 并行度
    String getName();
    default IAEItemStack getFinalOutput();
    default long getRemainingItemCount();
    default long getStartItemCount();
}
```

`IBaseMonitor<T>` 继承链：`IBaseMonitor → IMEInventoryHandler → IMEInventory`，需实现 `injectItems` / `extractItems` / `getAvailableItems` / `addListener` / `removeListener` 等方法。

---

## 二、总体架构（修正后）

```
TileComputationCore
├── AENetworkProxy (IGridProxyable)
├── ICraftingCPU 实现（通过内部代理类 ComputationCoreCPU）
│   ├── isBusy() → scheduler 状态
│   ├── getAvailableStorage() → Long.MAX_VALUE（内部 BigInteger，对外暴露 long 上限）
│   ├── getCoProcessors() → 16384
│   ├── submitJob() → 将 ICraftingJob 转换为 CraftingOrder 入队
│   └── IBaseMonitor 空壳/透传（P1 骨架阶段最小实现）
├── OrderScheduler
│   ├── pendingQueue: Queue<CraftingOrder>
│   ├── activeOrders: List<CraftingOrder>
│   └── 优先级：终端订单 > 自动合成卡
├── ParallelAllocator (MAX_PARALLEL = 16384)
├── BatchManager
│   └── BigInteger → long[] 子批次拆分
└── CraftingEngine（P2）
    ├── 材料抽取：从 AE2 网络 IMEMonitor 拉取
    ├── 子批次执行：按拆分后的 long[] 逐批次虚拟合成
    ├── 产物回写：injectItems 到网络存储
    └── 嵌套配方：递归 submit 子订单（或复用 AE2 ICraftingJob 树）

MixinCraftingGridCache
├── 新增字段：Set<ComputationCoreCPU> computationCores
├── addNode → 识别 TileComputationCore，提取其 CPU 代理加入集合
├── removeNode → 从集合移除
├── getCpus → 合并原生 CraftingCPUCluster + computationCores 返回
├── hasCpu → 扩展判断
└── submitJob → 当传入 ICraftingCPU 为 ComputationCoreCPU 时直接转发
```

---

## 三、Mixin 详细设计

### 3.1 目标类

| 目标类 | 路径 | Mixin 文件名 |
|---|---|---|
| `CraftingGridCache` | `appeng.me.cache.CraftingGridCache` | `MixinCraftingGridCache.java` |

### 3.2 字段注入

```java
@Unique
private final Set<ComputationCoreCPU> ae2enhanced$computationCores = new HashSet<>();
```

### 3.3 方法注入点

#### (1) addNode — 注册计算核心

```java
@Inject(method = "addNode", at = @At("HEAD"))
private void ae2enhanced$onAddNode(IGridNode node, IGridHost host, CallbackInfo ci) {
    if (host instanceof TileComputationCore) {
        TileComputationCore core = (TileComputationCore) host;
        if (core.isFormed()) {
            ComputationCoreCPU cpu = core.getCpuProxy();
            if (cpu != null) {
                ae2enhanced$computationCores.add(cpu);
                this.updateCPUClusters(new MENetworkCraftingCpuChange(node));
            }
        }
    }
}
```

#### (2) removeNode — 注销计算核心

```java
@Inject(method = "removeNode", at = @At("HEAD"))
private void ae2enhanced$onRemoveNode(IGridNode node, IGridHost host, CallbackInfo ci) {
    if (host instanceof TileComputationCore) {
        TileComputationCore core = (TileComputationCore) host;
        ComputationCoreCPU cpu = core.getCpuProxy();
        if (cpu != null) {
            ae2enhanced$computationCores.remove(cpu);
            this.updateCPUClusters(new MENetworkCraftingCpuChange(node));
        }
    }
}
```

#### (3) getCpus — 合并返回

```java
@ModifyVariable(
    method = "getCpus",
    at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet;copyOf(Ljava/util/Iterator;)Lcom/google/common/collect/ImmutableSet;"),
    ordinal = 0
)
private Iterator<ICraftingCPU> ae2enhanced$injectCpus(Iterator<ICraftingCPU> original) {
    return Iterators.concat(original, ae2enhanced$computationCores.iterator());
}
```

*备选：若 ModifyVariable 不稳定，采用 `@Overwrite` 重写 `getCpus()`，先调用原逻辑再合并。*

#### (4) hasCpu — 扩展判断

```java
@Inject(method = "hasCpu", at = @At("HEAD"), cancellable = true)
private void ae2enhanced$hasCpu(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
    if (cpu instanceof ComputationCoreCPU && ae2enhanced$computationCores.contains(cpu)) {
        cir.setReturnValue(true);
    }
}
```

#### (5) submitJob — 支持计算核心直接接单

```java
@Inject(
    method = "submitJob",
    at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;findFirst()Ljava/util/Optional;"),
    locals = LocalCapture.CAPTURE_FAILHARD
)
private void ae2enhanced$onSubmitJob(
    ICraftingJob job, ICraftingRequester req, ICraftingCPU targetCpu,
    boolean prioritizePower, IActionSource src, CallbackInfoReturnable<ICraftingLink> cir,
    // ... locals
) {
    if (targetCpu instanceof ComputationCoreCPU) {
        ICraftingLink link = ((ComputationCoreCPU) targetCpu).submitJob(this.myGrid, job, src, req);
        cir.setReturnValue(link);
    }
}
```

*注：若注入点不稳定，可在方法开头判断 `targetCpu instanceof ComputationCoreCPU` 后直接 `cir.setReturnValue(...)`。*

### 3.4 事件联动

当 `TileComputationCore` 完成结构组装（`assemble()`）或解体（`disassemble()`）时，需要触发 AE2 网络的 CPU 列表刷新：

```java
// TileComputationCore.assemble()
IGridNode node = getProxy().getNode();
if (node != null && node.getGrid() != null) {
    node.getGrid().postEvent(new MENetworkCraftingCpuChange(node));
}
```

`CraftingGridCache.updateCPUClusters(MENetworkCraftingCpuChange)` 会被调用，触发 `getCpus()` 刷新并同步到客户端（`PacketCraftingCPUsUpdate`）。

---

## 四、ComputationCoreCPU（内部代理类）

为避免 `TileComputationCore` 直接实现庞大的 `IBaseMonitor` 链条，引入轻量代理：

```java
public class ComputationCoreCPU implements ICraftingCPU {
    private final TileComputationCore core;
    private final IActionSource actionSource;
    private final Set<IMEMonitorHandlerReceiver<IAEItemStack>> listeners = new HashSet<>();
    
    // ICraftingCPU 实现
    public boolean isBusy() { return core.getScheduler().getActiveCount() >= core.getParallelLimit(); }
    public IActionSource getActionSource() { return actionSource; }
    public long getAvailableStorage() { return Long.MAX_VALUE; }
    public int getCoProcessors() { return ParallelAllocator.MAX_PARALLEL; }
    public String getName() { return I18n.format("tile.ae2enhanced.computation_core.name"); }
    
    // IBaseMonitor 最小实现（P1 骨架）
    public void addListener(...) { listeners.add(receiver); }
    public void removeListener(...) { listeners.remove(receiver); }
    public IAEItemStack injectItems(...) { return items; } // 透传/拒绝
    public IAEItemStack extractItems(...) { return null; }
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) { return out; }
    
    // 订单提交入口
    public ICraftingLink submitJob(IGrid grid, ICraftingJob job, IActionSource src, ICraftingRequester req) {
        CraftingOrder order = new CraftingOrder(job, BigInteger.valueOf(job.getByteTotal())); // 需调整
        core.getScheduler().submit(order);
        return new ComputationCoreCraftingLink(order); // 自建 Link 实现
    }
}
```

---

## 五、阶段划分（修订版）

| 阶段 | 内容 | 工期预估 | 依赖 |
|---|---|---|---|
| **P1-S1** | Mixin 骨架：`MixinCraftingGridCache` 字段注入 + `addNode`/`removeNode`/`getCpus`/`hasCpu` | 1d | Mixin 配置已就绪 |
| **P1-S2** | `ComputationCoreCPU` 代理类 + `ICraftingCPU` 最小实现 | 0.5d | P1-S1 |
| **P1-S3** | `TileComputationCore` 接入：组装/解体时触发 `MENetworkCraftingCpuChange` | 0.5d | P1-S2 |
| **P1-S4** | 终端验证：确保 AE 终端能识别计算核心为可用 CPU，显示名称/并行/存储容量正确 | 0.5d | P1-S3 |
| **P1-S5** | `OrderScheduler` + `ParallelAllocator` + `BatchManager` 完整订单驱动循环 | 2d | P1-S4 |
| **P2** | `CraftingEngine`：材料抽取、子批次虚拟合成、产物回写、嵌套配方 | 3d | P1-S5 |
| **P3** | GUI：订单列表、并行可视化、失败重试、历史记录 | 2d | P2 |
| **P4** | 极端压力测试、配方数据、本地化补全 | 2d | P3 |

---

## 六、风险与兜底

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| `CraftingGridCache` 内部方法签名在 AE2-UEL 子版本间变化 | Mixin 失效/崩溃 | 严格限定 Mixin 目标方法为 `obfuscated` + `remap = true`；CI 编译锁定 AE2-UEL 版本 |
| `submitJob` 注入点不稳定 | 无法将订单路由到计算核心 | 准备 `@Overwrite` 兜底方案；或改用 `@Inject` HEAD + 提前 return |
| `IBaseMonitor` 实现遗漏导致 NPE | 终端打开崩溃 | P1-S2 阶段完整列出接口方法，逐一实现空壳/透传 |
| 计算核心与原生 CraftingCPU 共存时的竞争 | 订单被原生 CPU 抢走 | `submitJob` 中若 `targetCpu == null`（自动选址），确保计算核心在排序中优先级正确（高并行优先） |
| `ICraftingJob` 的树结构解析复杂 | P2 工期膨胀 | 先复用 AE2 `CraftingJob` 的现有解析逻辑，通过反射获取树节点；若不可行则退化为扁平批次执行（限制：暂不支持嵌套配方动态重算） |

---

## 七、待确认事项（请审阅时回答）

1. **P1-S5 订单驱动方式**：计算核心是执行 `ICraftingJob` 的完整树结构（含嵌套配方动态调度），还是扁平化执行（一次性抽取全部材料，虚拟合成全部产物）？前者更接近原生 CPU，后者实现简单但功能受限。
2. **与原生 CraftingCPU 共存策略**：当网络中同时存在原生 CPU 和计算核心时，
   - A. 计算核心优先接单（高并行）
   - B. 原生 CPU 优先接单（保持原生行为）
   - C. 玩家在终端手动选择（需要 Mixin 终端 UI）
3. **IBaseMonitor 透传实现**：计算核心的 `injectItems`/`extractItems` 是透传给网络存储，还是仅作为内部合成缓冲？

---

*文档版本：v1.0-plan | 编写者：Kimi Code CLI | 等待审阅*
