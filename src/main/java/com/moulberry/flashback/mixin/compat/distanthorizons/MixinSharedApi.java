package com.moulberry.flashback.mixin.compat.distanthorizons;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("distanthorizons")
@Mixin(value = SharedApi.class, remap = false)
public abstract class MixinSharedApi {

    /*
     * Override any changes to the DhWorld, changing it to our proxy world
     */

//    @Inject(method = "setDhWorld", at = @At("HEAD"), cancellable = true)
//    private static void setDhWorld(AbstractDhWorld newWorld, CallbackInfo ci) {
//        if (Flashback.isInReplay() && newWorld != null && !(newWorld instanceof ProxyDhWorld)) {
//            SharedApi.setDhWorld(new ProxyDhWorld());
//            ci.cancel();
//        }
//    }

}
