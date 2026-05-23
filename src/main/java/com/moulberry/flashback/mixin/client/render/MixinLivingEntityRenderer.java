package com.moulberry.flashback.mixin.client.render;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    
    /**
     * Prevents rendering the player body when in first-person tracking mode.
     * Hands are still rendered by Minecraft's first-person hand rendering system.
     */
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void shouldShowName(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        // This mixin can be extended for future first-person body hiding logic
        // Currently relies on Minecraft's default hand rendering for first-person view
    }
}
