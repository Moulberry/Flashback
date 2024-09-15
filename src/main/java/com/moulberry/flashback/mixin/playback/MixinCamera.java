package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public class MixinCamera {

    @Shadow
    private float eyeHeightOld;

    @Shadow
    public float eyeHeight;

    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
    public void setRotation(Camera instance, float yaw, float pitch, Operation<Void> original,
            @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) float partialTick) {
        Vector2f rotation = AccurateEntityPositionHandler.getAccurateRotation(entity, partialTick);
        if (rotation != null) {
            pitch = rotation.x;
            yaw = rotation.y;
        }
        original.call(instance, yaw, pitch);
    }


    @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", ordinal = 0))
    public void setPosition(Camera instance, double x, double y, double z, Operation<Void> original,
        @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) float partialTick) {
        Vector3d position = AccurateEntityPositionHandler.getAccuratePosition(entity, partialTick);
        if (position != null) {
            x = position.x;
            y = position.y + Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight);
            z = position.z;
        }
        original.call(instance, x, y, z);
    }

}
