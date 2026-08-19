package com.github.aeddddd.ae2enhanced.integration.cellterminal;

import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * 元件终端(Cell Terminal)操作的当前执行者持有者.
 *
 * <p>Cell Terminal 的分区编辑分发路径(容器动作 / 网络批量工具)不携带玩家上下文,
 * 由条件 Mixin 在入口方法处通过本类记录当前执行者,
 * {@link EMCInterfaceFilterHost} 据此执行 {@code TileEMCInterface.canManage} 权限校验.</p>
 *
 * <p>本类不引用任何 cellterminal 类,可安全无条件加载.</p>
 */
public final class CellTerminalActor {

    private static final ThreadLocal<EntityPlayer> ACTOR = new ThreadLocal<>();

    private CellTerminalActor() {
    }

    public static void set(@Nullable EntityPlayer player) {
        ACTOR.set(player);
    }

    @Nullable
    public static EntityPlayer get() {
        return ACTOR.get();
    }

    public static void clear() {
        ACTOR.remove();
    }
}
