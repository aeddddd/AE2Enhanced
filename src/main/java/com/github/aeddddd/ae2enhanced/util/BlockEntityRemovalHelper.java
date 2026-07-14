package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 方块实体移除原因判断工具。
 * <p>用于区分 {@code setRemoved} 是由于方块被破坏，还是由于区块卸载 / 服务器关闭。
 * 在 {@code Block#onRemove} 中标记即将被破坏的方块实体，{@code setRemoved} 时检查该标记，
 * 避免在区块卸载路径上调用 {@code Level#getBlockState} 触发不必要的区块加载。</p>
 */
public final class BlockEntityRemovalHelper {

    // 使用 WeakHashMap 避免 BlockEntity 生命周期结束后造成内存泄漏。
    private static final Set<BlockEntity> BEING_BROKEN = Collections.newSetFromMap(new WeakHashMap<>());

    private BlockEntityRemovalHelper() {
    }

    /**
     * 标记指定方块实体因所在方块被破坏而移除。
     * <p>应在 {@code Block#onRemove} 中、方块状态已改变但 BlockEntity 尚未被移除前调用。</p>
     *
     * @param be 要标记的方块实体
     */
    public static void markForBreak(@Nullable BlockEntity be) {
        if (be != null) {
            BEING_BROKEN.add(be);
        }
    }

    /**
     * 判断当前方块实体是否因所在方块被破坏而移除。
     *
     * @param be 要判断的方块实体
     * @return 若该方块实体已被标记为破坏移除，则返回 true
     */
    public static boolean isBlockBeingBroken(@Nullable BlockEntity be) {
        return be != null && BEING_BROKEN.contains(be);
    }
}
