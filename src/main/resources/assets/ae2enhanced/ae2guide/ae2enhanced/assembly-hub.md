---
navigation:
  parent: ae2enhanced/multiblocks.md
  title: 超因果装配枢纽
  icon: assembly_controller
  position: 10
categories:
- multiblocks
item_ids:
- ae2enhanced:assembly_controller
- ae2enhanced:assembly_casing_1
- ae2enhanced:assembly_casing_2
- ae2enhanced:assembly_casing_3
- ae2enhanced:assembly_casing_4
- ae2enhanced:assembly_inner_wall
- ae2enhanced:assembly_stabilizer
- ae2enhanced:multiblock_me_interface
---

# 超因果装配枢纽

<Row>
  <BlockImage id="assembly_controller" scale="4" />
  <BlockImage id="assembly_casing_1" scale="4" />
  <BlockImage id="assembly_inner_wall" scale="4" />
  <BlockImage id="assembly_stabilizer" scale="4" />
</Row>

超因果装配枢纽是本模组的核心多方块: 它在 ME 网络中注册一个类似分子装配室的**巨型合成枢纽**,
允许以远超分子装配室的速度执行自动合成.

## 特性

* **虚拟合成**: 允许执行虚拟合成路径, 实际合成卡顿极小.
* **较高的并行上限**: 装配枢纽提供基础 64/s 的并行合成能力, 并且可以由
  [装配并行升级卡](assembly-upgrades.md) 来提升最大并行 (最高并行上限 `Long.MAX_VALUE`).
* **样板库与自动上传**: 最高支持 100 页样板库, 支持自动上传样板到 ME 网络.
* **递归合成**: 支持产物同时是原料的净产出样板 (如 A + 2B = 2A).
  下单前网络中需至少有 1 份产物作为"种子", 计算与执行会自动循环利用种子, 只交付净产出.

## 搭建

结构由装配控制器, 四种装配外壳, 装配内壁与装配稳定器组成.
任意结构方块均可连接 ME 网络.

未成型时右键控制器可打开结构界面, 使用**自动搭建**功能查看/放置结构预览;
结构完整后枢纽自动成型.

## 升级卡

枢纽有 6 个升级槽, 支持四种升级卡 (详见[装配升级卡](assembly-upgrades.md)):

* <ItemLink id="assembly_parallel_upgrade" /> - 提升并行批量上限
* <ItemLink id="assembly_speed_upgrade" /> - 缩短每批的执行间隔
* <ItemLink id="assembly_capacity_upgrade" /> - 增加内置样板页数
* <ItemLink id="assembly_auto_upload_upgrade" /> - 编码终端上传样板时同步进入枢纽样板库
