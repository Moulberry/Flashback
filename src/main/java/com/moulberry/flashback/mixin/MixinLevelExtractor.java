package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public class MixinLevelExtractor {

    @Inject(method = "extractBlockDestroyAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    public void renderBlockDestroyAnimation(CallbackInfo ci) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null) {
            if (!editorState.replayVisuals.renderBlocks) {
                ci.cancel();
            }
        }
    }

    @WrapWithCondition(method = "extract", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;extract(Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V"))
    public boolean extractLevel_particleEngine_extract(ParticleEngine instance, ParticlesRenderState renderState, Frustum frustum, Camera camera, float partialTickTime) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && !editorState.replayVisuals.renderParticles) {
            renderState.reset();
            return false;
        }
        return true;
    }

}
