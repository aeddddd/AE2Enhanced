---
navigation:
  title: 无线频道
  parent: devices.md
  position: 20
  icon: wireless_channel_transmitter
item_ids: [wireless_channel_transmitter, channel_receiver_card]
---

# 无线频道

<ItemLink id="wireless_channel_transmitter" /> **无线频道发生器**广播所在网络的频道, 让远程设备无需线缆即可共享频道.

## 绑定接收卡

1. 将未绑定的 <ItemLink id="channel_receiver_card" /> **无线频道接收卡**放入发生器的槽位, 卡片会自动绑定, 记录发生器的坐标, 维度.
2. 把绑定后的卡插入 AE2 设备 (部件或机器) 的升级槽.
3. 设备与发生器建立远程网络连接, 经发生器的控制器路径获取频道.

## 细节

- 发生器仅背面接线缆, 需要频道, 默认待机功耗 512 AE (配置 `wirelessChannel.transmitterPower`).
- 跨维度连接默认开启 (`wirelessChannel.crossDimension`); 距离默认不限 (`wirelessChannel.maxRange` = 0).
- 连接按定时器重连 (`wirelessChannel.reconnectIntervalTicks`, 默认 100 tick).
- 总线升级槽数量: 5 + `wirelessChannel.extraUpgradeSlots` (默认 2).
