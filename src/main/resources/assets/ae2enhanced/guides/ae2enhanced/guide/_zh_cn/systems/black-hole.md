---
navigation:
  title: 黑洞合成
  parent: systems.md
  position: 20
  icon: constrained_micro_singularity
item_ids: [constrained_micro_singularity, singularity_constrictor, stable_spacetime_manifold, differential_form_stabilizer, conformal_invariant_charge]
---

# 黑洞合成

以奇点为核心的合成系统: 把原料投入事件视界来执行配方.

## 世界合成

1. 在 ME 控制器附近的地面上丢出 **64 个 AE2 奇点**与 **4 个下界之星**.
2. 手持 **1 个下界之星**右键 ME 控制器.
3. 生成 <ItemLink id="constrained_micro_singularity" /> **微型奇点**, 存在时间为 6000 tick (300 秒). 世界合成消耗消耗目标周围 5x5x5 区域内的对应物品.

自定义仪式可用 CraftTweaker 添加 (`mods.ae2enhanced.SingularityRitual`).

## 微型奇点

- 不可破坏, 发光, 3x3x3 事件视界内的生物受到 "spacetime" 伤害 (模式由 `blackHole.damageMode` 控制: ALL / NON_CREATIVE / NONE).
- **右键**执行黑洞合成: 3x3x3 区域内匹配的物品实体被消耗, 产物从奇点上方弹出 (手动激活时不匹配的物品不会被销毁).
- 喂食可延长存在时间:
  - <ItemLink id="stable_spacetime_manifold" /> 稳态时空流形: +12000 tick (600 秒).
  - <ItemLink id="differential_form_stabilizer" /> 微分形式稳定单元: +48000 tick (2400 秒).
  - <ItemLink id="conformal_invariant_charge" /> 共形不变荷: 使其永久存在.
- 存在时间耗尽后奇点坍缩消失.
- 手持 <ItemLink id="singularity_constrictor" /> **奇点约束器**右键可将奇点约束为物品形态 (倒计时以 1/30 速度流逝). 扔出物品落地静置后恢复为方块形态, 并在原地返还空的约束器.

## 物质炮弹药

共形不变荷被注册为物质炮弹药, 使用其作为弹药会延长射程至128格,同时具有穿透效果.
