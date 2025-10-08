package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.client.Options;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void tick(CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            this.ticks = replayServer.getReplayTick();
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    public void renderLevel(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera,
            Matrix4f matrix4f, Matrix4f matrix4f2, Matrix4f projection, GpuBufferSlice gpuBufferSlice, Vector4f clearColour, boolean bl2, CallbackInfo ci) {
        ReplayUI.lastProjectionMatrix = projection;
        ReplayUI.lastViewQuaternion = camera.rotation();

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;

            if (!visuals.renderSky) {
                if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
                    clearColour.set(0f, 0f, 0f, 0f);
                } else {
                    float[] skyColour = visuals.skyColour;
                    clearColour.set(skyColour[0], skyColour[1], skyColour[2], 1f);
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

    @Inject(method = "renderBlockDestroyAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderBlockDestroyAnimation(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                ci.cancel();
            }
        }
    }

    @WrapOperation(method = "method_62214", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;)V"))
    public void method_62214_renderChunkGroup(ChunkSectionsToRender instance, ChunkSectionLayerGroup chunkSectionLayerGroup, Operation<Void> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                return;
            }
        }

        original.call(instance, chunkSectionLayerGroup);

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
                renderPass.bindSampler("InSampler", main.getColorTextureView());
                renderPass.draw(0, 3);
            }

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback round alpha render pass 2", main.getColorTextureView(), OptionalInt.empty())) {
                renderPass.setPipeline(ShaderManager.BLIT_SCREEN_ROUND_ALPHA);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.bindSampler("InSampler", this.roundAlphaBufferView);
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

    @WrapWithCondition(method = "submitEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    public boolean renderEntity(EntityRenderDispatcher instance, EntityRenderState entityRenderState, CameraRenderState cameraRenderState,
                                double d, double e, double f, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (entityRenderState instanceof AvatarRenderState) {
            return editorState == null || editorState.replayVisuals.renderPlayers;
        } else {
            return editorState == null || editorState.replayVisuals.renderEntities;
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
    public void addParticlesPass(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderParticles) {
            ci.cancel();
        }
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
