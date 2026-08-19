---
navigation:
  title: 区块节点
  parent: devices.md
  position: 40
  icon: chunk_power_node
item_ids: [chunk_power_node, compressed_chunk_power_node, chunk_mana_node, compressed_chunk_mana_node]
---

# 区块节点

区块节点直接从 ME 网络取出相应资源, 为区块范围内所有兼容机器供能.

## 供电节点

- <ItemLink id="chunk_power_node" /> **区块供电节点**: 每 20 ticks 向本区块所有能够接受 FE 的方块推送 FE. 未用完的能量返还网络. 
- <ItemLink id="compressed_chunk_power_node" /> **压缩区块供电节点**: 相同行为, 范围为 3x3 共 9 个区块.
- 占用 1 个频道, 待机功耗 32 AE. 不会给网络访问节点供电.
- **右键**打开 GUI: 实时查看网络状态、目标数量与每 tick 输出, 并可排除 (解除绑定) 或恢复单个供电目标.
- **Shift + 右键**高亮显示供电目标, 持续 100 ticks.
- 区块供电节点默认传输速率是 2.1G/t 特别的, 它会尝试绕过一些模组的能量输入限制
  - 末影接口: 越过能量输入限制
  - 热力膨胀: 越过能量输入限制
  - 龙之研究: 直接使用网络中存储能量, 达到超过 int 的速度.
  - 核电工艺: 越过能量输入限制

注意, 默认会给能量垃圾桶推送能量!
## 魔力节点 (需要植物魔法)

- <ItemLink id="chunk_mana_node" /> **区块魔力节点**: 从网络中提取Mana, 为区块内的植物魔法魔力接收设备供魔.
- <ItemLink id="compressed_chunk_mana_node" /> **压缩区块魔力节点**: 相同行为, 范围为 3x3 共 9 个区块.
- 对于魔力附魔台, 提供了绕过上限的供能方式.
