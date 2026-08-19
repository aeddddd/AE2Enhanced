---
navigation:
  title: 通用总线
  parent: buses.md
  position: 20
  icon: part_universal_import_bus
item_ids: [part_universal_import_bus, part_universal_export_bus]
---

# 通用总线

一个总线处理所有资源类型: 物品, 流体, 气体 (通用机械) 与源质 (神秘时代). 类似高版本ae2的体验.

## 通用输入总线

<ItemLink id="part_universal_import_bus" /> **通用输入总线**把资源从相邻容器拉入网络.

- 物品: 每次操作最多 min(2^速度卡数, 64) 个.
- 流体与气体: 每次操作最多 1000 mB.
- 过滤为空时无差别导入全部内容.

## 通用输出总线

<ItemLink id="part_universal_export_bus" /> **通用输出总线**把配置的资源从网络推入相邻容器.

- 物品: 每次操作最多 min(2^速度卡数, 64) 个.
- 流体: 每次操作 min(2^速度卡数 x 100, 8000) mB.
- 模糊卡使物品导出使用模糊匹配.
- 空过滤下会输出全部, 请不要直接对着垃圾桶使用.


