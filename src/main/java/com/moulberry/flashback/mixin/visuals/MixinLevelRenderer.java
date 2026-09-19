package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow @Final private LevelTargetBundle targets;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V", shift = At.Shift.AFTER))
    public void renderLevelPost(GraphicsResourceAllocator resourceAllocator, boolean renderOutline,
        CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor,
        boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci, @Local FrameGraphBuilder frameGraphBuilder
    ) {
        if (!Flashback.isInReplay()) {
            return;
        }

        FramePass framePass = frameGraphBuilder.addPass("flashback_mod_pass");
        this.targets.main = framePass.readsAndWrites(this.targets.main);
        framePass.executes(() -> {
            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(cameraState.viewRotationMatrix);

            // Set model view stack to identity
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();

            WorldRenderHook.renderHook(poseStack, cameraState);

            // Pop model view stack
            modelViewStack.popMatrix();
        });
    }

}
