# AE2Enhanced

AE2Enhanced 是 **AE2 Unofficial Extended Life (AE2-UEL)** 的终局扩展模组, 为后期游戏引入了多个大型多方块结构以及一系列实用物品:

模组反馈和交流QQ群:529037440


*当前发布版本:1.5.2*
>目前关于修改和实用物品的添加仍处于dev开发中,当完成开发后会发布Release

## 核心物品

### 全能终端(Omni 终端)
一款现代、通用、多功能的终端.

- 支持编码 **81 输入**与 **27 输出**的样板.
- 在同一界面中同时进行样板编码与物品合成

### 中枢 ME 接口 *(1.5.x 新增!)*

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
| **神秘时代 6 (Thaumcraft 6)** | 注魔祭坛 |
| **星辉魔法 (Astral Sorcery)** | 各个等级的星辉祭坛 |
| **实用拓展 (Actually Additions)** | 充能台 |
| **合成拓展 (Extended Crafting)** | 聚合合成、各类工作台、量子压缩机 |
| **植物魔法 (Botania)** | 花药台、魔力池、精灵门、符文祭坛、泰拉凝聚板 |
| **巫师之路 (Bewitchment)** | 女巫釜锅、纺车、蒸馏塔 |
| **龙之研究 (Draconic Evolution)** | 聚合注入器 |

(所有适配不需要玩家手动启动, 会自动开始执行合成, 包括注魔祭坛和星辉, 如果合成会残留例如空桶之类的返还物品, 也不需要担心, 中枢ME接口会自动回收)

使用通用内存卡进行绑定

## 多方块部分

- **超因果装配枢纽** —— 类似 LazyAE 大型分子装配室的巨型合成阵列, 以极高的并行(最高并行上限达到 `Long.MAX_VALUE`)执行合成样板,并且额外提供样板自动上传模块.
- **超维度仓储中枢** —— 达到 `BigInteger` 上限的无限容量存储结构, 支持物品、流体、气体(Mekanism)与源质(Thaumcraft), 数据持久化至外部文件, 避免存档膨胀, 并且不会因为存储大量NBT造成的卡顿和溢出问题.
- **超因果计算核心** —— 超级合成 CPU, 拥有 `Long.MAX_VALUE` 合成存储容量和 16384 并行加速器槽位, 支持动态虚拟 CPU 集群池实现多订单并发.

---



## 额外添加的实用物品
- **通用输入总线,输出总线** 允许使用单个总线完成所有类型的IO操作
- **库存总线** 可以维持目标容器中物品的数量
- 无线频道发生器, 允许把自身频道发送出去
- 频道接收卡, 允许接收来自无线频道发生器的频道(不限维度和距离,配置可调)
- ME接口镜像, 允许镜像ME接口
- 通用内存卡, 具有普通内存卡的功能, 额外允许复制机器配置和升级卡, 允许批量更改, 允许连接ME接口镜像
- ME发送节点, 允许批量无线发送物品(类似输出总线), 使用通用内存卡来设置范围
- ME收取节点, ME发送节点的对应版本
- 并发卡, 允许增加AE的IO设备的并行
- ME收集节点, 允许从世界中收集物品到ME网络

---
## 对AE2-uel的修改
- 支持下单超过`Int.MAX_VALUE`的物品
- 合成计划优化, 把高版本的合成计划中缺失物品置顶功能带回了`1.12.2`
- 修改创造物品元件的存储物品数量为`Long.MAX_VALUE`
- **更现代的ME终端**: 现在你可以丢掉流体终端,气体终端甚至源质终端, 现在所有的东西都会统一显示在一个终端中, 就像高版本那样(感谢 [AE2fc-rework-unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial/tree/main) 的部分代码,我参考了部分实现方式, 并且保证了相关兼容性) 
- 对于其他Qol改进(例如合成置顶), 可以使用 [RandomComplement](https://github.com/Circulate233/RandomComplement) 这个mod提供了许多高版本AE2功能

---
## 性能

### 装配枢纽
采取虚拟合成 + 真实合成的混合机制. 即使对于极大数目的下单, `mspt` 也不会受到明显影响, 对于绝大多数 AE 允许的下单均完成了适配.



### 超维度仓储中枢
采取异步加增量刷新模式, 使用外部文件存储数据, 从根本上解决了NBT溢出和卡顿的问题, 并且支持极高的存储空间.

使用不同于AE2-uel本身的全增量刷新机制, 并且定时进行全额检查确保正确性. 并且额外支持了配置文件, 允许玩家更改定时检查的频率(*默认情况下是200ticks一次*).

---

## 前置相关

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+

**硬依赖**
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 8.9+

**可选**
- **ctm** 提供连接纹理
- **Mekanism + MekanismEnergistics** 启用气体存储支持
- **Thaumcraft + Thaumic Energistics** 启用源质存储支持
- **CraftTweak** 提供合成修改方法
- **JEI/HEI** 相关支持
- 对于大量mod存在特殊适配:(神秘6,星辉魔法,血魔法,植物魔法,巫师之路,实用拓展,合成拓展,mek,热力配置,末影接口,...)

**兼容**
- **AE2fc-rework-unofficial** 不重复注册假流体


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


> 额外对于装配枢纽的补充
> 在 CRT 魔改合成配方时, 简单的 `.reuse` 并不能让 AE 知道这个物品不被消耗, 下单时仍会正常请求对应份数.
