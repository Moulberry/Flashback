package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class MixinLightTexture {

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"), require = 0)
    public boolean updateLightTexture_hasEffect(LocalPlayer instance, Holder holder, Operation<Boolean> original) {
        if (holder == MobEffects.NIGHT_VISION) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && editorState.replayVisuals.overrideNightVision) {
                return true;
            }
        }
        return original.call(instance, holder);
    }

}
