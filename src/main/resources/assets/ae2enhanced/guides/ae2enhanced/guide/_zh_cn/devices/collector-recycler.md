---
navigation:
  title: 收集器与回收节点
  parent: devices.md
  position: 50
  icon: advanced_me_collector
item_ids: [advanced_me_collector, me_network_recycler]
---

# 收集器与回收节点

## 先进 ME 收集器

<ItemLink id="advanced_me_collector" /> **先进 ME 收集器**在掉落物生成之前拦截, 直接回收到 ME 网络.

- 63 个过滤槽: 默认 18 个可用, 每张容量卡 +9. 过滤为空时收集全部物品.
- 5 个升级槽; 内部缓冲 27 槽 x 4096.
- 范围: 从 2 (5x5x5) 到 7 (15x15x15), 配置 `collector.defaultRange` / `collector.maxRange`.
- 待机功耗 16 AE (`collector.idlePower`).
- 支持红石控制, 模糊模式与仅合成设置.

## ME 网络回收节点

<ItemLink id="me_network_recycler" /> **ME 网络回收节点**把已绑定机器的产物直接回收进网络.

- 用[通用内存卡](tools/memory-card.md)绑定目标: 先选取机器, 再右键回收节点批量绑定. Alt + 右键清空绑定.
- 最多绑定 1024 个目标 (`recycler.maxTargets`); 支持远程与跨维度回收.
- `recycler.forceHyperdimensionalStorage` (默认开) 将回收物写入存储; `recycler.machineOutputRedirect` (默认开) 可在产物进入容器前重定向.
- 待机功耗 32 AE.
