package com.github.aeddddd.ae2enhanced.client.render;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;

import java.util.Set;

/**
 * 多方块结构放置预览渲染器。
 * 当玩家手持控制器方块并对准可放置位置时，以幽灵方块边框形式显示整个结构的占用空间。
 *
 * 颜色约定：
 * - 青色：控制器位置
 * - 黄色：ME 接口位置
 * - 绿色：其他结构方块位置（可放置或已有正确方块）
 * - 红色：被其他方块阻挡的位置
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class StructurePlacementPreview {

    private static final int COLOR_CONTROLLER = 0x00FFFF;
    private static final int COLOR_ME_INTERFACE = 0xFFFF00;
    private static final int COLOR_VALID = 0x00FF00;
    private static final int COLOR_INVALID = 0xFF0000;
    private static final double MAX_PREVIEW_DISTANCE = 32.0;

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            held = player.getHeldItemOffhand();
        }
        if (held.isEmpty()) return;

        StructureType type = getStructureType(held);
        if (type == null) return;

        RayTraceResult ray = mc.objectMouseOver;
        if (ray == null || ray.typeOfHit != RayTraceResult.Type.BLOCK) return;

        BlockPos placePos = ray.getBlockPos().offset(ray.sideHit);
        World world = player.world;
        EnumFacing facing = player.getHorizontalFacing().getOpposite();

        double dx = placePos.getX() + 0.5 - player.posX;
        double dy = placePos.getY() + 0.5 - player.posY;
        double dz = placePos.getZ() + 0.5 - player.posZ;
        if (dx * dx + dy * dy + dz * dz > MAX_PREVIEW_DISTANCE * MAX_PREVIEW_DISTANCE) return;

        double rx = mc.getRenderManager().viewerPosX;
        double ry = mc.getRenderManager().viewerPosY;
        double rz = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-rx, -ry, -rz);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.glLineWidth(2.0f);

        try {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

            switch (type) {
                case ASSEMBLY:
                    renderAssembly(world, placePos, facing, buffer);
                    break;
                case HYPERDIMENSIONAL:
                    renderHyperdimensional(world, placePos, facing, buffer);
                    break;
                case SUPERCAUSAL:
                    renderSupercausal(world, placePos, facing, buffer);
                    break;
            }

            tessellator.draw();
        } finally {
            GlStateManager.glLineWidth(1.0f);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static StructureType getStructureType(ItemStack held) {
        Item item = held.getItem();
        if (item == Item.getItemFromBlock(ModBlocks.ASSEMBLY_CONTROLLER)) {
            return StructureType.ASSEMBLY;
        } else if (item == Item.getItemFromBlock(ModBlocks.HYPERDIMENSIONAL_CONTROLLER)) {
            return StructureType.HYPERDIMENSIONAL;
        } else if (item == Item.getItemFromBlock(ModBlocks.COMPUTATION_CORE)) {
            return StructureType.SUPERCAUSAL;
        }
        return null;
    }

    private static void renderAssembly(World world, BlockPos controllerPos, EnumFacing facing, BufferBuilder buffer) {
        BlockPos origin = controllerPos.add(rotate(new BlockPos(0, 0, 7), facing));
        renderBlockSet(world, origin, facing, AssemblyStructure.CORE_SET, ModBlocks.ASSEMBLY_CONTROLLER, COLOR_CONTROLLER, buffer);
        renderBlockSet(world, origin, facing, AssemblyStructure.PART1_SET, ModBlocks.ASSEMBLY_ME_INTERFACE, COLOR_ME_INTERFACE, buffer);
        renderBlockSet(world, origin, facing, AssemblyStructure.PART2_SET, ModBlocks.ASSEMBLY_CASING, COLOR_VALID, buffer);
        renderBlockSet(world, origin, facing, AssemblyStructure.PART3_SET, ModBlocks.ASSEMBLY_INNER_WALL, COLOR_VALID, buffer);
        renderBlockSet(world, origin, facing, AssemblyStructure.PART4_SET, ModBlocks.ASSEMBLY_STABILIZER, COLOR_VALID, buffer);
    }

    private static void renderHyperdimensional(World world, BlockPos controllerPos, EnumFacing facing, BufferBuilder buffer) {
        renderBlockSet(world, controllerPos, facing, HyperdimensionalStructure.CONTROLLER_SET, ModBlocks.HYPERDIMENSIONAL_CONTROLLER, COLOR_CONTROLLER, buffer);
        renderBlockSet(world, controllerPos, facing, HyperdimensionalStructure.ME_INTERFACE_SET, ModBlocks.HYPERDIMENSIONAL_ME_INTERFACE, COLOR_ME_INTERFACE, buffer);
        renderBlockSet(world, controllerPos, facing, HyperdimensionalStructure.CORE_SET, ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE, COLOR_VALID, buffer);
        renderBlockSet(world, controllerPos, facing, HyperdimensionalStructure.CASING_SET, ModBlocks.HYPERDIMENSIONAL_CASING, COLOR_VALID, buffer);
    }

    private static void renderSupercausal(World world, BlockPos controllerPos, EnumFacing facing, BufferBuilder buffer) {
        EnumFacing sf = facing.getOpposite();

        // 控制器
        drawSingleBlock(world, controllerPos, ModBlocks.COMPUTATION_CORE, COLOR_CONTROLLER, buffer);

        // ME 接口
        BlockPos meActual = controllerPos.add(rotate(SupercausalStructure.ME_INTERFACE_REL, sf));
        drawSingleBlock(world, meActual, ModBlocks.SUPER_CRAFTING_INTERFACE, COLOR_ME_INTERFACE, buffer);

        // TENSOR_CASING_SET（跳过控制器位置，已由上方处理）
        for (BlockPos rel : SupercausalStructure.TENSOR_CASING_SET) {
            if (rel.equals(SupercausalStructure.CONTROLLER_REL)) continue;
            BlockPos actual = controllerPos.add(rotate(rel, sf));
            drawSingleBlock(world, actual, ModBlocks.CONSTANT_TENSOR_FIELD_CASING, COLOR_VALID, buffer);
        }

        for (BlockPos rel : SupercausalStructure.CAUSAL_ANCHOR_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, sf));
            drawSingleBlock(world, actual, ModBlocks.CAUSAL_ANCHOR_CORE, COLOR_VALID, buffer);
        }

        for (BlockPos rel : SupercausalStructure.SPINOR_CASING_SET) {
            BlockPos actual = controllerPos.add(rotate(rel, sf));
            drawSingleBlock(world, actual, ModBlocks.CONSTANT_SPINOR_FIELD_CASING, COLOR_VALID, buffer);
        }
    }

    private static void renderBlockSet(World world, BlockPos origin, EnumFacing facing,
                                       Set<BlockPos> relSet, Block expected, int baseColor,
                                       BufferBuilder buffer) {
        for (BlockPos rel : relSet) {
            BlockPos actual = origin.add(rotate(rel, facing));
            drawSingleBlock(world, actual, expected, baseColor, buffer);
        }
    }

    private static void drawSingleBlock(World world, BlockPos actual, Block expected, int baseColor, BufferBuilder buffer) {
        IBlockState state = world.getBlockState(actual);
        Block block = state.getBlock();
        int color = baseColor;
        if (!block.isReplaceable(world, actual) && block != expected) {
            color = COLOR_INVALID;
        }
        drawBlockOutline(buffer, actual, color, 0.5f);
    }

    private static void drawBlockOutline(BufferBuilder buffer, BlockPos pos, int color, float alpha) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        // 底面
        line(buffer, x, y, z, x + 1, y, z, r, g, b, alpha);
        line(buffer, x + 1, y, z, x + 1, y, z + 1, r, g, b, alpha);
        line(buffer, x + 1, y, z + 1, x, y, z + 1, r, g, b, alpha);
        line(buffer, x, y, z + 1, x, y, z, r, g, b, alpha);
        // 顶面
        line(buffer, x, y + 1, z, x + 1, y + 1, z, r, g, b, alpha);
        line(buffer, x + 1, y + 1, z, x + 1, y + 1, z + 1, r, g, b, alpha);
        line(buffer, x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, alpha);
        line(buffer, x, y + 1, z + 1, x, y + 1, z, r, g, b, alpha);
        // 竖边
        line(buffer, x, y, z, x, y + 1, z, r, g, b, alpha);
        line(buffer, x + 1, y, z, x + 1, y + 1, z, r, g, b, alpha);
        line(buffer, x + 1, y, z + 1, x + 1, y + 1, z + 1, r, g, b, alpha);
        line(buffer, x, y, z + 1, x, y + 1, z + 1, r, g, b, alpha);
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    /**
     * 与三个结构类完全一致的旋转逻辑。
     */
    private static BlockPos rotate(BlockPos rel, EnumFacing facing) {
        if (facing == EnumFacing.NORTH) return rel;
        int x = rel.getX();
        int y = rel.getY();
        int z = rel.getZ();
        switch (facing) {
            case SOUTH: return new BlockPos(-x, y, -z);
            case EAST:  return new BlockPos(-z, y, x);
            case WEST:  return new BlockPos(z, y, -x);
            default:    return rel;
        }
    }

    private enum StructureType {
        ASSEMBLY, HYPERDIMENSIONAL, SUPERCAUSAL
    }
}
