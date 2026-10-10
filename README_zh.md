# AE2Enhanced

AE2Enhanced 是 **AE2 Unofficial Extended Life (AE2-UEL)** 的终局扩展模组, 为后期游戏引入了多个大型多方块结构、强大的中枢自动化接口以及一系列实用物品:

模组反馈和交流QQ群:529037440

*当前发布版本:1.7.9.0*

## 核心物品

### 全能无线终端

集物品浏览、合成与样板编码于一体的通用无线终端。

> **注意: 全能无线终端正在进行完全重做。** 此处刻意不写细节, 以免过期。

### 中枢 ME 接口

一种高级 ME 接口,可远程绑定至目标方块实体,实现真正的物理并行与外部合成设备的原生自动化.

中枢ME接口的功能**非常强大**, 其允许:
- **无限制且不占用CPU**的一对多并行发配(就像样板P2P那样), 
- 默认强制保持主产物返回的模式. 
- 接口允许**自动回收**材料(*中枢ME接口会尝试一次性回收全部产物 !*)
- 接口允许**远程绑定** — 可在任意距离链接至目标方块实体
- 接口**不限制**绑定目标的数量
- **物理并行** — 绑定多个相同设备以扩展吞吐量

除此之外, 还有一个非常强大的功能:

中枢ME接口允许根据绑定的方块实体(Tile)不同, **自动适配**并且完成复杂的合成, 就像封包合成那样, 但是没有复杂的封包机解包机, 你只需要绑定目标方块实体, 刻下配方到样板中, 就可以轻松完成自动化!

(**注意,存在主材+辅材的配方需要保证主材位于样板第一位**)

**支持的模组与设备：**

| 模组 | 支持的设备 |
|------|-----------|
| **血魔法 (Blood Magic)** | 炼金术桌、狱火锻炉、血之祭坛 |
| **神秘时代 6 (Thaumcraft 6)** | 注魔矩阵、坩埚 |
| **星辉魔法 (Astral Sorcery)** | 各个等级的星辉祭坛 |
| **实用拓展 (Actually Additions)** | 充能台（含展示台） |
| **合成拓展 (Extended Crafting)** | 聚合核心、各级工作台、量子压缩机、末影工作台 |
| **无尽贪婪 (Avaritia)** | 终焉工作台 |
| **植物魔法 (Botania)** | 花药台、魔力池、精灵门、符文祭坛、泰拉凝聚板 |
| **巫师之路 (Bewitchment)** | 女巫釜锅、纺车、蒸馏塔 |
| **龙之研究 (Draconic Evolution)** | 聚合核心（含注入器） |
| **末影接口 (Ender IO)** | 全部机器（按命名空间整体适配） |
| **热力膨胀 (Thermal Expansion)** | 全部机器（按命名空间整体适配） |
| **核电工艺 (NuclearCraft)** | 全部机器（Legacy 与 Overhauled 均支持） |
| **科技复兴 (Tech Reborn)** | 19 种机器（合金炉、工业高炉、工业研磨机等） |
| **通用兜底** | 任意暴露 `IItemHandler` 的方块 |

(所有适配不需要玩家手动启动, 会自动开始执行合成, 包括注魔矩阵和星辉, 如果合成会残留例如空桶之类的返还物品, 也不需要担心, 中枢ME接口会自动回收)

使用通用内存卡进行绑定

**虚拟并行卡（等级 1-8）**: 装入中枢 ME 接口后, 可将支持的目标切换为纯虚拟批量合成, 并行度从 8 到 `Long.MAX_VALUE`。对于没有物理交互空间的目标（工作台类设备）, 即使不装卡也会常驻虚拟执行。

### 智能样板接口

将绑定机器的 JEI 配方批量转换为**智能样板** —— 一条智能样板可容纳多条配方, 每条可单独启停/锁定, 支持 JEI `[+]` 填充、输入输出旋转与批量替换。它是纯手动配置终端, 不接入 ME 网络。

