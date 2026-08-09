package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.display.ChartType;
import com.github.aeddddd.ae2enhanced.display.DisplayPalette;
import com.github.aeddddd.ae2enhanced.display.DisplayTheme;
import com.github.aeddddd.ae2enhanced.display.TimeRange;
import com.github.aeddddd.ae2enhanced.display.TrendBuffer;
import com.github.aeddddd.ae2enhanced.display.YAxisMode;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 趋势显示幕墙 TESR.
 *
 * <p>仅 master 面板执行绘制,一次绘制覆盖整个屏幕面.
 * 虚拟分辨率 32px/格;配色沿用项目 GuiColors 色系(深底 #1a1a2e 家族 + 青色 #00d4ff 强调).
 * 五种图表类型;Y 轴 nice-number 刻度 + k/M 缩写 + 自动量程平滑过渡;
 * X 轴相对时间标签 + 右缘"当前"标记;顶部图例标注每条曲线对应的物品.</p>
 *
 * <p>绘制期间关闭面剔除(防止特定视角下图层被剔除),
 * 图层通过递增 z 偏移避免 z-fighting.</p>
 */
public class RenderDisplayWall extends TileEntitySpecialRenderer<TileDisplayPanel> {

    /** 每格虚拟像素数 */
    private static final float PX = 32.0f;
    /** 图表边距(虚拟像素):左(Y 轴标签)/上(图例)/右/下(X 轴标签) */
    private static final float MARGIN_L = 34, MARGIN_T = 14, MARGIN_R = 6, MARGIN_B = 14;

    // 图层间距需足够大:过小会在斜视时与方块面 z-fighting(表现为闪烁/贴图边框渗出)
    private static final double Z_BG = 0.004;
    private static final double Z_GRID = 0.008;
    private static final double Z_DATA = 0.012;
    private static final double Z_TEXT = 0.016;

    @Override
    public boolean isGlobalRenderer(TileDisplayPanel te) {
        return true;
    }

    @Override
    public void render(TileDisplayPanel te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        if (!te.isMasterRole()) return;
        // 距离剔除(isGlobalRenderer 关闭了默认剔除,这里手动按配置距离剔除)
        double maxDist = AE2EnhancedConfig.displayWall.renderDistance;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view != null && te.getDistanceSq(view.posX, view.posY, view.posZ) > maxDist * maxDist) {
            return;
        }
        EnumFacing facing = te.getFacing();
        int w = te.getRectW();
        int h = te.getRectH();
        if (w <= 0 || h <= 0) return;

        // 观察者视角左下角的世界坐标角点 C 与旋转角
        double cx, cy = te.getPos().getY(), cz;
        float angle;
        switch (facing) {
            case SOUTH: cx = te.getPos().getX();         cz = te.getPos().getZ() + 1; angle = 0;   break;
            case EAST:  cx = te.getPos().getX() + 1;     cz = te.getPos().getZ() + w; angle = 90;  break;
            case NORTH: cx = te.getPos().getX() + w;     cz = te.getPos().getZ();     angle = 180; break;
            case WEST:  cx = te.getPos().getX();         cz = te.getPos().getZ();     angle = 270; break;
            default: return;
        }

        GlStateManager.pushMatrix();
        // 平移到角点(相对 TE 原点),绕角点旋转,使局部 +x=观察者右、+y=上、+z=朝向观察者
        GlStateManager.translate(x + (cx - te.getPos().getX()),
                y + (cy - te.getPos().getY()), z + (cz - te.getPos().getZ()));
        GlStateManager.rotate(angle, 0, 1, 0);
        // 切换为像素坐标:y 向下、原点为屏幕左上角
        GlStateManager.scale(1.0 / PX, -1.0 / PX, 1.0);
        GlStateManager.translate(0, -h * PX, 0);

        // GL 状态管理复刻 RenderComputationCore 的成熟模式:
        // 关键是禁用 lightmap 纹理单元(单元1)——POSITION_COLOR 顶点不带纹理坐标,
        // 单元1 激活时会用残留坐标采样 lightmap 调制顶点颜色,
        // 表现为特定视角下网格线/内容变暗或消失.
        boolean blendWas = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWas = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean fogWas = GL11.glIsEnabled(GL11.GL_FOG);
        boolean alphaWas = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean lightingWas = GL11.glIsEnabled(GL11.GL_LIGHTING);
        int shadeModelWas = GL11.glGetInteger(GL11.GL_SHADE_MODEL);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmapTexWas = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        if (lightmapTexWas) {
            GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        boolean texture2DWas = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        // GL11 与 GlStateManager 双重调用,防止状态跟踪不同步
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.disableLighting();
        // 关闭面剔除:像素空间经过 y 翻转,quad 绕序对正反面判定不可靠
        GlStateManager.disableCull();
        GL11.glDisable(GL11.GL_FOG);
        GlStateManager.disableFog();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GlStateManager.glLineWidth(1.2f);

        try {
            Theme t = new Theme(te.getTheme());
            float pw = w * PX, ph = h * PX;

            // 背景
            quad(0, 0, pw, ph, t.bg, Z_BG);

            if (!te.isPowered()) {
                quad(0, 0, pw, ph, t.overlay, Z_GRID);
                String s = tr("gui.ae2enhanced.display_wall.no_power");
                drawText(s, pw / 2 - font().getStringWidth(s) / 2f, ph / 2 - 4, t.textDim, Z_TEXT);
                return;
            }

            List<Integer> active = activeSlots(te);
            if (active.isEmpty()) {
                String s = tr("gui.ae2enhanced.display_wall.empty_hint");
                drawText(s, pw / 2 - font().getStringWidth(s) / 2f, ph / 2 - 4, t.textDim, Z_TEXT);
                return;
            }

            // 图例:顶部一行,色块 + 物品名,标注每条曲线对应的物品
            drawLegend(te, active, pw, t);

            float gx = MARGIN_L, gy = MARGIN_T;
            float gw = pw - MARGIN_L - MARGIN_R;
            float gh = ph - MARGIN_T - MARGIN_B;

            if (te.getChartType() == ChartType.RATE) {
                renderRate(te, active, gx, gy, gw, gh, t);
            } else {
                renderChart(te, active, gx, gy, gw, gh, t, partialTicks);
            }
        } finally {
            GL11.glLineWidth(1.0f);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GlStateManager.shadeModel(shadeModelWas);
            if (texture2DWas) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (lightingWas) {
                GL11.glEnable(GL11.GL_LIGHTING);
                GlStateManager.enableLighting();
            } else {
                GL11.glDisable(GL11.GL_LIGHTING);
                GlStateManager.disableLighting();
            }
            if (fogWas) {
                GL11.glEnable(GL11.GL_FOG);
                GlStateManager.enableFog();
            } else {
                GL11.glDisable(GL11.GL_FOG);
                GlStateManager.disableFog();
            }
            if (alphaWas) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GlStateManager.enableAlpha();
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GlStateManager.disableAlpha();
            }
            if (lightmapTexWas) {
                GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.enableTexture2D();
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            }
            if (cullWas) GlStateManager.enableCull();
            if (!blendWas) GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.popMatrix();
        }
    }

    // ==================== 图例 ====================

    /** 顶部图例:每项 = 色块 + 名称,自动截断超出屏幕宽度的部分. */
    private void drawLegend(TileDisplayPanel te, List<Integer> active, float pw, Theme t) {
        float lx = MARGIN_L;
        float ly = 3;
        float maxX = pw - MARGIN_R;
        for (int slot : active) {
            int color = DisplayPalette.get(te.getSlotColor(slot));
            ItemStack cfg = te.getConfigInv().getStackInSlot(slot);
            String name = cfg.isEmpty() ? "?" : cfg.getDisplayName();
            int nameW = font().getStringWidth(name);
            // 超长名称逐步截断
            while (nameW > 60 && name.length() > 2) {
                name = name.substring(0, name.length() - 2);
                nameW = font().getStringWidth(name + "…");
            }
            if (nameW > 60) name += "…";
            nameW = font().getStringWidth(name);
            if (lx + 8 + nameW + 8 > maxX) break; // 放不下则停止
            quad(lx, ly + 1, lx + 6, ly + 7, color, Z_GRID);
            // 名称使用曲线同色,明确对应关系
            drawText(name, lx + 8, ly, color, Z_TEXT);
            lx += 8 + nameW + 8;
        }
    }

    // ==================== 图表(折线/面积/变化量/堆叠) ====================

    private void renderChart(TileDisplayPanel te, List<Integer> active,
                             float gx, float gy, float gw, float gh, Theme t, float partialTicks) {
        TimeRange range = te.getTimeRange();
        int tier = TrendBuffer.selectTier(range.getSeconds());
        int interval = TrendBuffer.TIER_INTERVAL[tier];
        int maxSamples = range.getSeconds() / interval;

        long dataMax = 1;
        long deltaMax = 1;
        int samples = 0;
        for (int slot : active) {
            TrendBuffer buf = te.getBuffer(slot);
            samples = Math.max(samples, Math.min(buf.getSize(tier), maxSamples));
        }
        if (samples < 2) {
            String s = tr("gui.ae2enhanced.display_wall.collecting");
            drawText(s, gx + gw / 2 - font().getStringWidth(s) / 2f, gy + gh / 2 - 4, t.textDim, Z_TEXT);
            return;
        }
        ChartType type = te.getChartType();

        if (type == ChartType.DELTA) {
            for (int slot : active) {
                TrendBuffer buf = te.getBuffer(slot);
                int n = Math.min(buf.getSize(tier), samples);
                for (int i = 1; i < n; i++) {
                    if (buf.isValid(tier, i) && buf.isValid(tier, i - 1)) {
                        long d = Math.abs(buf.getValue(tier, i - 1) - buf.getValue(tier, i));
                        deltaMax = Math.max(deltaMax, d);
                    }
                }
            }
        } else if (type == ChartType.STACKED) {
            for (int i = 0; i < samples; i++) {
                long sum = 0;
                for (int slot : active) {
                    TrendBuffer buf = te.getBuffer(slot);
                    if (i < buf.getSize(tier) && buf.isValid(tier, i)) sum += buf.getValue(tier, i);
                }
                dataMax = Math.max(dataMax, sum);
            }
        } else {
            for (int slot : active) {
                TrendBuffer buf = te.getBuffer(slot);
                int n = Math.min(buf.getSize(tier), samples);
                for (int i = 0; i < n; i++) {
                    if (buf.isValid(tier, i)) dataMax = Math.max(dataMax, buf.getValue(tier, i));
                }
            }
        }

        // Y 量程:AUTO 平滑过渡 / FIXED 固定 / LOG 对数
        YAxisMode yMode = te.getYMode();
        boolean log = yMode == YAxisMode.LOG;
        long targetMax = type == ChartType.DELTA ? deltaMax : dataMax;
        float smoothMax;
        if (yMode == YAxisMode.FIXED) {
            smoothMax = te.getFixedMax();
        } else {
            float tgt = niceCeil(targetMax * 1.1f);
            float prev = te.clientSmoothMax;
            smoothMax = prev <= 0 ? tgt : prev + (tgt - prev) * 0.08f;
            if (Math.abs(smoothMax - tgt) < tgt * 0.001f) smoothMax = tgt;
        }
        te.clientSmoothMax = smoothMax;
        float yMax = log ? (float) Math.log10(smoothMax + 1) : smoothMax;
        if (yMax <= 0) yMax = 1;

        // ---- 网格与 Y 轴标签 ----
        if (log) {
            for (int p = 0; Math.pow(10, p) <= smoothMax; p++) {
                float frac = (float) (Math.log10(Math.pow(10, p) + 1) / yMax);
                float yy = gy + gh - frac * gh;
                quad(gx, yy, gx + gw, yy + 0.5f, t.grid, Z_GRID);
                drawTextRight(fmt((long) Math.pow(10, p)), gx - 2, yy - 4, t.textDim, Z_TEXT);
            }
        } else if (type == ChartType.DELTA) {
            float step = niceStep(smoothMax, 3);
            for (float v = -smoothMax; v <= smoothMax + step * 0.5f; v += step) {
                float yy = gy + gh / 2 - (v / smoothMax) * (gh / 2);
                quad(gx, yy, gx + gw, yy + 0.5f, Math.abs(v) < step * 0.5f ? t.axis : t.grid, Z_GRID);
                drawTextRight(fmtSigned((long) v), gx - 2, yy - 4, t.textDim, Z_TEXT);
            }
        } else {
            float step = niceStep(smoothMax, 4);
            for (float v = 0; v <= smoothMax + step * 0.5f; v += step) {
                float yy = gy + gh - (v / yMax) * gh;
                if (yy < gy - 1) break;
                quad(gx, yy, gx + gw, yy + 0.5f, v == 0 ? t.axis : t.grid, Z_GRID);
                drawTextRight(fmt((long) v), gx - 2, yy - 4, t.textDim, Z_TEXT);
            }
        }
        // 图表边框
        quad(gx, gy, gx + gw, gy + 0.5f, t.grid, Z_GRID);
        quad(gx, gy, gx + 0.5f, gy + gh, t.grid, Z_GRID);
        quad(gx + gw - 0.5f, gy, gx + gw, gy + gh, t.grid, Z_GRID);
        quad(gx, gy + gh - 0.5f, gx + gw, gy + gh, t.axis, Z_GRID);

        // ---- X 轴:相对时间标签 + 右缘"当前"标记,明确时间方向 ----
        int totalSec = range.getSeconds();
        for (int i = 1; i <= 3; i++) {
            float xx = gx + gw - gw * i / 3f;
            String label = "-" + fmtTime(totalSec * i / 3);
            drawText(label, xx - font().getStringWidth(label) / 2f, gy + gh + 3, t.textDim, Z_TEXT);
        }
        // 右缘"当前"竖线与标签(时间轴方向锚点)
        quad(gx + gw - 1, gy, gx + gw, gy + gh, t.accent, Z_GRID);
        String now = tr("gui.ae2enhanced.display_wall.now");
        drawText(now, gx + gw - font().getStringWidth(now), gy + gh + 3, t.accent, Z_TEXT);

        // ---- 数据绘制 ----
        // dx 按满刻度固定,不随已有采样数变化;否则数据积累期图表每秒整体重缩放
        float dx = gw / Math.max(1, maxSamples - 1);
        // 次秒级平滑滚动:随距上个采样包的时间推移,图表整体左移,新点从右缘滑入
        float scroll = te.getScrollShift(partialTicks) * dx;
        switch (type) {
            case LINE:
                for (int slot : active) {
                    drawLine(te, slot, tier, samples, gx, gy, gw, gh, dx, scroll, yMax, log,
                            DisplayPalette.get(te.getSlotColor(slot)), 0xFF, false);
                }
                break;
            case AREA:
                for (int slot : active) {
                    drawLine(te, slot, tier, samples, gx, gy, gw, gh, dx, scroll, yMax, log,
                            DisplayPalette.get(te.getSlotColor(slot)), 0x55, true);
                    drawLine(te, slot, tier, samples, gx, gy, gw, gh, dx, scroll, yMax, log,
                            DisplayPalette.get(te.getSlotColor(slot)), 0xFF, false);
                }
                break;
            case DELTA:
                for (int slot : active) {
                    drawDelta(te, active, slot, tier, samples, gx, gy, gw, gh, dx, scroll, smoothMax,
                            DisplayPalette.get(te.getSlotColor(slot)));
                }
                break;
            case STACKED:
                drawStacked(te, active, tier, samples, gx, gy, gw, gh, dx, scroll, yMax, log);
                break;
            default:
                break;
        }

        // ---- 最新值端点圆点,强化"最新在右"的方向感 ----
        if (type != ChartType.DELTA) {
            for (int slot : active) {
                TrendBuffer buf = te.getBuffer(slot);
                if (buf.getSize(tier) < 1 || !buf.isValid(tier, 0)) continue;
                long v = buf.getValue(tier, 0);
                float frac = log ? (float) (Math.log10(v + 1) / yMax) : v / yMax;
                float yy = gy + gh - Math.min(1, Math.max(0, frac)) * gh;
                int color = DisplayPalette.get(te.getSlotColor(slot));
                float dotX = gx + gw - 1.5f - scroll;
                quad(Math.max(gx, dotX - 1.5f), yy - 1.5f, dotX + 1.5f, yy + 1.5f, color, Z_DATA + 0.0005);
            }
        }
    }

    /** 折线(valid=false 处断线);fill=true 时填充到基线. scroll 为平滑滚动位移. */
    private void drawLine(TileDisplayPanel te, int slot, int tier, int samples,
                          float gx, float gy, float gw, float gh, float dx, float scroll,
                          float yMax, boolean log, int color, int alphaOverride, boolean fill) {
        TrendBuffer buf = te.getBuffer(slot);
        int n = Math.min(buf.getSize(tier), samples);
        if (n < 2) return;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        int prim = fill ? GL11.GL_TRIANGLE_STRIP : GL11.GL_LINE_STRIP;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        GlStateManager.disableTexture2D();
        b.begin(prim, DefaultVertexFormats.POSITION_COLOR);
        boolean pen = false;
        for (int i = n - 1; i >= 0; i--) {
            // i=0 为最新采样,画在最右;i 越大越靠左,与 X 轴时间标签方向一致
            float xx = Math.max(gx, gx + gw - i * dx - scroll);
            if (!buf.isValid(tier, i)) {
                if (pen) {
                    tess.draw();
                    b.begin(prim, DefaultVertexFormats.POSITION_COLOR);
                    pen = false;
                }
                continue;
            }
            long v = buf.getValue(tier, i);
            float frac = log ? (float) (Math.log10(v + 1) / yMax) : v / yMax;
            float yy = gy + gh - Math.min(1, Math.max(0, frac)) * gh;
            if (fill) {
                // TRIANGLE_STRIP:基线点(透明)与数据点交替
                b.pos(xx, gy + gh, Z_DATA).color(r, g, bl, 0).endVertex();
                b.pos(xx, yy, Z_DATA).color(r, g, bl, alphaOverride).endVertex();
            } else {
                b.pos(xx, yy, Z_DATA).color(r, g, bl, alphaOverride).endVertex();
            }
            pen = true;
        }
        tess.draw();
    }

    /** 变化量柱状图(零线居中,正上负下;多物品水平错位避免遮挡). */
    private void drawDelta(TileDisplayPanel te, List<Integer> active, int slot, int tier, int samples,
                           float gx, float gy, float gw, float gh, float dx, float scroll,
                           float yMax, int color) {
        TrendBuffer buf = te.getBuffer(slot);
        int n = Math.min(buf.getSize(tier), samples);
        if (n < 2) return;
        float zeroY = gy + gh / 2;
        float barW = Math.max(1, dx * 0.7f / Math.max(1, active.size()));
        float offset = active.indexOf(slot) * barW;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        GlStateManager.disableTexture2D();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        for (int i = n - 1; i >= 1; i--) {
            if (!buf.isValid(tier, i) || !buf.isValid(tier, i - 1)) continue;
            long d = buf.getValue(tier, i - 1) - buf.getValue(tier, i);
            float xx = gx + gw - i * dx - scroll + offset;
            if (xx < gx) continue;
            float hh = (d / yMax) * (gh / 2);
            float y1 = zeroY - Math.max(0, hh);
            float y2 = zeroY - Math.min(0, hh);
            b.pos(xx, y2, Z_DATA).color(r, g, bl, 0xAA).endVertex();
            b.pos(xx + barW, y2, Z_DATA).color(r, g, bl, 0xAA).endVertex();
            b.pos(xx + barW, y1, Z_DATA).color(r, g, bl, 0xAA).endVertex();
            b.pos(xx, y1, Z_DATA).color(r, g, bl, 0xAA).endVertex();
        }
        tess.draw();
    }

    /** 构成占比堆叠面积. */
    private void drawStacked(TileDisplayPanel te, List<Integer> active, int tier, int samples,
                             float gx, float gy, float gw, float gh, float dx, float scroll,
                             float yMax, boolean log) {
        // prev[i] 按"距最新的采样数 i"索引累计高度
        long[] prev = new long[samples];
        for (int slot : active) {
            TrendBuffer buf = te.getBuffer(slot);
            int n = Math.min(buf.getSize(tier), samples);
            int color = DisplayPalette.get(te.getSlotColor(slot));
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder b = tess.getBuffer();
            GlStateManager.disableTexture2D();
            b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int i = n - 1; i >= 0; i--) {
                float xx = Math.max(gx, gx + gw - i * dx - scroll);
                long cur = buf.isValid(tier, i) ? buf.getValue(tier, i) : 0;
                long top = prev[i] + cur;
                float fracTop = log ? (float) (Math.log10(top + 1) / yMax) : top / yMax;
                float fracBot = log ? (float) (Math.log10(prev[i] + 1) / yMax) : prev[i] / yMax;
                prev[i] = top;
                float yTop = gy + gh - Math.min(1, Math.max(0, fracTop)) * gh;
                float yBot = gy + gh - Math.min(1, Math.max(0, fracBot)) * gh;
                b.pos(xx, yBot, Z_DATA).color(r, g, bl, 0x88).endVertex();
                b.pos(xx, yTop, Z_DATA).color(r, g, bl, 0x88).endVertex();
            }
            tess.draw();
        }
    }

    // ==================== 速率读数 ====================

    private void renderRate(TileDisplayPanel te, List<Integer> active,
                            float gx, float gy, float gw, float gh, Theme t) {
        float rowH = Math.min(20, gh / Math.max(1, active.size()));
        float yy = gy + 2;
        for (int slot : active) {
            TrendBuffer buf = te.getBuffer(slot);
            int color = DisplayPalette.get(te.getSlotColor(slot));
            // 色块
            quad(gx - MARGIN_L + 4, yy + 1, gx - MARGIN_L + 12, yy + 9, color, Z_GRID);
            // 名称
            ItemStack cfg = te.getConfigInv().getStackInSlot(slot);
            String name = font().trimStringToWidth(cfg.getDisplayName(), (int) (gw * 0.35f));
            drawText(name, gx - MARGIN_L + 16, yy + 1, t.text, Z_TEXT);

            if (buf.getSize(0) >= 2) {
                long now = buf.getValue(0, 0);
                int back = Math.min(59, buf.getSize(0) - 1);
                long past = buf.getValue(0, back);
                long ratePerMin = (now - past) * 60 / Math.max(1, back);
                String value = fmt(now);
                String rate = fmtSigned(ratePerMin) + "/min";
                drawText(value, gx + gw * 0.45f, yy + 1, t.text, Z_TEXT);
                int rc = ratePerMin > 0 ? 0xFF55FF88 : ratePerMin < 0 ? 0xFFFF5555 : t.textDim;
                drawText(rate, gx + gw * 0.45f + font().getStringWidth(value) + 8, yy + 1, rc, Z_TEXT);
                drawSparkline(buf, gx + gw - 40, yy, 40, rowH - 2, color);
            }
            yy += rowH;
        }
    }

    private void drawSparkline(TrendBuffer buf, float x, float y, float w, float h, int color) {
        int n = Math.min(buf.getSize(0), 120);
        if (n < 2) return;
        long max = 1;
        for (int i = 0; i < n; i++) max = Math.max(max, buf.getValue(0, i));
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        GlStateManager.disableTexture2D();
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
        float dx = w / (n - 1f);
        for (int i = n - 1; i >= 0; i--) {
            float xx = x + w - i * dx;
            float yy = y + h - (buf.getValue(0, i) / (float) max) * h;
            b.pos(xx, yy, Z_DATA).color(r, g, bl, 0xFF).endVertex();
        }
        tess.draw();
    }

    // ==================== 工具 ====================

    private List<Integer> activeSlots(TileDisplayPanel te) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < TileDisplayPanel.MAX_TRACKED; i++) {
            if (!te.getConfigInv().getStackInSlot(i).isEmpty() && te.isSlotVisible(i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** nice-number 步进:1/2/5 × 10^n */
    private static float niceStep(float range, int targetDivs) {
        float raw = range / Math.max(1, targetDivs);
        float mag = (float) Math.pow(10, Math.floor(Math.log10(raw)));
        float norm = raw / mag;
        float step = norm < 1.5f ? 1 : norm < 3f ? 2 : norm < 7f ? 5 : 10;
        return step * mag;
    }

    private static float niceCeil(float v) {
        if (v <= 1) return 1;
        float mag = (float) Math.pow(10, Math.floor(Math.log10(v)));
        float norm = v / mag;
        float ceil = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10;
        return ceil * mag;
    }

    /** 大数缩写:1234 → 1.2k */
    public static String fmt(long v) {
        long abs = Math.abs(v);
        if (abs < 1000) return Long.toString(v);
        String[] units = {"k", "M", "G", "T", "P"};
        double d = v;
        int u = -1;
        do {
            d /= 1000.0;
            u++;
        } while (Math.abs(d) >= 1000 && u < units.length - 1);
        return String.format(Locale.ROOT, "%.1f%s", d, units[u]);
    }

    private static String fmtSigned(long v) {
        return (v > 0 ? "+" : "") + fmt(v);
    }

    private static String fmtTime(int seconds) {
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }

    private static String tr(String key) {
        return I18n.format(key);
    }

    private static FontRenderer font() {
        return Minecraft.getMinecraft().fontRenderer;
    }

    private void quad(float x1, float y1, float x2, float y2, int argb, double z) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        GlStateManager.disableTexture2D();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        int a = (argb >>> 24), r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
        b.pos(x1, y2, z).color(r, g, bl, a).endVertex();
        b.pos(x2, y2, z).color(r, g, bl, a).endVertex();
        b.pos(x2, y1, z).color(r, g, bl, a).endVertex();
        b.pos(x1, y1, z).color(r, g, bl, a).endVertex();
        tess.draw();
    }

    private void drawText(String s, float x, float y, int argb, double z) {
        GlStateManager.enableTexture2D();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, z);
        font().drawString(s, (int) x, (int) y, argb & 0x00FFFFFF);
        GlStateManager.popMatrix();
    }

    private void drawTextRight(String s, float rightX, float y, int argb, double z) {
        drawText(s, rightX - font().getStringWidth(s), y, argb, z);
    }

    /**
     * 主题配色,深色主题沿用项目 GuiColors 色系
     * (PANEL_BG #1a1a2e / TEXT_MAIN #e0e0e0 / TEXT_DIM #88aaaa / ACCENT #00d4ff).
     */
    private static final class Theme {
        final int bg, overlay, grid, axis, text, textDim, accent;

        Theme(DisplayTheme theme) {
            if (theme == DisplayTheme.LIGHT) {
                bg = 0xFFF4F5F7;
                overlay = 0xCCD8DADE;
                grid = 0x331A1A2E;
                axis = 0x881A1A2E;
                text = 0xFF1A1A2E;
                textDim = 0xFF5A6270;
                accent = 0xFF0F4C75;
            } else {
                bg = 0xFF141628;
                overlay = 0xCC0D0F1E;
                grid = 0x33E0E0E0;
                axis = 0x88E0E0E0;
                text = 0xFFE0E0E0;
                textDim = 0xFF88AAAA;
                accent = 0xFF00D4FF;
            }
        }
    }
}
