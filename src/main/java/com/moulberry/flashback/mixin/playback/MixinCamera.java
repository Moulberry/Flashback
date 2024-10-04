package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    private float eyeHeightOld;

    @Shadow
    public float eyeHeight;

    @Shadow
    protected abstract void setRotation(float f, float g);

    @Shadow
    protected abstract void setPosition(double d, double e, double f);

    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    public void afterSetPosition(BlockGetter blockGetter, Entity entity, boolean bl, boolean bl2, float partialTick, CallbackInfo ci)  {
        Vector2f rotation = AccurateEntityPositionHandler.getAccurateRotation(entity, partialTick);
        if (rotation != null) {
            this.setRotation(rotation.y, rotation.x);
        }
        Vector3d position = AccurateEntityPositionHandler.getAccuratePosition(entity, partialTick);
        if (position != null) {
            this.setPosition(position.x, position.y + Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight), position.z);
        }
    }

}
