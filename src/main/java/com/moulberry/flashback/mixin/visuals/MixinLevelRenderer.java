package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.sugar.Local;
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
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
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
        target = "Lnet/minecraft/client/Options;getCloudsType()Lnet/minecraft/client/CloudStatus;",
        shift = At.Shift.BEFORE
    ))
    public void renderLevelPost(GraphicsResourceAllocator graphicsResourceAllocator, net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                Matrix4f matrix4f, Matrix4f projection, CallbackInfo ci, @Local FrameGraphBuilder frameGraphBuilder) {
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
        ResourceHandle<RenderTarget> resourceHandle = this.targets.main;
        framePass.executes(() -> {
            this.renderBuffers.bufferSource().endBatch();
            resourceHandle.get().bindWrite(false); // Ensure main framebuffer is bound, for some reason this isn't the case with Fabulous in 1.21.4+

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(matrix4f);

            // Set model view stack to identity
            var modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewStack.identity();

            float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
            WorldRenderHook.renderHook(poseStack, tickDelta, renderBlockOutline, camera, gameRenderer, projection);

            this.renderBuffers.bufferSource().endBatch();

            // Pop model view stack
            modelViewStack.popMatrix();
        });
    }

}
