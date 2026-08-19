---
navigation:
  title: 超因果装配枢纽
  parent: machines.md
  position: 10
  icon: assembly_controller
item_ids: [assembly_controller, assembly_me_interface, assembly_casing, assembly_inner_wall, assembly_stabilizer, upgrade_card]
---

# 超因果装配枢纽

<ItemLink id="assembly_controller" /> **装配枢纽控制器**是大规模并行自动合成多方块的核心. 每个样板槽独立执行任务, 可同时运行极大量合成任务.

## 结构

完整结构共 344 个方块:

- 1 个装配枢纽控制器
- 3 个 <ItemLink id="assembly_me_interface" /> 装配枢纽 ME 接口 (网络接入点)
- 180 个 <ItemLink id="assembly_casing" /> 装配枢纽外壳
- 128 个 <ItemLink id="assembly_inner_wall" /> 装配枢纽内壁
- 32 个 <ItemLink id="assembly_stabilizer" /> 装配枢纽稳定器


## 样板槽

- 每页 102 槽 (17 列 x 6 行), 默认 5 页.
- 每张**维度折叠模块** (容量) 增加 5 页, 上限 30 页.
- 总数上限 2880 槽, 仅接受合成样板.

## 升级卡

控制器有 6 个升级槽, 槽位序号与卡片类型一一对应. <ItemLink id="upgrade_card" /> 升级卡:

- **时间折叠模块** (并行, 最多 5 张): 批量合成并行上限. 0 张 = 64, 每张乘 32, 封顶 67,108,864; 装 5 张时 提供Long.MAX_VALUE的并行上限.
- **时空膨胀模块** (速度, 最多 5 张): 批量合成冷却从 20 tick 起, 每张减半, 最低 1 tick.
- **能量优化模块** (效率): 降低枢纽能耗.
- **维度折叠模块** (容量): 见上方样板槽说明.
- **自动上传模块** (最多 1 张): 在[全能终端](tools/omni-terminal.md)中编码完成的合成样板自动上传到最近的已组装枢纽, 处理样板不上传.
- **扩展模块 (预留)**: 暂无功能.

可通过 CraftTweaker 注册自定义并行/速度升级卡 (见[黑洞合成](systems/black-hole.md)).

## 黑洞溢出

枢纽借助稳定黑洞合成时, 3x3x3 事件视界内的物品会被吸入作为原料, 范围内生物被秒杀. 内部存在5个溢出缓冲区, 溢出会产生一次不会破坏方块的爆炸, 同时清空缓存.
