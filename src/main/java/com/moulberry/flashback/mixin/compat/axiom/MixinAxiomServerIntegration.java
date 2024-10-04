package com.moulberry.flashback.mixin.compat.axiom;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfModLoaded("axiom")
@Pseudo
@Mixin(targets = "com.moulberry.axiom.integration.ServerIntegration", remap = false)
public class MixinAxiomServerIntegration {

    @Inject(method = "changeGameMode", at = @At("HEAD"), cancellable = true, require = 0)
    private static void changeGameMode(CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

}
