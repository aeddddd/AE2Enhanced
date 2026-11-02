---
navigation:
  title: 全能终端
  parent: tools.md
  position: 10
  icon: omni_wireless_terminal_ii
item_ids: [omni_wireless_terminal, omni_wireless_terminal_ii, omni_upgrade_card]
---

# 全能无线终端 II

<ItemLink id="omni_wireless_terminal_ii" /> **全能无线终端 II** 是采用可拼接界面的耗电无线终端。使用前需在 ME 安全终端中绑定并保持电量充足，也可放入 Baubles 饰品栏。

## 面板与布局

- 物品仓库、合成、合成管理、接口终端、样板编码、存储、升级、玩家背包与 View Cell 均为独立面板。
- 面板可拖动、拼接、浮动、关闭和重新打开；仓库列数随面板宽度变化。拖动右下角调整大小，按住 Shift 拖动则保持宽高比。
- 布局编辑器支持撤销、重置、三个预设槽，以及布局导入、导出和分享。
- 布局保存在终端物品的 `omni_layout` NBT 中，并由服务端校验。
- 本地兜底与预设按终端 `storageId` 隔离，新终端不会继承另一把终端的布局或活动预设。

## 合成与编码

- 从网络提取物品并使用 3×3 合成格。
- 接口终端复用 AE2 原生供应器列表、样板同步与槽位操作，不使用平行协议。
- 编码合成与处理样板，支持 JEI/HEI 配方转移。
- 搜索与仓库分页由服务端同步，不同客户端拥有独立查询会话。

## 旧版终端

旧版 <ItemLink id="omni_wireless_terminal" /> 已弃用但仍可使用。原主配方现在产出终端 II。通过旧→新转换配方可保留网络绑定、`storageId`、库存、升级和布局 NBT。

## 升级与搜索

终端保留升级槽与 View Cell 过滤能力。默认按 **Shift+E** 打开。搜索框可与 JEI/HEI 搜索同步；悬停物品按 **F** 可复制显示名。
