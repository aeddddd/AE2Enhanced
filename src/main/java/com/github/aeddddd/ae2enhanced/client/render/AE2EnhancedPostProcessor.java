package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.shaders.Uniform;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.assembly.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.block.MultiblockControllerBlock;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;

/**
 * 装配枢纽黑洞后处理渲染器.
 * <p>以结构几何中心为黑洞原点做全屏光线步进：事件视界、吸积盘体渲染与外部辉光.
 * 场景直通采样（受控黑洞,不扭曲结构）,并通过深度缓冲重建实现方块遮挡.</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AE2EnhancedPostProcessor {

    private static TextureTarget intermediateTarget;

    private AE2EnhancedPostProcessor() {
    }

    private record TargetInfo(Vec3 worldPos, float radius, Direction facing) {
    }

    /**
     * 后处理是否处于激活状态（配置 + 环境 + shader 加载）.
     * <p>对象空间渲染器据此跳过黑洞本体,保证任意配置组合下只渲染一个黑洞.</p>
     */
    public static boolean isPostActive() {
        return AE2EnhancedConfig.CLIENT.enableAssemblyPostProcessing.get()
                && AE2EnhancedConfig.CLIENT.enableAssemblyShader.get()
                && !AE2EnhancedConfig.CLIENT.forceCompatibilityMode.get()
                && !ModList.get().isLoaded("oculus")
                && AE2EnhancedShaders.isAssemblyBlackHolePostLoaded();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!isPostActive()) {
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

        // 只取距离最近的一个已成形枢纽：每个目标都要跑一遍全屏光线步进,多个叠加会拖垮 GPU
        TargetInfo nearest = null;
        double nearestDistSqr = renderDist * renderDist;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunkAccess = level.getChunk(playerChunk.x + dx, playerChunk.z + dz);
                if (!(chunkAccess instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof AssemblyControllerBlockEntity controller && controller.isFormed()) {
                        TargetInfo info = buildTargetInfo(controller, level);
                        if (info != null) {
                            double distSqr = info.worldPos.distanceToSqr(eye);
                            if (distSqr <= nearestDistSqr) {
                                nearest = info;
                                nearestDistSqr = distSqr;
                            }
                        }
                    }
                }
            }
        }

        if (nearest == null) {
            return;
        }

        ShaderInstance shader = AE2EnhancedShaders.getAssemblyBlackHolePost();
        if (shader == null) {
            return;
        }

        float time = level.getGameTime() + event.getPartialTick();
        float intensity = Mth.clamp(AE2EnhancedConfig.CLIENT.dynamicRenderIntensity.get().floatValue(), 0.0f, 2.0f);
        // 尺寸以主渲染目标为准（framebuffer 像素）,与 gl_FragCoord/u_resolution 坐标系严格一致；
        // 不能用窗口尺寸——DPI 缩放下两者可能不一致,会导致拷贝越界静默失败
        RenderTarget mainTarget = mc.getMainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;
        TextureTarget intermediate = copyMainToIntermediate(mainTarget, width, height);

        Vector3f screenPos = project(nearest.worldPos, eye, event.getPoseStack().last().pose(), width, height);
        if (screenPos == null) {
            return;
        }

        // 直接从当前投影矩阵推导 FOV 与逆投影矩阵：与游戏实际渲染严格一致,
        // 不依赖配置项或事件缓存,保证视线重建与深度重建对齐
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        float fov = (float) Math.toDegrees(2.0 * Math.atan(1.0 / proj.m11()));
        Matrix4f invProj = proj.invert();

        // shader 以黑洞中心为世界原点：eye 上传相对坐标；
        // target = eye + 相机视线方向,使 viewMatrix 重建的视线与游戏相机完全一致
        Vec3 eyeRel = eye.subtract(nearest.worldPos);
        Vector3f look = camera.getLookVector();
        Vector3f upVec = camera.getUpVector();
        Vec3 targetRel = eyeRel.add(look.x(), look.y(), look.z());
        Vec3 up = new Vec3(upVec.x(), upVec.y(), upVec.z());

        renderBlackHole(shader, eyeRel, targetRel, up, fov, invProj, time, intensity, width, height, intermediate);

        // 约束壳必须在光线步进之后叠加：事件视界阴影不合成背景,
        // 壳若在方块实体阶段绘制（不写深度）会被阴影整体覆盖
        renderShellOverlay(nearest, camera, event.getPoseStack(), time, intensity);
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
        Vec3 centerOffset = AssemblyHubRenderer.getShiftedCenterOffset(bounds);
        Vec3 worldPos = new Vec3(pos.getX() + centerOffset.x, pos.getY() + centerOffset.y, pos.getZ() + centerOffset.z);
        return new TargetInfo(worldPos, 1.8f, facing);
    }

    /**
     * 将主渲染目标颜色与深度复制到中间渲染目标,避免 shader 同时读写同一纹理产生 feedback loop / 撕裂.
     * <p>注意 {@link RenderTarget#bindRead()} 只绑定颜色纹理、并不绑定任何帧缓冲（命名有误导性）,
     * 必须用原始 GL 调用显式指定 READ/DRAW 帧缓冲,blit 才能从主渲染目标拷到中间纹理.</p>
     */
    private static TextureTarget copyMainToIntermediate(RenderTarget mainTarget, int width, int height) {
        if (intermediateTarget == null || intermediateTarget.width != width || intermediateTarget.height != height) {
            if (intermediateTarget != null) {
                intermediateTarget.destroyBuffers();
            }
            // 需要深度附件：shader 逐像素重建场景距离做方块遮挡剔除
            intermediateTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            intermediateTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            intermediateTarget.clear(Minecraft.ON_OSX);
        }

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, intermediateTarget.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainTarget.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        // 恢复：主渲染目标绑定为当前帧缓冲（读写）,与渲染管线后续阶段状态一致
        mainTarget.bindWrite(false);

        return intermediateTarget;
    }

    /**
     * 将世界坐标投影到屏幕像素坐标（gl_FragCoord 同一坐标系,左下原点）,仅用于相机背面剔除.
     * <p>事件阶段提供的 pose 只含相机旋转、不含平移,需先减去相机位置补全视图变换；
     * 只剔除会导致透视除零的退化情况（目标位于相机背后）.</p>
     */
    private static Vector3f project(Vec3 worldPos, Vec3 cameraPos, Matrix4fc modelViewMatrix, int width,
            int height) {
        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
        Matrix4f viewProj = new Matrix4f(projectionMatrix).mul(modelViewMatrix);

        Vector4f clip = new Vector4f(
                (float) (worldPos.x - cameraPos.x),
                (float) (worldPos.y - cameraPos.y),
                (float) (worldPos.z - cameraPos.z),
                1.0f);
        viewProj.transform(clip);

        if (clip.w <= 0.0001f) {
            return null;
        }

        float invW = 1.0f / clip.w;
        float x = (clip.x * invW + 1.0f) * 0.5f * width;
        float y = (clip.y * invW + 1.0f) * 0.5f * height;
        return new Vector3f(x, y, 0.0f);
    }

    // 事件视界深度遮挡球半径（方块）：光线步进捕获半径 _ShadowR×_Scale = 1.8,
    // 加上引力透镜暗区外扩,取 2.6 与屏幕上的黑色区域对齐
    private static final float EVENT_HORIZON_OCCLUDER_RADIUS = 2.6f;

    /**
     * 遮挡球专用 builder（仅渲染线程访问）.初始容量 64KB,可容纳
     * 24x24 段球体（3456 顶点 × 16B = ~54KB）,begin() 每帧重置并复用底层内存.
     */
    @Nullable
    private static BufferBuilder occluderBuilder;

    private static BufferBuilder getOccluderBuilder() {
        if (occluderBuilder == null) {
            occluderBuilder = new BufferBuilder(1 << 16);
        }
        return occluderBuilder;
    }

    /**
     * 约束壳叠加绘制：在全屏光线步进之后执行,保证壳线不被事件视界阴影覆盖.
     * <p>顶点经事件 pose（相机旋转）+ 相对平移变换到相机空间,与对象空间路径的
     * 坐标约定一致；additive 混合、不写深度,保留深度测试使壳仍被结构方块正确遮挡.</p>
     * <p>光线步进本身不写深度,直接画壳会让黑洞后方的壳线叠在黑洞之上；
     * 因此先以纯深度方式绘制事件视界遮挡球,后方壳线由深度测试正确剔除.</p>
     */
    private static void renderShellOverlay(TargetInfo target, Camera camera, PoseStack eventPose, float time,
            float intensity) {
        ShaderInstance shader = AE2EnhancedShaders.getAssemblyBlackHole();
        if (shader == null) {
            return;
        }

        Vec3 rel = target.worldPos.subtract(camera.getPosition());
        // 动画时间与对象空间路径一致（gameTime + partialTick 后乘 0.05）
        float animTime = time * 0.05f;

        Uniform uTime = shader.getUniform("uTime");
        Uniform uIntensity = shader.getUniform("uIntensity");
        Uniform uScale = shader.getUniform("uScale");
        Uniform uCenter = shader.getUniform("uCenter");
        if (uTime != null) {
            uTime.set(animTime);
        }
        if (uIntensity != null) {
            uIntensity.set(intensity);
        }
        if (uScale != null) {
            // 对象空间路径 getScaleFactor 固定返回 1.0,此处保持一致
            uScale.set(1.0f);
        }
        if (uCenter != null) {
            uCenter.set((float) rel.x, (float) rel.y, (float) rel.z);
        }

        // 事件 pose 只含相机旋转、不含平移,补上相对平移后顶点即为相机空间坐标
        PoseStack pose = new PoseStack();
        pose.last().pose().mul(eventPose.last().pose());
        pose.translate(rel.x, rel.y, rel.z);

        // 事件视界遮挡球使用独立 BufferBuilder：Tesselator 只有一个内部 builder,
        // 复用会导致 begin() 在未 end() 的 builder 上重复调用（Already building! 崩溃）.
        // 该 builder 静态复用：每帧新建会让 24x24 球体把 direct buffer 撑到 ~2MB 后丢弃,
        // 原生内存仅靠 GC+Cleaner 回收,高帧率下耗尽直接内存（OOM: Failed to resize buffer）
        BufferBuilder occluder = getOccluderBuilder();
        occluder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        RenderHelper.drawSphere(occluder, pose, EVENT_HORIZON_OCCLUDER_RADIUS, 0x000000, 1.0f, 24, 24);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        AssemblyHubRenderer.appendContainmentShellGeometry(builder, pose, animTime, 1.0, 24, 24);

        // 保存 GL 状态,绘制后恢复（同 renderBlackHole 的状态保护策略）
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int[] blendSrc = new int[1];
        int[] blendDst = new int[1];
        GL11.glGetIntegerv(GL11.GL_BLEND_SRC, blendSrc);
        GL11.glGetIntegerv(GL11.GL_BLEND_DST, blendDst);

        // shader 顶点已是相机空间,ModelViewMat 必须为单位矩阵
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();

            // 先画遮挡球：关闭颜色写入、开启深度写入,将事件视界写入深度缓冲
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            BufferUploader.drawWithShader(occluder.end());

            // 再画约束壳：恢复颜色写入,壳线不写深度,后方壳线被遮挡球剔除
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.colorMask(true, true, true, true);
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
            if (cull) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderBlackHole(ShaderInstance shader, Vec3 eye, Vec3 target, Vec3 up, float fov,
            Matrix4f invProj, float time, float intensity, int width, int height, TextureTarget intermediate) {
        Minecraft mc = Minecraft.getInstance();

        // 保存当前 FBO 与 viewport,绘制后恢复
        int[] savedFbo = new int[1];
        int[] savedViewport = new int[4];
        GL30.glGetIntegerv(GL30.GL_FRAMEBUFFER_BINDING, savedFbo);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);

        // Sampler 纹理必须在绘制前指定：VertexBuffer._drawWithShader 会按
        // RenderSystem.getShaderTexture 设置采样器并在 apply() 时绑定到对应纹理单元
        RenderSystem.setShaderTexture(0, intermediate.getColorTextureId());
        RenderSystem.setShaderTexture(1, intermediate.getDepthTextureId());
        RenderSystem.setShader(() -> shader);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().identity(), VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        // 保存当前 GL 状态,绘制后恢复,避免状态泄漏导致后续渲染阶段撕裂/深度失效
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int[] blendSrc = new int[1];
        int[] blendDst = new int[1];
        GL11.glGetIntegerv(GL11.GL_BLEND_SRC, blendSrc);
        GL11.glGetIntegerv(GL11.GL_BLEND_DST, blendDst);

        try {
            // 深度测试保持关闭：遮挡关系由 shader 通过深度缓冲重建逐像素判定
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            Uniform uTime = shader.getUniform("u_time");
            Uniform uResolution = shader.getUniform("u_resolution");
            Uniform uIntensity = shader.getUniform("u_intensity");
            Uniform uFov = shader.getUniform("u_fov");
            Uniform uInvProj = shader.getUniform("u_invProj");
            Uniform eyeUniform = shader.getUniform("eye");
            Uniform targetUniform = shader.getUniform("target");
            Uniform upUniform = shader.getUniform("u_up");

            if (uTime != null) {
                uTime.set(time * 0.05f);
            }
            if (uResolution != null) {
                uResolution.set((float) width, (float) height);
            }
            if (uIntensity != null) {
                uIntensity.set(intensity);
            }
            if (uFov != null) {
                uFov.set(fov);
            }
            if (uInvProj != null) {
                uInvProj.set(invProj);
            }
            if (eyeUniform != null) {
                eyeUniform.set((float) eye.x, (float) eye.y, (float) eye.z);
            }
            if (targetUniform != null) {
                targetUniform.set((float) target.x, (float) target.y, (float) target.z);
            }
            if (upUniform != null) {
                upUniform.set((float) up.x, (float) up.y, (float) up.z);
            }

            // 确保绘制到 Minecraft 主渲染目标,而不是默认 FBO,否则后续 mainTarget blit 会覆盖输出
            mc.getMainRenderTarget().bindWrite(false);

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

            // 恢复之前的 FBO 与 viewport
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo[0]);
            GL11.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
        }

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }
}
