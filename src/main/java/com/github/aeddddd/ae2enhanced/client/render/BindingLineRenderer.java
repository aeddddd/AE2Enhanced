package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端绑定高亮渲染器.
 *
 * 当玩家主手持有 Universal Memory Card 且准心指向某个 1 对多网络中的设备时,
 * 只为该网络渲染高亮描边边框：
 * - 中枢 ME 接口(source)：青色描边
 * - 目标机器(target)：橙色描边
 */
public class BindingLineRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        ItemStack held = player.getHeldItemMainhand();
        if (!(held.getItem() instanceof ItemUniversalMemoryCard)) return;

        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult ray = mc.objectMouseOver;
        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) return;

        BlockPos lookedPos = ray.getBlockPos();
        int lookedDim = player.world.provider.getDimension();

        // 查找准心指向的 block 属于哪个 Central ME Interface 网络
        // 使用 ACTIVE_INTERFACES 集合而不是扫描全世界 TE,避免每帧 O(N) 遍历
        TileCentralMEInterface matchedSource = null;
        for (TileCentralMEInterface source : TileCentralMEInterface.getActiveInterfaces()) {
            if (source == null || source.getWorld() != player.world) continue;

            // 准心指向 source 本身
            if (source.getPos().equals(lookedPos)) {
                matchedSource = source;
                break;
            }

            // 准心指向该 source 的某个 target
            for (TargetBinding binding : source.getInterfaceDuality().getBindings()) {
                if (binding.pos.equals(lookedPos) && binding.dimension == lookedDim) {
                    matchedSource = source;
                    break;
                }
            }

            if (matchedSource != null) break;
        }

        if (matchedSource == null) return;

        double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
        double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
        double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.glLineWidth(2.5f);

        List<RenderEntry> entries = new ArrayList<>();

        // Source
        entries.add(new RenderEntry(
                new AxisAlignedBB(matchedSource.getPos()).grow(0.002),
                0.0f, 1.0f, 1.0f, 0.85f));

        // Targets
        for (TargetBinding target : matchedSource.getInterfaceDuality().getBindings()) {
            entries.add(new RenderEntry(
                    new AxisAlignedBB(target.pos).grow(0.002),
                    1.0f, 0.65f, 0.0f, 0.85f));
        }

        if (!entries.isEmpty()) {
            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.getBuffer();
            buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            for (RenderEntry entry : entries) {
                drawBoxOutline(buf, entry.aabb, entry.r, entry.g, entry.b, entry.a);
            }

            tess.draw();
        }

        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static class RenderEntry {
        final AxisAlignedBB aabb;
        final float r, g, b, a;
        RenderEntry(AxisAlignedBB aabb, float r, float g, float b, float a) {
            this.aabb = aabb;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }
    }

    private static void drawBoxOutline(BufferBuilder buf, AxisAlignedBB aabb, float r, float g, float b, float a) {
        double minX = aabb.minX, minY = aabb.minY, minZ = aabb.minZ;
        double maxX = aabb.maxX, maxY = aabb.maxY, maxZ = aabb.maxZ;

        // 底面 4 条边
        buf.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, minZ).color(r, g, b, a).endVertex();

        // 顶面 4 条边
        buf.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();

        // 侧面 4 条边
        buf.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
    }
}
