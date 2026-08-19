package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;

/**
 * 奇点处理仓 TESR：透明框架立方体内部渲染一个微型奇点
 * （框架由方块静态模型承担,此处只渲染奇点）.
 *
 * <p>缩放 0.04：事件视界半径 0.16 格,吸积盘外缘 0.48 格,完整收入框架窗口内.</p>
 */
public class RenderTileSingularityChamber extends TileEntitySpecialRenderer<TileSingularityChamber> {

    private static final float CHAMBER_SCALE = 0.04f;

    @Override
    public void render(TileSingularityChamber te, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (te.getWorld() == null) {
            return;
        }
        RenderMicroSingularity.renderSingularity(te.getWorld(), x, y, z, partialTicks, CHAMBER_SCALE);
    }
}
