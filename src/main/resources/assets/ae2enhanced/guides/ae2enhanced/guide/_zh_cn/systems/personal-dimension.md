---
navigation:
  title: 个人维度
  parent: systems.md
  position: 10
  icon: personal_dimension
item_ids: [personal_dimension, yellow_stripes_block_b]
---

# 个人维度

每名玩家都可以拥有一个私人的平坦单群系维度. <ItemLink id="personal_dimension" /> **个人维度核心**是进入它的钥匙.

## 进入与离开

- **右键方块**: 传送. 不在个人维度时记录返回点并进入 (首次使用时创建维度); 已在个人维度时传送回返回点.
- **Shift + 右键方块**: 绑定进入点 (只能在自己的维度内绑定).
- **右键空气**: 打开规则配置 GUI.
- 个人维度内无法设置重生点; 死亡后回到主世界.

## 规则 (按维度独立)

- 刷怪: 禁止生物自然生成 (默认关).
- 锁定天气: 每 tick 清除下雨与雷暴, 并阻止雨雪冰与闪电.
- 锁定时间 / 昼夜循环: 将世界时间冻结在设定值 (默认 6000).
- 飞行: 允许在维度内飞行 (创造模式恒允许).
- 移动速度: 行走/飞行速度, 限制在 0.05 到 2.0 之间 (默认 0.1).
- 无飞行惯性: 飞行时无移动输入即停止漂移.

## 地板预设

地板生成于 y=64（配置 `personalDimension.floorY`），沿用兼容的 JSON `default` 预设；新区块不再有基岩底层。打开管理界面的“预设”标签页，可预览样式、修改马路/标线/平台配色，点击“应用并重铺”后查看剩余区块进度；`/ae2e pd floor style`、`color`、`apply` 命令仍保留。旧基岩只在目标地板下两层清除。后台只扫描 region 表，世界修改、光照与区块包均由主线程分批执行；未加载区块在加载后续作，任务进度可跨重启恢复。

## 团队与个人模式

`/ae2e pd mode <auto|team|personal>` 设置默认目标，`/ae2e pd enter [模式]` 进入。AUTO 有 FTB PLAYER 团队时进入团队维度，否则进入个人维度；TEAM 在无团队时明确提示。切换后两个世界均保留。

团队成员关系与 MOD/OWNER 管理由 FTB 团队负责，旧 invite/kick/setperm 入口已移除，遗留白名单仅保留读取。Forge/FTB ranks 控制 enter、build、interact、manage 与 bypass 节点；交互和返回点均由服务端校验。个人所有者或团队 MOD/OWNER 可修改规则与地板。见[命令与配置](systems/commands-config.md)。
