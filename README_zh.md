# AE2Enhanced

AE2Enhanced 是 **AE2 Unofficial Extended Life (AE2-UEL)** 的终局扩展模组, 为后期游戏引入了多个大型多方块结构:

- **超因果装配枢纽** —— 类似 LazyAE 大型分子装配室的巨型合成阵列, 以极高的并行和速度执行合成样板.
- **超维度仓储中枢** —— 突破 `long` 上限的无限容量存储结构, 支持物品、流体、气体(Mekanism)与源质(Thaumcraft), 数据持久化至外部文件, 避免存档膨胀.
- **超因果计算核心** —— 超级合成 CPU, 拥有 `Long.MAX_VALUE` 合成存储容量和 16384 并行加速器槽位, 支持动态虚拟 CPU 集群池实现多订单并发.

---

## 超因果装配枢纽

基础运行参数为 **64 并行, 1 秒每次**, 相当于 `LazyAE` 大型分子装配室在不修改配置文件时的最强表现.

**需要强调, 装配枢纽并行不依赖 CPU 并行!**

安装全部 5 个并行升级后, 并行上限达到 `Long.MAX_VALUE`, 在绝大多数整合包中等价于无穷大(这甚至超过了 `1.12.2 AE` 终端的显示上限!).

速度升级允许将冷却从默认的 `20 ticks` 压缩至 `1 tick`, 合成不再成为工厂发展的瓶颈.

此外, 多方块提供了类似高版本 `EAEP` 装配矩阵上传核心的**自动上传模块**, 允许将编码好的合成样板自动上传到枢纽中, 同时具备重复检测功能, 重复样板不会二次上传.

---

## 超维度仓储中枢

专为解决后期 AE2 网络存储容量瓶颈而设计.

- **BigInteger 级容量**: 单结构容量理论上无上限, 彻底绕过 `int`/`long` 限制
- **多类型兼容**: 原生支持物品、流体; 可选支持 Mekanism 气体与 Thaumcraft 源质(需安装对应模组)
- **外部文件持久化**: 数据存储于 `<world>/ae2enhanced/storage/<uuid>.dat`, 避免因为存储大量NBT造成的卡顿和溢出问题.
- **安全模式**: 文件异常时自动进入只读状态, 防止数据损坏, 并且支持安全的Mod版本更新, 不会因为更新mod丢失物品.
- **第三方扩展**: 提供 `registerExternalAdapter()` API, 其他模组可接入自定义存储类型

---

## 超因果计算核心

第三阶段多方块结构, 充当 AE2 网络的**超级合成 CPU**.

- **海量合成存储**: `Long.MAX_VALUE` 字节合成存储容量 —— 对任何实际订单而言等同于无限.
- **16384 加速器容量**: 可通过 `AE2EnhancedConfig.crafting.maxParallel` 配置.
- **多订单并发**: 动态生成虚拟 `CraftingCPUCluster` 实例处理并发合成任务. 每个订单独占一个集群; 空闲的额外集群自动回收.
- **网络原生集成**: 核心直接借用控制器的 AE2 网络节点, 对网络表现为原生 CPU. 控制器本身不需要独立 ME 线缆连接.
- **ME 接口方块**: 专用的 `super_crafting_interface` 方块作为结构的线缆接入点.
- **戴森球 TESR**: 全结构全息投影, 包含实心核心、科技网格、面板、能量流与轨道环.
- **大数格式化**: Z/Y 单位 + 科学计数法 + Shift 切换, 精确显示数量.

### 多订单支持 (Mixin 架构)

计算核心通过 Mixin 注入 AE2 的 `CraftingGridCache` 管理虚拟 `CraftingCPUCluster` 实例池:

- `MixinCraftingGridCache` 通过 `addNode`/`removeNode` 生命周期钩子追踪 `TileComputationCore` 实例.
- 每次 `updateCPUClusters()` 重建后, 虚拟集群被重新注入 `craftingCPUClusters`.
- 备用反射注入每 5 tick 运行一次, 确保终端始终能看到最新的 CPU 池.
- `MixinCraftingCPUCluster` 为虚拟集群重定向 `getCore()` 和 `updateCraftingLogic()`, 支持带网络库存补给的批量合成.

---

## 性能

### 装配枢纽
采取虚拟合成 + 真实合成的混合机制. 即使对于极大数目的下单, `mspt` 也不会受到明显影响, 对于绝大多数 AE 允许的下单均完成了适配.

> 额外补充
> 在 CRT 魔改合成配方时, 简单的 `.reuse` 并不能让 AE 知道这个物品不被消耗, 下单时仍会正常请求对应份数.

### 超维度仓储中枢
采取异步加增量刷新模式, 使用外部文件存储数据, 从根本上解决了NBT溢出和卡顿的问题, 并且支持极高的存储空间.

### 计算核心
虚拟 CPU 集群将实际合成委托给现有的网络装配室和 ME 接口, 最小化开销. 动态池确保资源仅在需要时分配.

> 目前内置了气体(Mekanism)与源质(Thaumcraft)的支持, 在安装对应Mod后自动启用, 对于其他存储类型的支持可以在`issues`中提出, 并且提供了api便于扩展.

---

## 配置文件

位于 `config/ae2enhanced.cfg`, 提供以下可调参数:

| 分类 | 参数 | 说明 |
|---|---|---|
| Storage | `flushIntervalSeconds` | 存储文件自动刷盘间隔 (秒, 默认 5) |
| Render | `enableHyperdimensionalRenderer` | 是否启用特效渲染 (默认 true) |
| Render | `renderDistance` | 最大渲染距离 (方块, 默认 64) |
| BlackHole | `damageMode` | 黑洞伤害模式: ALL / NON_CREATIVE / NONE (默认 ALL) |
| Crafting | `maxParallel` | 计算核心加速器容量 (默认 16384) |

---

## 需求

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 8.9+

可选前置:
- **Mekanism + MekanismEnergistics** —— 启用气体存储支持
- **Thaumcraft + Thaumic Energistics** —— 启用源质存储支持

---

## 下载

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced)  
[Releases](https://github.com/aeddddd/AE2Enhanced/releases)

---

## 兼容性

兼容 **Cleanroom**, 对其他 AE 附属保持良好兼容, 在大型整合包 `Divine Journey 2` 中测试无问题(需要更新 AE2-UEL 版本至最新).

---

### 额外合成设定: 黑洞合成

独特的合成系统, 允许玩家创建 **微型奇点** 并且将物品投入事件视界, 转化为所需材料.

**注意**: 微型奇点只会维持 `300s`, 需要右键来开启合成, **不要离它太近!!!**

支持 **CraftTweaker** 修改配方:
```zenscript
import mods.ae2enhanced.BlackHole;
BlackHole.addRecipe(IItemStack output, IItemStack[] inputs);
// 示例
BlackHole.addRecipe(<minecraft:obsidian>, [<minecraft:stone> * 8, <minecraft:diamond>]);
BlackHole.removeRecipe("test_obsidian");
```

内置的配方名
```
id: "test_obsidian"
id: "stable_spacetime_manifold"
id: "differential_form_stabilizer"
id: "conformal_invariant_charge"
```