---

## 多方块部分

- **超因果装配枢纽** —— 类似 LazyAE2 大型分子装配室的巨型合成阵列（344 方块）, 以极高的并行(最高并行上限达到 `Long.MAX_VALUE`)执行合成样板,并且额外提供样板自动上传模块。六种升级模块: 时间折叠(并行)、时空膨胀(速度)、能量优化(效率)、维度折叠(容量)、自动上传、时线分歧。
- **超维度仓储中枢** —— 无限容量存储结构（21 方块）。数量统计使用 `HugeCount`（long 快路径 + BigInteger 溢出）, 支持物品、流体、气体(Mekanism)与源质(Thaumcraft), 数据持久化至外部文件（基础快照 + WAL 预写日志 + 空闲检查点）, 避免存档膨胀, 并且不会因为存储大量NBT造成的卡顿和溢出问题; 损坏时回退为只读安全模式。
- **超因果计算核心** —— 超级合成 CPU 结构（约 860 方块）, 托管原生 AE2 `CraftingCPUCluster` 集群池: 1 个常驻集群 + 按订单动态创建的并发集群, 空闲集群自动回收。

---

## 合成系统

### 黑洞合成

独特的合成系统, 允许玩家创建 **微型奇点** 并且将物品投入事件视界, 转化为所需材料.

**注意**: 微型奇点只会维持 `300s`, 需要右键来开启合成, **不要离它太近!!!**

支持 **CraftTweaker** 修改配方:
```zenscript
import mods.ae2enhanced.BlackHole;
BlackHole.addRecipe(IItemStack output, IItemStack[] inputs);
// 示例
BlackHole.addRecipe(<minecraft:obsidian>, [<minecraft:stone> * 8, <minecraft:diamond>]);
// 可通过 id 移除配方（内置或 CT 添加均可）:
BlackHole.removeRecipe("stable_spacetime_manifold");
```

内置 9 条配方（稳态时空流形、微分形式稳定单元、共形不变荷、智能空白样板、虚拟并行卡 T1 基底 + T2~T6 升级链）。

另包含: **微型奇点仪式**（投掷 64 个 AE2 奇点 + 4 个下界之星, 再手持下界之星右键 ME 控制器, 获得被约束的微型奇点）与**奇点燃料**（稳态时空流形/微分形式稳定单元可延长奇点寿命; 共形不变荷使其永久存在）。

### 奇点处理仓

后期单方块高并行处理机器。自动聚合以下配方来源: 黑洞合成、AE2 压印器（含多步合并链）、水晶充能/福鲁伊克斯聚合/水晶种子生长、AE2 磨粉机, 并支持 CraftTweaker 自定义:

```zenscript
import mods.ae2enhanced.SingularityChamber;
SingularityChamber.addRecipe(<minecraft:obsidian> * 2, [<minecraft:stone> * 64], 100);
SingularityChamber.removeRecipe("ct_...");
```

输入进入 long 级缓存槽（管道或 GUI 倒入）; 虚拟并行卡决定并行通道数, 相同配方聚合为单个任务; 加速卡缩短耗时。产物优先注入 ME 网络, 失败时进入输出缓冲。GUI 支持三种红石模式（忽略/高电平/低电平）。

---

## 个人维度

**个人维度核心**让每个玩家拥有自己的口袋维度:
- 右键空气打开配置 GUI; 右键方块传送; Shift+右键方块绑定进入点（仅在自己的维度内）。
- 可配置规则: 生物生成、天气/时间锁定、昼夜循环、飞行、移动速度、飞行惯性。
- 地板预设: `config/ae2enhanced/personal_dimension_floor.json`。
- 邀请制共享: 受邀玩家拥有完整的进入/建造/交互权限。

命令（无需 OP, 仅删除他人维度需要 OP）:
```
/ae2e pd list
/ae2e pd info <玩家>
/ae2e pd tp <玩家>
/ae2e pd invite <玩家>
/ae2e pd kick <玩家>
/ae2e pd delete [玩家]
```

