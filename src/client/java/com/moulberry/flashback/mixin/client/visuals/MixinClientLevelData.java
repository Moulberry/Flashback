package com.moulberry.flashback.mixin.client.visuals;

import com.moulberry.flashback.ReplayVisuals;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.ClientLevelData.class)
public class MixinClientLevelData {

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    public void getDayTime(CallbackInfoReturnable<Long> cir) {
        if (ReplayVisuals.overrideTimeOfDay >= 0) {
            cir.setReturnValue(ReplayVisuals.overrideTimeOfDay);
        }
    }

}
