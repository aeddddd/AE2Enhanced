---
navigation:
  title: 黑洞合成
  parent: systems.md
  position: 20
  icon: micro_singularity
item_ids: [micro_singularity, stable_spacetime_manifold, differential_form_stabilizer, conformal_invariant_charge]
---

# 黑洞合成

以奇点为核心的终局合成系统: 把原料投入事件视界来执行配方.

## 世界合成

1. 在 ME 控制器附近的地面上丢出 **64 个 AE2 奇点**与 **4 个下界之星**.
2. 手持 **1 个下界之星**右键 ME 控制器.
3. 生成 <ItemLink id="micro_singularity" /> **微型奇点**, 存在时间为 6000 tick (300 秒). 世界合成消耗消耗目标周围 5x5x5 区域内的对应物品.

自定义仪式可用 CraftTweaker 添加 (`mods.ae2enhanced.SingularityRitual`).

## 微型奇点

- 不可破坏, 发光, 3x3x3 事件视界内的生物受到 "spacetime" 伤害 (模式由 `blackHole.damageMode` 控制: ALL / NON_CREATIVE / NONE).
- **右键**执行黑洞合成: 3x3x3 区域内匹配的物品实体被消耗, 产物从奇点上方弹出 (手动激活时不匹配的物品不会被销毁).
- 喂食可延长存在时间:
  - <ItemLink id="stable_spacetime_manifold" /> 稳态时空流形: +12000 tick (600 秒).
  - <ItemLink id="differential_form_stabilizer" /> 微分形式稳定单元: +48000 tick (2400 秒).
  - <ItemLink id="conformal_invariant_charge" /> 共形不变荷: 使其永久存在.
- 存在时间耗尽后奇点坍缩消失.

## 内置配方 (投入事件视界)


存在魔改情况下以实际情况为准.
- 16 个 16384k 空间组件 + 64 个奇点 = 稳态时空流形.
- 128 个奇点 + 16 个下界之星 = 微分形式稳定单元.
- 16 个稳态时空流形 + 16 个微分形式稳定单元 = 共形不变荷.
- 64 个空白样板 = [智能空白样板](devices/smart-pattern.md).
- 1024 个加速卡 + 1024 个容量卡 + 64 个奇点 = [虚拟并行卡](devices/central-interface.md)等级 1.
- 16 张某等级虚拟并行卡 = 下一等级 (默认最高合成到等级 6).

## CraftTweaker

- `mods.ae2enhanced.BlackHole.addRecipe(output, inputs)` / `removeRecipe(id)`.
- `mods.ae2enhanced.SingularityRitual.addRecipe(id, droppedInputs, heldItem, targetBlock, lifetimeTicks)`.
- `mods.ae2enhanced.SingularityFuel.addFuel(id, item, ticks)` / `addPermanentFuel(id, item)`.
- `mods.ae2enhanced.AssemblyHub.registerParallelUpgrade(card, maxStack, values)` / `registerSpeedUpgrade(...)`.

## 物质炮弹药

共形不变荷被注册为物质炮弹药, 权重 1E8 (约 5,000,000 点伤害). 使用其作为弹药会延长射程至128格,同时具有穿透效果.
