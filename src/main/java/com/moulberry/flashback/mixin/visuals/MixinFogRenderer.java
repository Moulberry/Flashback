package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(value = FogRenderer.class, priority = 900) // Priority 900 so we inject before Sodium
public class MixinFogRenderer {

    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    public void setupFog_updateBufferWrap(FogRenderer instance, ByteBuffer byteBuffer, int i, Vector4f colour, float environmentalStart, float environmentalEnd,
            float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudEnd, Operation<Void> original) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;
            if (visuals.overrideFog) {
                environmentalStart = visuals.overrideFogStart;
                environmentalEnd = visuals.overrideFogEnd;
                renderDistanceStart = visuals.overrideFogStart;
                renderDistanceEnd = visuals.overrideFogEnd;
                skyEnd = visuals.overrideFogEnd;
                cloudEnd = visuals.overrideFogEnd;
            }
            if (visuals.overrideFogColour) {
                float[] fogColour = visuals.fogColour;
                colour.set(fogColour[0], fogColour[1], fogColour[2], 1.0F);
            }
        }
        original.call(instance, byteBuffer, i, colour, environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd, skyEnd, cloudEnd);
    }


    @Inject(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    public void setupFog_updateBufferInject(Camera camera, int i, DeltaTracker deltaTracker, float f, ClientLevel clientLevel, CallbackInfoReturnable<Vector4f> cir,
        @Local FogData fogData, @Local Vector4f fogColour) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            ReplayVisuals visuals = editorState.replayVisuals;
            if (visuals.overrideFog) {
                fogData.environmentalStart = visuals.overrideFogStart;
                fogData.environmentalEnd = visuals.overrideFogEnd;
                fogData.renderDistanceStart = visuals.overrideFogStart;
                fogData.renderDistanceEnd = visuals.overrideFogEnd;
                fogData.skyEnd = visuals.overrideFogEnd;
                fogData.cloudEnd = visuals.overrideFogEnd;
            }
            if (visuals.overrideFogColour) {
                float[] newColour = visuals.fogColour;
                fogColour.set(newColour[0], newColour[1], newColour[2], 1.0F);
            }
        }
    }
}
