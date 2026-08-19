---
navigation:
  title: 命令与配置
  parent: systems.md
  position: 30
  icon: minecraft:command_block
---

# 命令与配置

## /ae2e 命令

主命令 `/ae2enhanced`, 别名 `/ae2e`, 权限等级 2:

- `/ae2e channels <enable|disable|status>`: 开关 AE2 频道检查.
- `/ae2e fastpathing <enable|disable|status>`: 实验性 O(N) 频道寻路.
- `/ae2e specialcrafting <enable|disable|status>`: 特殊合成计划 (自引用 / 循环配方), 只能在超因果计算核心上执行.
- `/ae2e recoverhd list` / `/ae2e recoverhd <uuid>`: 列出超维度存储 UUID / 获取绑定该 UUID 的控制器.
- `/ae2e migratefluids`: 把 AE2E 流体假物品迁移为 ae2fc 格式 (已弃用,仅用于旧存档迁移).
- `/ae2e pd <list|info|delete|tp|invite|kick|setperm>`: [个人维度](systems/personal-dimension.md)管理.
- `/ae2e help`: 显示帮助.

## 按键绑定

以下均为默认情况下绑定的按键:

- **F**: 把 JEI 悬停物品的名称填入终端搜索框.
- **Shift+E**: 打开全能终端.
- **H**: 切换高级磁引卡模式.
- **N / Shift+N / Ctrl+N / C**: 先进 ME 工具的模式 / 精准采集 / 掉落模式 / 配置 GUI.
- **G** (游戏内): ME 放置工具径向菜单.
- **G** (GUI 中悬停物品): 按住打开对应指南页.

## 配置 (ae2enhanced.cfg)

- `BlackHole`: `damageMode` (ALL / NON_CREATIVE / NONE).
- `Crafting`: `maxParallel` 16384, `maxActiveOrders` 8, `specialCrafting`, `dagPlannerMode`.
- `WirelessChannel`: `crossDimension`, `maxRange`, `transmitterPower` 512, `extraUpgradeSlots` 2, `reconnectIntervalTicks` 100.
- `Storage`: `flushIntervalSeconds` 5, `monitorFullScanIntervalTicks` 200.
- `Collector` / `Recycler`: 范围, 功耗与目标上限.
- `CentralInterface`: 虚拟批量的超时, 冷却与能耗.
- `Energy` / `Mana` / `Starlight`: 访问节点传输上限与创造 RF 增益.
- `OmniTool`: 瞬移距离, 攻击伤害, 升级开关.
- `SmartPattern`: `maxRecipes` 256, `blacklist`.
- `PersonalDimension`: `presetPath`, `floorY` 64, `entryY` 65.
- `EMCInterface`: `enabled`, `idlePower` 5.
- `Guide`: `enabled`, `theme` (vscode-dark / github-light / dracula / nord).
