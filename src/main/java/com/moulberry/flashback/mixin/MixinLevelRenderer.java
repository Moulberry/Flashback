package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.exporting.PerfectFrames;
import com.moulberry.flashback.visuals.ReplayVisuals;
import com.moulberry.flashback.visuals.ShaderManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class MixinLevelRenderer {

    @Shadow @Final public ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow private @Nullable SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow @Final public SectionOcclusionGraph sectionOcclusionGraph;

    @Shadow
    private int ticks;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void tick(CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            this.ticks = replayServer.getReplayTick();
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void renderLevel(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f projection, CallbackInfo ci) {
        ReplayUI.lastProjectionMatrix = projection;
        ReplayUI.lastViewQuaternion = camera.rotation();
    }

    @Unique
    private RenderTarget roundAlphaBuffer = null;

    @Inject(method = "close", at = @At("HEAD"))
    public void close(CallbackInfo ci) {
        if (this.roundAlphaBuffer != null) {
            this.roundAlphaBuffer.destroyBuffers();
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderSectionLayer(RenderType renderType, double d, double e, double f, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 2, shift = At.Shift.AFTER), require = 0)
    public void renderLevel_renderCutoutLayer(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
            RenderTarget main = Minecraft.getInstance().mainRenderTarget;

            RenderSystem.assertOnRenderThread();
            if (this.roundAlphaBuffer == null) {
                this.roundAlphaBuffer = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            } else if (this.roundAlphaBuffer.width != main.width || this.roundAlphaBuffer.height != main.height) {
                this.roundAlphaBuffer.destroyBuffers();
                this.roundAlphaBuffer = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            }

            this.roundAlphaBuffer.bindWrite(true);

            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._viewport(0, 0, main.viewWidth, main.viewHeight);
            GlStateManager._disableBlend();
            ShaderInstance shaderInstance = Objects.requireNonNull(ShaderManager.blitScreenRoundAlpha, "Blit shader not loaded");
            shaderInstance.setSampler("DiffuseSampler", main.colorTextureId);
            shaderInstance.apply();
            BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
            bufferBuilder.addVertex(0.0f, 0.0f, 0.0f);
            bufferBuilder.addVertex(1.0f, 0.0f, 0.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(0.0f, 1.0f, 0.0f);
            BufferUploader.draw(bufferBuilder.buildOrThrow());
            shaderInstance.clear();

            main.bindWrite(true);
            this.roundAlphaBuffer.blitToScreen(main.width, main.height);

            GlStateManager._enableBlend();
            GlStateManager._depthMask(true);
            GlStateManager._enableDepthTest();
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FogRenderer;levelFogColor()V", shift = At.Shift.AFTER), require = 0)
    public void renderLevel_levelFogColor(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;
            if (visuals.overrideFogColour) {
                float[] fogColour = visuals.fogColour;
                FogRenderer.fogRed = fogColour[0];
                FogRenderer.fogGreen = fogColour[1];
                FogRenderer.fogBlue = fogColour[2];
                RenderSystem.setShaderFogColor(fogColour[0], fogColour[1], fogColour[2]);

                if (visuals.renderSky) {
                    RenderSystem.clearColor(fogColour[0], fogColour[1], fogColour[2], 1.0F);
                }
            }
            if (!visuals.renderSky) {
                if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
                    RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0F);
                } else {
                    float[] skyColour = visuals.skyColour;
                    RenderSystem.clearColor(skyColour[0], skyColour[1], skyColour[2], 1.0F);
                }
            }

        }
    }

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"), require = 0)
    public boolean renderLevel_renderBlockEntity(BlockEntityRenderDispatcher instance, BlockEntity blockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource) {
        EditorState editorState = EditorStateManager.getCurrent();
        return editorState == null || editorState.replayVisuals.renderBlocks;
    }

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"), require = 0)
    public boolean renderLevel_renderEntity(LevelRenderer instance, Entity entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (entity instanceof Player) {
            return editorState == null || editorState.replayVisuals.renderPlayers;
        } else {
            return editorState == null || editorState.replayVisuals.renderEntities;
        }
    }

    @Unique
    private Biome.Precipitation forcePrecipitation = null;

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"))
    public void renderSnowAndRain(LightTexture lightTexture, float f, double d, double e, double g, CallbackInfo ci) {
        this.forcePrecipitation = null;

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            switch (editorState.replayVisuals.overrideWeatherMode) {
                case CLEAR, OVERCAST -> forcePrecipitation = Biome.Precipitation.NONE;
                case RAINING, THUNDERING -> forcePrecipitation = Biome.Precipitation.RAIN;
                case SNOWING -> forcePrecipitation = Biome.Precipitation.SNOW;
            }
        }
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;hasPrecipitation()Z"))
    public boolean renderSnowAndRain_hasPrecipitation(Biome instance, Operation<Boolean> original) {
        if (this.forcePrecipitation != null) {
            return this.forcePrecipitation != Biome.Precipitation.NONE;
        }
        return original.call(instance);
    }

    @WrapOperation(method = "renderSnowAndRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    public Biome.Precipitation renderSnowAndRain_getPrecipitationAt(Biome instance, BlockPos blockPos, Operation<Biome.Precipitation> original) {
        if (this.forcePrecipitation != null) {
            return this.forcePrecipitation;
        } else {
            return original.call(instance, blockPos);
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCloudsType()Lnet/minecraft/client/CloudStatus;"), require = 0)
    public CloudStatus renderLevel_getCloudsType(Options instance, Operation<CloudStatus> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderSky) {
            return CloudStatus.OFF;
        } else {
            return original.call(instance);
        }
    }

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V"), require = 0)
    public boolean renderLevel_renderParticles(ParticleEngine instance, LightTexture lightTexture, Camera camera, float f) {
        EditorState editorState = EditorStateManager.getCurrent();
        return editorState == null || editorState.replayVisuals.renderParticles;
    }

    @Inject(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    public void addParticleInternal(ParticleOptions particleOptions, boolean bl, boolean bl2, double d, double e, double f, double g, double h, double i, CallbackInfoReturnable<Particle> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderParticles) {
            cir.setReturnValue(null);
        }
    }

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"), require = 0)
    public boolean renderLevel_renderSky(LevelRenderer instance, Matrix4f matrix4f, Matrix4f matrix4f2, float f, Camera camera, boolean bl, Runnable runnable) {
        EditorState editorState = EditorStateManager.getCurrent();
        return editorState == null || editorState.replayVisuals.renderSky;
    }

//    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"), require = 0)
//    public void setupRender(LevelRenderer instance, Camera camera, Frustum frustum, boolean capturedFrustum, boolean isSpectator, Operation<Void> original) {
//        if (PerfectFrames.isEnabled()) {
//            boolean doCompile = true;
//            while (doCompile) {
//                doCompile = false;
//
//                int before = this.visibleSections.size();
//                original.call(instance, camera, frustum, capturedFrustum, isSpectator);
//                if (this.visibleSections.size() != before) {
//                    doCompile = true;
//                }
//
//                RenderRegionCache renderRegionCache = new RenderRegionCache();
//                for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
//                    if (renderSection.isDirty()) {
//                        this.sectionRenderDispatcher.rebuildSectionSync(renderSection, renderRegionCache);
//                        renderSection.setNotDirty();
//                        doCompile = true;
//                    }
//                }
//
//                this.sectionRenderDispatcher.uploadAllPendingUploads();
//            }
//        } else {
//            original.call(instance, camera, frustum, capturedFrustum, isSpectator);
//        }
//    }

}
