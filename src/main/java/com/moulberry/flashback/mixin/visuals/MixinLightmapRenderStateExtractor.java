package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapRenderStateExtractor {

    @Inject(method = "extract", at = @At("RETURN"))
    public void extractReturn(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (!renderState.needsUpdate) {
            return;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideNightVision) {
            renderState.nightVisionEffectIntensity = 1.0f;
        }
    }

}
