package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
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

    @Inject(method="renderLevel", at=@At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/Options;getCloudsType()Lnet/minecraft/client/CloudStatus;",
        shift = At.Shift.BEFORE
    ))
    public void renderLevelPost(net.minecraft.client.DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
        Matrix4f matrix4f, Matrix4f projection, CallbackInfo ci) {

        if (!Flashback.isInReplay()) {
            return;
        }

        this.renderBuffers.bufferSource().endBatch();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(matrix4f);

        // Set model view stack to identity
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);
        WorldRenderHook.renderHook(poseStack, tickDelta, renderBlockOutline, camera, gameRenderer, lightTexture, projection);

        this.renderBuffers.bufferSource().endBatch();

        // Pop model view stack
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

}