---

## 能源与资源设备

- **网络访问节点** —— 合并了原 RF / Mana / 星能三种访问节点: 按相邻方块类型自动执行对应的 IO, 支持输入/输出模式切换。
- **区块供电节点 / 压缩区块供电节点** —— 从 ME 网络的 RF 存储通道取电, 为所在区块（或 3×3 区块）内所有 FE 设备供能, 消耗 1 频道。
- **区块魔力节点 / 压缩区块魔力节点** —— 从 ME 网络向所在区块（或 3×3 区块）内的植物魔法供魔设施供魔。
- **能源存储总线** —— 对标原版存储总线的 RF 版本: 将相邻 RF 容器（Forge Energy / 龙研扩展 RF）暴露给 ME 网络, 支持优先级/访问模式/白名单。
- **EMC 接口**（需要 ProjectE）—— 将绑定玩家的 EMC 余额（BigInteger 级）作为 ME 网络的物品源; EMC 达到上限后可解锁创造模式。

---

## 额外添加的实用物品

- **通用输入总线 / 通用输出总线** —— 允许使用单个总线完成所有类型（物品/流体/气体/源质）的 IO 操作。
- **库存维持总线** —— 可以维持目标容器中物品的数量。
- **无线频道发生器 + 频道接收卡** —— 发生器广播 32 个密集频道; 空白接收卡在其 GUI 中绑定后, 插入任意 AE 设备即可获得远程无线连接（不限距离、可跨维度, 配置可调）。
- **通用内存卡** —— 具有普通内存卡的功能, 额外允许复制机器配置和升级卡（跨模组: Mekanism、Ender IO、热力膨胀等）、批量更改、绑定中枢 ME 接口。
- **先进ME工具** —— 一个工具四种模式（通用/放置/旋转/传送）, 支持精准采集与时运、掉落物路由（普通/入包/入 ME 网络）、闪现位移与升级扩展（混沌核心、基岩破坏等）。按 C 打开配置 GUI。
- **ME 放置工具** —— 直接从 ME 网络放置方块/AE2 Part/伪装板/线缆; G 键径向菜单, 批量放置支持方向锁定。
- **先进 ME 收集器** —— 在掉落物生成为实体前注入 ME 网络, 带内部缓冲、63 槽过滤库存与可配置收集区域。
- **ME 网络回收节点** —— 管理远程/跨维度回收目标, 产物直接写入超维度仓储中枢。
- **显示面板（+ 显示屏边框）** —— 自由拼接的显示幕墙（2~16 × 2~9）, 展示 ME 网络统计, 5 种图表（折线/面积/增量/速率/堆叠）。
- **AE2Enhanced 指南** —— 内置 Markdown 指南书（28 页, 中英双语, 4 套主题）。悬停相关物品时**按住 G** 打开对应页面。

---

## LP 合成规划器

AE2Enhanced 用 **LP 规划器**（SCC 凝聚分层 + 三阶段字典序单纯形: 缺料最小 → 交付最大 → 执行次数最小）替代了 AE2 的原生合成计算, 可以正确处理原生计算器无法求解的成环/循环配方。含成环样板的计划会被特殊标记, 必须由超因果计算核心执行。

原生计算器仍作为兜底。通过 `Crafting.dagPlannerMode`（`OFF` / `FALLBACK` / `DEFAULT`）配置, 或运行时热切换:
```
/ae2e specialcrafting enable|disable|status
```

---

