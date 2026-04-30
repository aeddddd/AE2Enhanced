# AE2Enhanced

AE2Enhanced 是 **AE2 Unofficial Extended Life (AE2-UEL)** 的终局扩展模组, 为后期游戏引入了两个大型多方块结构:

- **超因果装配枢纽** —— 类似 LazyAE 大型分子装配室的巨型合成阵列, 以极高的并行和速度执行合成样板.
- **超维度仓储中枢** —— 突破 `long` 上限的无限容量存储结构, 支持物品、流体、气体(Mekanism)与源质(Thaumcraft), 数据持久化至外部文件, 避免存档膨胀.

---

## 超因果装配枢纽

基础运行参数为 **64 并行, 1 秒每次**, 相当于 `LazyAE` 大型分子装配室在不修改配置文件时的最强表现.

**需要强调, 装配枢纽并行不依赖 CPU 并行!**

安装全部 5 个并行升级后, 并行上限达到 `Long.MAX_VALUE`, 在绝大多数整合包中等价于无穷大(这甚至超过了 `1.12.2 AE` 终端的显示上限!).

速度升级允许将冷却从默认的 `20 ticks` 压缩至 `1 tick`, 合成不再成为工厂发展的瓶颈.

此外, 多方块提供了类似高版本 `EAEP` 装配矩阵上传核心的**自动上传模块**, 允许将编码好的合成样板自动上传到枢纽中, 同时具备重复检测功能, 重复样板不会二次上传.

---

## 超维度仓储中枢

一种 21 格平面多方块结构, 专为解决后期 AE2 网络存储容量瓶颈而设计.

- **BigInteger 级容量**: 单结构容量理论上无上限, 彻底绕过 `int`/`long` 限制
- **多类型兼容**: 原生支持物品、流体; 可选支持 Mekanism 气体与 Thaumcraft 源质(需安装对应模组)
- **外部文件持久化**: 数据存储于 `<world>/ae2enhanced/storage/<uuid>.dat`, 使用压缩 NBT + 原子写入, 存档大小与存储容量无关
- **安全模式**: 文件异常时自动进入只读状态, GUI 显示红色横幅, 防止数据损坏
- **第三方扩展**: 提供 `registerExternalAdapter()` API, 其他模组可接入自定义存储类型

结构核心为**超维度控制器**, 直接承载 AE2 网络节点; **ME 接口**仅作为网络接入点. 结构上方会渲染全息超立方体投影(可在配置中关闭或调整距离).

---

## 性能

### 装配枢纽
采取虚拟合成 + 真实合成的混合机制. 即使对于极大数目的下单, `mspt` 也不会受到明显影响, 对于绝大多数 AE 允许的下单均完成了适配.

> 额外补充
> 在 CRT 魔改合成配方时, 简单的 `.reuse` 并不能让 AE 知道这个物品不被消耗, 下单时仍会正常请求对应份数.

### 超维度仓储中枢
- **服务端零负担**: 所有存储操作基于内存中的 `ConcurrentHashMap`, 文件写入由独立后台线程异步执行, 主线程永不等待 I/O
- **增量刷新**: 仅在实际发生存取时通过 `IStorageGrid.postAlterationOfStoredItems` 通知 AE2 网络更新, 避免无意义的全量扫描
- **终端实时同步**: 物品完全取出后终端即时刷新, 反射双保险兜底

---

## 配置文件

位于 `config/ae2enhanced.cfg`, 提供以下可调参数:

| 分类 | 参数 | 说明 |
|---|---|---|
| Storage | `flushIntervalSeconds` | 存储文件自动刷盘间隔 (秒, 默认 5) |
| Render | `enableHyperdimensionalRenderer` | 是否启用全息投影 (默认 true) |
| Render | `renderDistance` | 最大渲染距离 (方块, 默认 64) |
| BlackHole | `damageMode` | 黑洞伤害模式: ALL / NON_CREATIVE / NONE (默认 ALL) |

---

## 需求

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 8.9+

可选前置:
- **Mekanism + Mekanism Gas Library** —— 启用气体存储支持
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
