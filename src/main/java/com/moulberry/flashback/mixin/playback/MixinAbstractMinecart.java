package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public class MixinAbstractMinecart {

    @Shadow
    private int lerpSteps;

    @Inject(method = "lerpTo", at = @At("RETURN"))
    public void modifyLerpTime(double d, double e, double f, float g, float h, int i, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            this.lerpSteps = 3;
        }
    }

}
