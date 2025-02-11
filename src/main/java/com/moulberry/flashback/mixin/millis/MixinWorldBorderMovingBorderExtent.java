package com.moulberry.flashback.mixin.millis;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldBorder.MovingBorderExtent.class)
public class MixinWorldBorderMovingBorderExtent {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 0)
    private long setupMovingBorderExtent(Operation<Long> original) {
        if (Flashback.isInReplay()) {
            if (Flashback.worldBorderLerpStartTime != -1L) {
                long time = Flashback.worldBorderLerpStartTime;
                Flashback.worldBorderLerpStartTime = -1L;
                return time;
            }

            return Flashback.getVisualMillis();
        }
        return original.call();
    }

    @WrapOperation(method = "getSize", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 0)
    private long getSize(Operation<Long> original) {
        if (Flashback.isInReplay()) {
            return Flashback.getVisualMillis();
        }
        return original.call();
    }

    @WrapOperation(method = "getLerpRemainingTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 0)
    private long getLerpRemainingTime(Operation<Long> original) {
        if (Flashback.isInReplay()) {
            return Flashback.getVisualMillis();
        }
        return original.call();
    }

}
