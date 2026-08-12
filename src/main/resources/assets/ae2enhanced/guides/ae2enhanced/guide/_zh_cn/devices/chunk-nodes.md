---
navigation:
  title: 区块节点
  parent: devices.md
  position: 40
  icon: chunk_power_node
item_ids: [chunk_power_node, compressed_chunk_power_node, chunk_mana_node, compressed_chunk_mana_node]
---

# 区块节点

区块节点从 ME 网络取能, 为区块范围内所有兼容机器供能.

## 供电节点

- <ItemLink id="chunk_power_node" /> **区块供电节点**: 每 20 tick 扫描所在区块, 向所有接受 Forge Energy 的方块推送 FE. 未用完的能量返还网络.
- <ItemLink id="compressed_chunk_power_node" /> **压缩区块供电节点**: 相同行为, 范围为 3x3 共 9 个区块.
- 占用 1 个频道, 待机功耗 32 AE. 网络访问节点在黑名单中 (不会被供电).
- **右键**打开 GUI: 实时查看网络状态、目标数量与每 tick 输出, 并可排除 (解除绑定) 或恢复单个供电目标.
- **Shift + 右键**高亮显示供电目标, 持续 100 tick.

## 魔力节点 (需要植物魔法)

- <ItemLink id="chunk_mana_node" /> **区块魔力节点**: 从网络 Mana 通道提取, 为区块内的植物魔法魔力接收设备供魔.
- <ItemLink id="compressed_chunk_mana_node" /> **压缩区块魔力节点**: 相同行为, 范围为 3x3 共 9 个区块.
- 魔力虚空与产能花不会成为供魔目标.
