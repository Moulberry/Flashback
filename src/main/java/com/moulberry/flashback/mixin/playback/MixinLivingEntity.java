package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    // Prevent invisible/glowing state from being updated based on potion effects inside a replay

    @Inject(method = "updateDirtyEffects", at = @At("HEAD"), cancellable = true)
    public void updateDirtyEffects(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateInvisibilityStatus", at = @At("HEAD"), cancellable = true)
    public void updateInvisibilityStatus(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateGlowingStatus", at = @At("HEAD"), cancellable = true)
    public void updateGlowingStatus(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
