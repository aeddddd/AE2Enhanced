---
navigation:
  title: 超因果计算核心
  parent: machines.md
  position: 30
  icon: computation_core
item_ids: [computation_core, constant_tensor_field_casing, constant_spinor_field_casing, causal_anchor_core, super_crafting_interface]
---

# 超因果计算核心

<ItemLink id="computation_core" /> **超因果计算核心**是多方块合成 CPU: 通过它创建的合成集群拥有 Long.MAX_VALUE 的合成存储容量与极大的并行能力.

## 结构

- 1 个超因果计算核心 (控制器)
- 1 个 <ItemLink id="super_crafting_interface" /> 超因果合成接口 (网络接入点)
- 144 个 <ItemLink id="constant_tensor_field_casing" /> 恒定张量场外壳
- 366 个 <ItemLink id="constant_spinor_field_casing" /> 恒定旋量场外壳
- 343 个 <ItemLink id="causal_anchor_core" /> 因果锚定核心

## 行为

- 每个合成集群创建时带有 `Long.MAX_VALUE` 可用存储容量与 16384 并行.
- 并行上限由配置 `crafting.maxParallel` 决定 (默认 16384).
- 自动拆分机制会始终保持一个空闲 CPU 集群供新任务使用.
- 特殊合成计划 (自引用或循环配方的闭式求解) 在此核心上执行; 可用 `/ae2e specialcrafting` 或配置 `crafting.specialCrafting` 开关 (见[命令与配置](systems/commands-config.md)).
