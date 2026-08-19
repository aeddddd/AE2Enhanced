---
navigation:
  title: 第三方集成
  parent: index.md
  position: 60
  icon: emc_interface
item_ids: [emc_interface]
---

# 第三方集成

所有集成均为可选: 仅在安装对应模组时加载相关内容.

## 神秘时代

- 源质假物品, 通用总线的源质支持, 以及[超维度仓储中枢](machines/storage-nexus.md)的源质通道 (需要神秘时代 + 神秘能源).
- 中枢 ME 接口的注魔矩阵与坩埚处理器 (坩埚默认每次合成后清空源质).

## 通用机械

- 气体假物品, 通用总线与仓储中枢的气体支持 (需要通用机械 + MekanismEnergistics, 且未安装 ae2fc).

## 植物魔法

- 网络 Mana 存储通道, Mana 假物品, [区块魔力节点](devices/chunk-nodes.md), [网络访问节点](devices/network-access-node.md)的 Mana 桥接, 以及中枢 ME 接口的魔力池, 精灵传送门, 泰拉钢平台, 符文祭坛与花瓣药剂台处理器.
- 若 Botania Applied 已提供 Mana 通道, 则直接复用而不重复注册.

## 星辉魔法

- 网络星能存储通道, 星能假物品, 网络访问节点的星能桥接, 以及中枢 ME 接口的祭坛处理器.

## 等价交换

- <ItemLink id="emc_interface" /> **EMC 接口**把绑定玩家的转化知识暴露为 ME 网络的物品来源 (单向: 从 EMC 余额生成网络物品, 不接受反向注入).
- 放置时绑定放置者; **Shift + 右键** GUI 标题重新绑定 (限所有者或权限等级 2). 所有者离线时也可提取 (EMC 从存档数据中扣减).
- 2040 格白名单 (20 页 x 102) 限制暴露的物品; 白名单为空时不暴露任何物品.
- 待机功耗 5 AE; 需要配置 `EMCInterface.enabled` (默认开).

## 龙之进化

- 中枢 ME 接口的聚合核心处理器, [能源存储总线](buses/energy-storage-bus.md)的能量核心支持, 网络访问节点的创造 RF 源增益, 以及[先进 ME 工具](tools/omni-tool.md)的混沌核心升级.

## 其他

- **AE2 Fluid Crafting (ae2fc)**: 安装后 AE2E 自有的流体/气体假物品体系自动停用; 用 `/ae2e migratefluids` 迁移存量.
- **Flux Applied**: 存在时复用其能量通道.
- **存储抽屉**: 抽屉网络可包装为网络监视器.
- **JEI**: 黑洞合成配方分类, 全能终端配方转移, 终端搜索同步 (按 **F**), 智能样板接口的 ghost 物品拖拽.
- **CraftTweaker**: 黑洞配方, 奇点仪式/燃料与装配枢纽升级 API (见[黑洞合成](systems/black-hole.md)).
