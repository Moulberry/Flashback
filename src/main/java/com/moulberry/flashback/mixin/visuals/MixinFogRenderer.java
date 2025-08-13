package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    public void setupFog_updateBuffer(FogRenderer instance, ByteBuffer byteBuffer, int i, Vector4f colour, float environmentalStart, float environmentalEnd,
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
}
