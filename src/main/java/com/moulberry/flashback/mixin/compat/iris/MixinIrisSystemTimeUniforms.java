package com.moulberry.flashback.mixin.compat.iris;

import com.moulberry.flashback.Flashback;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfModLoaded("iris")
@Pseudo
@Mixin(value = SystemTimeUniforms.Timer.class, remap = false)
public class MixinIrisSystemTimeUniforms {

    @Inject(method = "getFrameTimeCounter", at = @At("HEAD"), require = 0, cancellable = true)
    public void getFrameTimeCounter(CallbackInfoReturnable<Float> cir) {
        if (Flashback.isExporting()) {
            double currentTick = Flashback.EXPORT_JOB.getCurrentTickDouble();
            cir.setReturnValue((float)(currentTick / 20));
        }
    }

    @Inject(method = "getLastFrameTime", at = @At("HEAD"), require = 0, cancellable = true)
    public void getLastFrameTime(CallbackInfoReturnable<Float> cir) {
        if (Flashback.isExporting()) {
            cir.setReturnValue((float)(1 / Flashback.EXPORT_JOB.getSettings().framerate()));
        }
    }

}
