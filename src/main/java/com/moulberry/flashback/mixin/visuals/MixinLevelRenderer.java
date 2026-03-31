package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method="renderLevel", at=@At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;addLateDebugPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4fc;)V",
        shift = At.Shift.BEFORE
    ))
    public void renderLevelPost(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker, boolean renderOutline,
        CameraRenderState cameraState, Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky,
        ChunkSectionsToRender chunkSectionsToRender, CallbackInfo ci,
        @Local FrameGraphBuilder frameGraphBuilder
    ) {
        if (!Flashback.isInReplay()) {
            return;
        }

        FramePass framePass = frameGraphBuilder.addPass("flashback_mod_pass");
        this.targets.main = framePass.readsAndWrites(this.targets.main);
        if (this.targets.translucent != null) {
            this.targets.translucent = framePass.readsAndWrites(this.targets.translucent);
        }
        if (this.targets.itemEntity != null) {
            this.targets.itemEntity = framePass.readsAndWrites(this.targets.itemEntity);
        }
        if (this.targets.particles != null) {
            this.targets.particles = framePass.readsAndWrites(this.targets.particles);
        }
        framePass.executes(() -> {
            this.renderBuffers.bufferSource().endBatch();

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(modelViewMatrix);

            // Set model view stack to identity
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();

            WorldRenderHook.renderHook(poseStack, cameraState);

            this.renderBuffers.bufferSource().endBatch();

            // Pop model view stack
            modelViewStack.popMatrix();
        });
    }

}
