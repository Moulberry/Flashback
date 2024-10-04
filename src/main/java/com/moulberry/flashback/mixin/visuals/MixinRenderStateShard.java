package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderStateShard.class)
public class MixinRenderStateShard {

    @WrapOperation(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 0)
    private static long setupGlintTexturing(Operation<Long> original) {
        if (Flashback.isExporting()) {
            double currentTick = Flashback.EXPORT_JOB.getCurrentTickDouble();
            return (long)(currentTick / 20 * 1000);
        }
        return original.call();
    }

}
