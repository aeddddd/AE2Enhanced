---
navigation:
  parent: ae2enhanced/assembly-hub.md
  title: 装配升级卡
  icon: assembly_speed_upgrade
  position: 10
categories:
- items
item_ids:
- ae2enhanced:assembly_parallel_upgrade
- ae2enhanced:assembly_speed_upgrade
- ae2enhanced:assembly_capacity_upgrade
- ae2enhanced:assembly_auto_upload_upgrade
---

# 装配升级卡

用于[超因果装配枢纽](assembly-hub.md)的升级卡, 插入枢纽界面的升级槽生效.

## 装配并行升级卡 (最多 5 张)

<ItemLink id="assembly_parallel_upgrade" />

提升枢纽虚拟 CPU 的并行批量上限:

* 0 张: 64
* 每张 x32: 1 张 = 2048, 2 张 = 65536, 3 张 = 约 210 万, 4 张 = 约 6700 万
* 5 张: `Long.MAX_VALUE`

## 装配速度升级卡 (最多 5 张)

<ItemLink id="assembly_speed_upgrade" />

缩短每批合成的执行间隔: 基础 20 tick/批, 每张减半, 最低 1 tick/批.

## 装配扩容升级卡 (最多 10 张)

<ItemLink id="assembly_capacity_upgrade" />

增加枢纽内置样板库的页数: 基础 5 页, 每张 +10 页, 上限 100 页.

## 装配自动上传升级卡 (最多 1 张)

<ItemLink id="assembly_auto_upload_upgrade" />

插入后, 在 ME 样板编码终端中点击"上传"编码样板时,
样板会同时进入枢纽样板库.

## 配方

<RecipeFor id="assembly_parallel_upgrade" />
<RecipeFor id="assembly_speed_upgrade" />
<RecipeFor id="assembly_capacity_upgrade" />
<RecipeFor id="assembly_auto_upload_upgrade" />
