package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void setupFog(Camera camera, FogRenderer.FogMode fogMode, Vector4f vector4f, float f, boolean bl, float g, CallbackInfoReturnable<FogParameters> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFog) {
            FogParameters returnParameters = cir.getReturnValue();
            if (returnParameters != null) {
                cir.setReturnValue(new FogParameters(editorState.replayVisuals.overrideFogStart,
                        editorState.replayVisuals.overrideFogEnd, FogShape.SPHERE, returnParameters.red(),
                        returnParameters.green(), returnParameters.blue(), returnParameters.alpha()));
            }
        }
    }

}
