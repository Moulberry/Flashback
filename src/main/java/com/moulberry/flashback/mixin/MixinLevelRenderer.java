package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
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
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
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

    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void tick(CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            this.ticks = replayServer.getReplayTick();
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void renderLevel(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer,
                            Matrix4f matrix4f, Matrix4f projection, CallbackInfo ci) {
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

    @Inject(method = "renderBlockDestroyAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderBlockDestroyAnimation(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderSectionLayer(RenderType renderType, double d, double e, double f, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                ci.cancel();
                return;
            }
        }

        if (renderType == RenderType.cutout() && Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
            RenderTarget main = Minecraft.getInstance().mainRenderTarget;

            if (this.roundAlphaBuffer == null) {
                this.roundAlphaBuffer = new TextureTarget(main.width, main.height, false);
            } else if (this.roundAlphaBuffer.width != main.width || this.roundAlphaBuffer.height != main.height) {
                this.roundAlphaBuffer.destroyBuffers();
                this.roundAlphaBuffer = new TextureTarget(main.width, main.height, false);
            }

            this.roundAlphaBuffer.bindWrite(true);

            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._viewport(0, 0, main.viewWidth, main.viewHeight);
            GlStateManager._disableBlend();
            CompiledShaderProgram shaderInstance = Objects.requireNonNull(
                RenderSystem.setShader(ShaderManager.blitScreenRoundAlpha), "Blit shader not loaded"
            );
            shaderInstance.bindSampler("InSampler", main.colorTextureId);
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

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/renderer/FogParameters;"))
    public FogParameters setupFog(Camera camera, FogRenderer.FogMode fogMode, Vector4f colour, float distance, boolean foggy, float partialTick, Operation<FogParameters> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;
            if (visuals.overrideFogColour) {
                float[] fogColour = visuals.fogColour;
                if (fogMode == FogRenderer.FogMode.FOG_SKY) {
                    colour.set(fogColour[0], fogColour[1], fogColour[2], 1.0F);
                } else {
                    colour = new Vector4f(fogColour[0], fogColour[1], fogColour[2], 1.0F);
                }
            }
            if (fogMode == FogRenderer.FogMode.FOG_SKY && !visuals.renderSky) {
                if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
                    Vector4f originalColour = colour;
                    colour = new Vector4f(colour);
                    originalColour.set(0, 0, 0, 0);
                } else {
                    float[] skyColour = visuals.skyColour;
                    colour.set(skyColour[0], skyColour[1], skyColour[2], 1.0F);
                }
            }
        }
        return original.call(camera, fogMode, colour, distance, foggy, partialTick);
    }

    @Inject(method = "renderBlockEntities", at = @At("HEAD"), cancellable = true)
    public void renderBlockEntities(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, MultiBufferSource.BufferSource bufferSource2, Camera camera, float f, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderBlocks) {
            ci.cancel();
        }
    }


    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    public void renderEntity(Entity entity, double d, double e, double f, float g, PoseStack poseStack, MultiBufferSource multiBufferSource, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (entity instanceof Player) {
            if (editorState != null && !editorState.replayVisuals.renderPlayers) {
                ci.cancel();
            }
        } else {
            if (editorState != null && !editorState.replayVisuals.renderEntities) {
                ci.cancel();
            }
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

    @Inject(method = "addParticlesPass", at = @At("HEAD"), cancellable = true)
    public void addParticlesPass(FrameGraphBuilder frameGraphBuilder, Camera camera, float f, FogParameters fogParameters, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderParticles) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    public void addParticleInternal(ParticleOptions particleOptions, boolean bl, boolean bl2, double d, double e, double f, double g, double h, double i, CallbackInfoReturnable<Particle> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderParticles) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    public void addSkyPass(FrameGraphBuilder frameGraphBuilder, Camera camera, float f, FogParameters fogParameters, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderSky) {
            ci.cancel();
        }
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
