package com.github.aeddddd.ae2enhanced.display.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 趋势显示幕墙配置操作包(C→S).
 *
 * <p>所有操作都要求玩家当前打开目标 Tile 的 {@link ContainerDisplayWall}，并复用其权限校验.</p>
 */
public class PacketDisplayAction implements IMessage {

    public static final int ACTION_CYCLE_CHART = 0;
    public static final int ACTION_CYCLE_RANGE = 1;
    public static final int ACTION_CYCLE_YMODE = 2;
    public static final int ACTION_CYCLE_COLOR = 3;
    public static final int ACTION_TOGGLE_VISIBLE = 4;
    /** 客户端缓冲失步时请求全量重同步 */
    public static final int ACTION_REQUEST_SYNC = 5;
    /** GUI 中直接选定图表类型(arg = ordinal) */
    public static final int ACTION_SET_CHART = 6;
    /** GUI 中直接选定时间范围(arg = ordinal) */
    public static final int ACTION_SET_RANGE = 7;
    /** 色板右键:反向循环颜色(arg = 槽位) */
    public static final int ACTION_CYCLE_COLOR_PREV = 8;

    private BlockPos pos;
    private int action;
    private int arg;

    public PacketDisplayAction() {}

    public PacketDisplayAction(BlockPos pos, int action, int arg) {
        this.pos = pos;
        this.action = action;
        this.arg = arg;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.action = buf.readByte();
        this.arg = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeByte(action);
        buf.writeByte(arg);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getAction() {
        return action;
    }

    public int getArg() {
        return arg;
    }
}