## 对AE2-uel的修改
- 支持下单超过`Int.MAX_VALUE`的物品
- 合成计划优化, 把高版本的合成计划中缺失物品置顶功能带回了`1.12.2`
- 修改创造物品元件的存储物品数量为`Long.MAX_VALUE`
- **F 键 JEI 搜索**: 从 JEI/HEI 直接搜索所有 AE2 终端（含书签覆盖层）, 按键可配置
- 流体/气体/源质终端支持（现代化的统一终端显示）请使用 **[AE2FluidCraft-Rework-Unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial)** —— AE2Enhanced 自有的假物品显示体系已弃用, 请改用该模组
- 对于其他Qol改进(例如合成置顶), 可以使用 [RandomComplement](https://github.com/Circulate233/RandomComplement) 这个mod提供了许多高版本AE2功能

---

## 性能

### 装配枢纽
采取虚拟合成 + 真实合成的混合机制. 即使对于极大数目的下单, `mspt` 也不会受到明显影响, 对于绝大多数 AE 允许的下单均完成了适配.

> **关于 CraftTweaker:** 在 CRT 魔改合成配方时, 简单的 `.reuse` 并不能让 AE 知道这个物品不被消耗, 下单时仍会正常请求对应份数.

### 超维度仓储中枢
采取异步加增量刷新模式, 使用外部文件存储数据（快照 + WAL）, 从根本上解决了NBT溢出和卡顿的问题, 并且支持极高的存储空间。定时进行全额检查确保正确性（可在配置中调整, 默认每 200 tick 一次）。

### LP 合成规划器
规划在带预算、可恢复的时间片任务中执行; 凝聚分层保证即使大型网络也能保持较小的 LP 模型。典型计划在默认预算内即可完成（可配置）。

内置诊断: `/ae2e perf`（TPS 与节点耗时）、`/ae2e diag check`（存储/个人维度完整性）、`/ae2e plan stats|running`。

---

## 前置相关

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+

**硬依赖**
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 10.6+

**推荐**
- **[AE2FluidCraft-Rework-Unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial)** —— 流体/气体/源质终端支持（AE2Enhanced 自有的假物品显示已弃用, 请改用此模组）

**可选**
- **ctm** 提供连接纹理
- **Mekanism + MekanismEnergistics** 启用气体存储支持
- **Thaumcraft + Thaumic Energistics** 启用源质存储支持
- **ProjectE** EMC 接口
- **Botania** 魔力频道与区块魔力节点
- **CraftTweaker** 提供合成修改方法
- **JEI/HEI** 配方转移、幽灵材料拖拽、F 键搜索
- 中枢 ME 接口设备适配: 神秘6、星辉魔法、血魔法、植物魔法、巫师之路、实用拓展、合成拓展、无尽贪婪、龙之研究、末影接口、热力膨胀、核电工艺、科技复兴

---

## 下载

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced)  
[Releases](https://github.com/aeddddd/AE2Enhanced/releases)

---

## 兼容性

兼容 **CleanroomMC**, 对其他 AE 附属保持良好兼容, 在大型整合包 `Divine Journey 2` 中测试无问题(需要更新 AE2-UEL 版本至最新).

---

## 许可证

**GPL-3.0**（见 [LICENSE](/LICENSE)）。

归属说明:
- 可停靠工作区布局引擎移植自 **MEST (ME Spliced Terminal)**, 基于 MIT 许可证使用（见 `licenses/MEST-LICENSE.txt`）。
- 统一终端实现参考了 [AE2FluidCraft-Rework-Unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial) 的部分实现方式, 并保证了相关兼容性。
- 可拼接终端的界面素材（窗口框、槽格、按钮、页签、滚动条滑块、合成箭头、编码区底、输入框与数量弹窗）取自
  **Applied Energistics 2** 19.2.17 版。这些纹理版权归 © 2020 Ridanisaurus Rid、© 2013-2020 AlgorithmX2 et al 所有,
  以 **CC BY-NC-SA 3.0** 授权（https://creativecommons.org/licenses/by-nc-sa/3.0/）。本项目为非商用项目、不提供付费获取,
  符合 NonCommercial 条款; 由这些文件演绎出的素材必须继续以同一许可证发布。逐文件清单与完整声明见 `licenses/AE2-ASSETS.txt`。
