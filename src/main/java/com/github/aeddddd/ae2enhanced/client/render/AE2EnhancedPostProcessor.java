package com.github.aeddddd.ae2enhanced.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.shaders.Uniform;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;

/**
 * 装配枢纽黑洞后处理渲染器。
 * <p>参考 GregTechCEu Modern 的 black_hole.fsh 思路，在屏幕空间对成形装配枢纽进行
 * raymarching 全屏后处理，实现事件视界、引力透镜与吸积盘效果。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AE2EnhancedPostProcessor {

    private static TextureTarget intermediateTarget;

    private AE2EnhancedPostProcessor() {
    }

    private record TargetInfo(Vec3 worldPos, float radius, Direction facing) {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!AE2EnhancedConfig.CLIENT.enableAssemblyPostProcessing.get()
                || AE2EnhancedConfig.CLIENT.forceCompatibilityMode.get()) {
            return;
        }
        if (!AE2EnhancedShaders.isAssemblyBlackHolePostLoaded()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        Player player = mc.player;
        if (!(level instanceof ClientLevel) || player == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        double renderDist = AE2EnhancedConfig.CLIENT.renderDistance.get();
        int chunkRadius = (int) Math.ceil(renderDist / 16.0);
        ChunkPos playerChunk = player.chunkPosition();

        List<TargetInfo> targets = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunkAccess = level.getChunk(playerChunk.x + dx, playerChunk.z + dz);
                if (!(chunkAccess instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AssemblyControllerBlockEntity controller && controller.isFormed()) {
                        TargetInfo info = buildTargetInfo(controller, level);
                        if (info != null && info.worldPos.distanceToSqr(eye) <= renderDist * renderDist) {
                            targets.add(info);
                        }
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return;
        }

        ShaderInstance shader = AE2EnhancedShaders.getAssemblyBlackHolePost();
        if (shader == null) {
            return;
        }

        float time = level.getGameTime() + event.getPartialTick();
        float intensity = Mth.clamp(AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue(), 0.0f, 2.0f);
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        // 先把主渲染目标颜色复制到中间纹理，避免 shader 同时读写同一纹理产生 feedback loop / 撕裂
        int textureId = copyMainToIntermediate(mc.getMainRenderTarget(), width, height);
        // Minecraft options.fov() 已经是垂直 FOV，shader 中 rayDirection 直接使用垂直 FOV
        float fov = mc.options.fov().get().floatValue();

        for (TargetInfo info : targets) {
            Vector3f screenPos = project(info.worldPos, camera, width, height);
            if (screenPos == null || screenPos.z < 0.0f) {
                continue;
            }
            // 只剔除完全移出屏幕的效果；不对方块遮挡做剔除，因为黑洞目标位于结构内部，
            // 点遮挡检测会永远将其过滤掉，导致后处理完全不可见。
            double distance = eye.distanceTo(info.worldPos);
            double worldRadius = info.radius * 10.0;
            double focalLength = height / (2.0 * Math.tan(Math.toRadians(fov) / 2.0));
            double screenRadius = (worldRadius / distance) * focalLength;
            if (screenPos.x + screenRadius < 0 || screenPos.x - screenRadius > width
                    || screenPos.y + screenRadius < 0 || screenPos.y - screenRadius > height) {
                continue;
            }
            renderBlackHole(shader, eye, info.worldPos, screenPos, info.radius, time, intensity, fov, width, height, textureId);
        }
    }

    private static TargetInfo buildTargetInfo(AssemblyControllerBlockEntity controller, Level level) {
        BlockPos pos = controller.getBlockPos();
        Direction facing = Direction.NORTH;
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(MultiblockControllerBlock.FACING)) {
            facing = state.getValue(MultiblockControllerBlock.FACING);
        }

        // 使用与对象空间渲染器完全相同的包围盒/中心计算，保证黑洞锚点一致
        float[] bounds = AbstractMultiblockRenderer.computeBounds(AssemblyStructure.getAllSet(), facing);
        Vec3 centerOffset = AbstractMultiblockRenderer.computeCenterOffset(bounds);
        // computeCenterOffset 返回的是从控制器方块整数坐标到结构几何中心的偏移，与 AbstractMultiblockRenderer 保持一致
        Vec3 worldPos = new Vec3(pos.getX() + centerOffset.x, pos.getY() + centerOffset.y, pos.getZ() + centerOffset.z);
        // 将黑洞半径统一为对象空间渲染使用的事件视界基准半径，避免后处理中 u_size 过小导致效果不可见
        return new TargetInfo(worldPos, 2.5f, facing);
    }

    /**
     * 将主渲染目标颜色复制到中间纹理，避免 shader 同时读写同一纹理产生 feedback loop / 撕裂。
     */
    private static int copyMainToIntermediate(RenderTarget mainTarget, int width, int height) {
        if (intermediateTarget == null || intermediateTarget.width != width || intermediateTarget.height != height) {
            if (intermediateTarget != null) {
                intermediateTarget.destroyBuffers();
            }
            intermediateTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            intermediateTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            intermediateTarget.clear(Minecraft.ON_OSX);
        }
        // 确保中间纹理已分配存储
        intermediateTarget.bindWrite(false);
        intermediateTarget.unbindWrite();

        mainTarget.bindRead();
        RenderSystem.bindTexture(intermediateTarget.getColorTextureId());
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        mainTarget.unbindRead();

        return intermediateTarget.getColorTextureId();
    }

    /**
     * 将世界坐标投影到屏幕像素坐标。
     * <p>手动构建 viewMatrix = R * T，其中 R 是相机旋转的逆，T 是 -eye 平移；
     * 然后 viewProj = projection * viewMatrix，使用 JOML project 得到屏幕坐标。</p>
     */
    private static Vector3f project(Vec3 worldPos, Camera camera, int width, int height) {
        Vec3 eye = camera.getPosition();

        Matrix4f viewMatrix = new Matrix4f().identity()
                .rotate(camera.rotation().invert(new Quaternionf()))
                .translate((float) -eye.x, (float) -eye.y, (float) -eye.z);

        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
        Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(viewMatrix);

        // 手动投影到裁剪空间，检查 w 与 z 以正确剔除位于相机后方的目标
        Vector4f clip = new Vector4f((float) worldPos.x, (float) worldPos.y, (float) worldPos.z, 1.0f);
        viewProj.transform(clip);
        if (clip.w <= 0.0f || clip.z < -clip.w) {
            return null;
        }

        float invW = 1.0f / clip.w;
        float x = (clip.x * invW + 1.0f) * 0.5f * width;
        float y = (1.0f - clip.y * invW) * 0.5f * height;
        float z = clip.z * invW * 0.5f + 0.5f;
        return new Vector3f(x, y, z);
    }

    private static void renderBlackHole(ShaderInstance shader, Vec3 eye, Vec3 target, Vector3f targetScreen, float size,
            float time, float intensity, float fov, int width, int height, int textureId) {
        RenderSystem.setShader(() -> shader);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().identity(), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        // 保存当前 GL 状态，绘制后恢复，避免状态泄漏导致后续渲染阶段撕裂/深度失效
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int[] blendSrc = new int[1];
        int[] blendDst = new int[1];
        GL11.glGetIntegerv(GL11.GL_BLEND_SRC, blendSrc);
        GL11.glGetIntegerv(GL11.GL_BLEND_DST, blendDst);

        try {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            RenderSystem.setShaderTexture(0, textureId);

            Uniform uTime = shader.getUniform("u_time");
            Uniform uResolution = shader.getUniform("u_resolution");
            Uniform uIntensity = shader.getUniform("u_intensity");
            Uniform uSize = shader.getUniform("u_size");
            Uniform uFov = shader.getUniform("u_fov");
            Uniform uTargetScreen = shader.getUniform("u_targetScreen");
            Uniform eyeUniform = shader.getUniform("eye");
            Uniform targetUniform = shader.getUniform("target");

            if (uTime != null) {
                uTime.set(time * 0.05f);
            }
            if (uResolution != null) {
                uResolution.set((float) width, (float) height);
            }
            if (uIntensity != null) {
                uIntensity.set(intensity);
            }
            if (uSize != null) {
                uSize.set(size);
            }
            if (uFov != null) {
                uFov.set(fov);
            }
            if (uTargetScreen != null) {
                uTargetScreen.set(targetScreen.x, targetScreen.y);
            }
            if (eyeUniform != null) {
                eyeUniform.set((float) eye.x, (float) eye.y, (float) eye.z);
            }
            if (targetUniform != null) {
                targetUniform.set((float) target.x, (float) target.y, (float) target.z);
            }

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder builder = tesselator.getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.vertex(-1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, -1.0, 0.0).endVertex();
            builder.vertex(1.0, 1.0, 0.0).endVertex();
            builder.vertex(-1.0, 1.0, 0.0).endVertex();
            tesselator.end();
        } finally {
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(depthMask);
            if (blend) {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(blendSrc[0], blendDst[0]);
            } else {
                RenderSystem.disableBlend();
            }
        }

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}
