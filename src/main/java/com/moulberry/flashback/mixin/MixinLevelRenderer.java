package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.visuals.ReplayVisuals;
import com.moulberry.flashback.visuals.ShaderManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.world.TickRateManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalInt;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class MixinLevelRenderer {

    @Shadow @Final public ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow private @Nullable SectionRenderDispatcher sectionRenderDispatcher;

    @Shadow @Final public SectionOcclusionGraph sectionOcclusionGraph;

    @Shadow
    private int ticks;

    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method = "tick", at = @At("HEAD"))
    public void tick(CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            this.ticks = replayServer.getReplayTick();
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/TickRateManager;runsNormally()Z"))
    public boolean tick_runsNormally(TickRateManager instance, Operation<Boolean> original) {
        if (Flashback.isInReplay()) {
            return false;
        }
        return original.call(instance);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void renderLevel(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline, CameraRenderState cameraState,
            Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci) {
        ReplayUI.lastProjectionMatrix = new Matrix4f(cameraState.projectionMatrix);
        ReplayUI.lastViewQuaternion = new Quaternionf(cameraState.orientation);

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;

            if (!visuals.renderSky) {
                if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
                    fogColor.set(0f, 0f, 0f, 0f);
                } else {
                    float[] skyColour = visuals.skyColour;
                    fogColor.set(skyColour[0], skyColour[1], skyColour[2], 1f);
                }
            }
        }
    }

    @Unique
    private GpuTexture roundAlphaBuffer = null;
    @Unique
    private GpuTextureView roundAlphaBufferView = null;

    @Inject(method = "close", at = @At("HEAD"))
    public void close(CallbackInfo ci) {
        if (this.roundAlphaBuffer != null) {
            this.roundAlphaBuffer.close();
            this.roundAlphaBuffer = null;
        }
        if (this.roundAlphaBufferView != null) {
            this.roundAlphaBufferView.close();
            this.roundAlphaBufferView = null;
        }
    }


    @Inject(method = "extractBlockDestroyAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderBlockDestroyAnimation(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                ci.cancel();
            }
        }
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V"))
    public void method_62214_renderChunkGroup(ChunkSectionsToRender instance, ChunkSectionLayerGroup chunkSectionLayerGroup, GpuSampler gpuSampler, Operation<Void> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                return;
            }
        }

        original.call(instance, chunkSectionLayerGroup, gpuSampler);

        if (chunkSectionLayerGroup == ChunkSectionLayerGroup.OPAQUE && Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
            RenderTarget main = Minecraft.getInstance().mainRenderTarget;

            if (this.roundAlphaBuffer == null) {
                this.roundAlphaBuffer = RenderSystem.getDevice().createTexture(() -> "flashback round alpha buffer", GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, main.width, main.height, 1, 1);
                this.roundAlphaBufferView = RenderSystem.getDevice().createTextureView(this.roundAlphaBuffer);
            } else if (this.roundAlphaBuffer.getWidth(0) != main.width || this.roundAlphaBuffer.getHeight(0) != main.height) {
                this.roundAlphaBuffer.close();
                this.roundAlphaBufferView.close();
                this.roundAlphaBuffer = RenderSystem.getDevice().createTexture(() -> "flashback round alpha buffer", GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, main.width, main.height, 1, 1);
                this.roundAlphaBufferView = RenderSystem.getDevice().createTextureView(this.roundAlphaBuffer);
            }

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback round alpha render pass 1", this.roundAlphaBufferView, OptionalInt.empty())) {
                renderPass.setPipeline(ShaderManager.BLIT_SCREEN);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", main.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.draw(0, 3);
            }

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback round alpha render pass 2", main.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(ShaderManager.BLIT_SCREEN_ROUND_ALPHA);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindTexture("InSampler", this.roundAlphaBufferView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                renderPass.draw(0, 3);
            }
        }
    }

    @Inject(method = "submitBlockEntities", at = @At("HEAD"), cancellable = true)
    public void renderBlockEntities(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderBlocks) {
            ci.cancel();
        }
    }

    @WrapWithCondition(method = "submitEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    public boolean renderEntity(EntityRenderDispatcher instance, EntityRenderState entityRenderState, CameraRenderState cameraRenderState,
                                double d, double e, double f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (entityRenderState instanceof AvatarRenderState) {
            return editorState == null || editorState.replayVisuals.renderPlayers;
        } else {
            return editorState == null || editorState.replayVisuals.renderEntities;
        }
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;cloudStatus:Lnet/minecraft/client/CloudStatus;", opcode = Opcodes.GETFIELD), require = 0)
    public CloudStatus renderLevel_getCloudsType(OptionsRenderState instance, Operation<CloudStatus> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderSky) {
            return CloudStatus.OFF;
        } else {
            return original.call(instance);
        }
    }

    @WrapWithCondition(method = "extractLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;extract(Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V"))
    public boolean extractLevel_particleEngine_extract(ParticleEngine instance, ParticlesRenderState renderState, Frustum frustum, Camera camera, float partialTickTime) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderParticles) {
            renderState.reset();
            return false;
        }
        return true;
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    public void addSkyPass(CallbackInfo ci) {
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
