---
navigation:
  title: 智能样板
  parent: devices.md
  position: 60
  icon: smart_pattern_interface
item_ids: [smart_pattern_interface, smart_blank_pattern, smart_pattern]
---

# 智能样板

智能样板把多个配方打包进一个样板物品, 并针对特定机器编码.

## 流程

1. 通过[黑洞合成](systems/black-hole.md)获得 <ItemLink id="smart_blank_pattern" /> **智能空白样板** . ME 接口会忽略未编码的空白样板.
2. 在 <ItemLink id="smart_pattern_interface" /> **智能样板接口**的 GUI 中编码. 可以从 JEI 拖拽物品到接口中选择配方.
3. 编码产物为 <ItemLink id="smart_pattern" /> **智能样板**, 物品本身只存一个 id, 配方数据保存在世界存档中. 内部的单条配方可以逐个禁用.

## 细节

- 一张智能样板可容纳大量配方 (配置 `smartPattern.maxRecipes`, 默认 256, 上限 4096).
- 可将机器列入黑名单禁止使用智能样板 (配置 `smartPattern.blacklist`).
- 样板展开发生在接口构建合成列表时, 网络看到的是样板中的各条独立配方.
