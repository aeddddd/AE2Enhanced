package com.github.aeddddd.ae2enhanced.dimension.dev;

import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.FloorStyleRegistry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/** 隔离双客户端验收共用的 dev-only 地板样式；正常游戏不注册。 */
public final class PrivateDimensionHarness {
    public static final String STYLE_ID = "harness";

    private PrivateDimensionHarness() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("ae2e.harness.privateDimension");
    }

    public static void registerFloorStyle() {
        if (enabled()) {
            FloorStyleRegistry.register(STYLE_ID, new FloorPreset(1, 1,
                    new IBlockState[]{Blocks.CONCRETE.getStateFromMeta(1)}, new int[]{0}));
        }
    }
}
