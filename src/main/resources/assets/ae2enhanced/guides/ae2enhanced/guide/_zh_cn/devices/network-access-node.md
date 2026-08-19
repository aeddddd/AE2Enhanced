---
navigation:
  title: 网络访问节点
  parent: devices.md
  position: 30
  icon: network_access_node
item_ids: [network_access_node]
---

# 网络访问节点

<ItemLink id="network_access_node" /> **网络访问节点**允许将相邻方块存储的资源与 ME 网络进行交互. 支持三种资源, 各有输入与输出两种模式.

## 用法

- **Shift + 右键**节点切换输入输出模式.
- RF: 在网络 RF 通道与相邻 RF 源之间转移 RF. 传输上限在配置文件 `energy.rfAccessNodeMaxTransfer` (默认不限).
- Mana (需要植物魔法): 在网络 Mana 通道与相邻魔力池之间转移 Mana. 传输上限在配置文件 `mana.manaAccessNodeMaxTransfer` (默认 10000).
- 星能 (需要星辉魔法): 在网络与相邻祭坛之间转移星能. 输入上限 `starlight.starlightAccessNodeMaxInput` (默认 100), 输出上限 `starlight.starlightAccessNodeMaxOutput` (默认 1000). 输入仅在夜间 (13000-23000) 且祭坛能见到天空时生效.

## 创造 RF 能量源调整

相邻存在龙之进化创造 RF 源时, 节点会向网络注入能量. 由配置文件中的 `energy.creativeRfSourceBoostEnabled` (默认开) 与 `energy.creativeRfSourceBoostAmount` (默认 1.0E12) 控制.
