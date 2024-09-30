package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true, require = 0)
    private static void setupFog(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFog) {
            RenderSystem.setShaderFogStart(editorState.replayVisuals.overrideFogStart);
            RenderSystem.setShaderFogEnd(editorState.replayVisuals.overrideFogEnd);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            ci.cancel();
        }
    }

}
