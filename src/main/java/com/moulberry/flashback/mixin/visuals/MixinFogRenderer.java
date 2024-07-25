package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.ReplayVisuals;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void setupFog(Camera camera, FogRenderer.FogMode fogMode, float f, boolean bl, float g, CallbackInfo ci) {
        if (ReplayVisuals.overrideFog) {
            RenderSystem.setShaderFogStart(ReplayVisuals.overrideFogStart);
            RenderSystem.setShaderFogEnd(ReplayVisuals.overrideFogEnd);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            ci.cancel();
        }
    }

}
