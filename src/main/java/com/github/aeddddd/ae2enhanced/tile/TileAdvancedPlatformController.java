package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.tileentity.TileEntity;

/**
 * 先进中枢平台控制器的空 TileEntity 占位实现。
 *
 * 平台功能已完全移除，该方块现在仅作装饰。保留这个空 TileEntity 是为了让旧存档中
 * 仍然存在的 advanced_platform_controller TileEntity NBT 能够安全加载并被覆盖，
 * 避免“Missing mapping”提示或因旧 TileEntity 初始化 AE 网络而崩溃。
 */
public class TileAdvancedPlatformController extends TileEntity {
    // 不进行任何 tick、不加入网络、不持有状态
}
