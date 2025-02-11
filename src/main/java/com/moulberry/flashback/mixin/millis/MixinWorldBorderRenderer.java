package com.moulberry.flashback.mixin.millis;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldBorderRenderer.class)
public class MixinWorldBorderRenderer {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 0)
    public long render(Operation<Long> original) {
        if (Flashback.isInReplay()) {
            return Flashback.getVisualMillis();
        }
        return original.call();
    }

}
