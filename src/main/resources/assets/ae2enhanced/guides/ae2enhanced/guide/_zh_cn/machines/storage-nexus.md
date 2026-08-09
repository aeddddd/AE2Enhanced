---
navigation:
  title: 超维度仓储中枢
  parent: machines.md
  position: 20
  icon: hyperdimensional_controller
item_ids: [hyperdimensional_controller, hyperdimensional_me_interface, hyperdimensional_casing, hyperdimensional_singularity_core]
---

# 超维度仓储中枢

<ItemLink id="hyperdimensional_controller" /> **超维度仓储中枢控制器**向 ME 网络暴露基于文件的存储通道. 物品数量以 BigInteger 计数, 没有理论上限.

## 结构

全平面单层结构:

- 1 个超维度仓储中枢控制器
- 1 个 <ItemLink id="hyperdimensional_me_interface" /> 仓储中枢 ME 接口 (网络接入点, 控制器自身不接线缆)
- 5 个 <ItemLink id="hyperdimensional_singularity_core" /> 仓储中枢裸奇点核心
- 14 个 <ItemLink id="hyperdimensional_casing" /> 仓储中枢度规稳定锚

## 存储通道

- 恒可用: 物品, 流体, 能量 (RF), Mana, 星能.
- 条件可用: 气体 (通用机械) 与源质 (神秘时代), 取决于对应模组是否安装.
- 若 Flux Applied 或 Botania Applied 已提供能量/Mana 通道, 中枢会改用外部通道.

## 持久化

- 每个中枢由 UUID (nexusId) 标识. 数据存放于 `<世界目录>/ae2enhanced/storage/<nexusId>/`, 文件为 `items.bin`, `fluids.bin`, `energy.bin`, `mana.bin`, `starlight.bin`, 以及可选的 `gases.bin` 与 `essentias.bin`.
- 脏数据按定时器增量落盘 (配置 `storage.flushIntervalSeconds`, 默认 5 秒).
- 拆除控制器或整个结构**不会**删除数据; 携带 nexusId 的控制器物品可以重新访问同一存储.
- 管理命令: `/ae2e recoverhd list` 与 `/ae2e recoverhd <uuid>` 可发放绑定指定 nexusId 的控制器物品 (见[命令与配置](systems/commands-config.md)).
- 统计数字以 BigInteger 格式化显示 (K/M/G/T/P/E/Z/Y, 超过 1e27 用科学计数法).
