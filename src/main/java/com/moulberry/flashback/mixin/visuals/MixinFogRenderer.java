package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
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

    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    public FogData setupFog(FogData fogData) {
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
                float[] fogColour = visuals.fogColour;
                fogData.color.set(fogColour[0], fogColour[1], fogColour[2], 1.0F);
            }
        }
        return fogData;
    }

}
