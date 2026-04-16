package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import com.moulberry.flashback.visuals.CameraRotation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

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

    @Shadow
    private @Nullable Entity entity;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private int matrixPropertiesDirty;

    @Inject(method = "update", at = @At(value = "RETURN"))
    public void afterSetPosition(DeltaTracker deltaTracker, CallbackInfo ci)  {
        if (this.entity == null) {
            return;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        Vector2f rotation = AccurateEntityPositionHandler.getAccurateRotation(this.entity, partialTick);
        if (rotation != null) {
            this.setRotation(rotation.y, rotation.x);
        }
        Vector3d position = AccurateEntityPositionHandler.getAccuratePosition(this.entity, partialTick);
        if (position != null) {
            this.setPosition(position.x, position.y + Mth.lerp(partialTick, this.eyeHeightOld, this.eyeHeight), position.z);
        }
    }

    @WrapMethod(method = "getCameraEntityPartialTicks")
    public float getCameraEntityPartialTicks(DeltaTracker deltaTracker, Operation<Float> original) {
        float originalPartialTick = original.call(deltaTracker);
        if (Flashback.isInReplay() && this.entity == this.minecraft.player) {
            return ((MinecraftExt)this.minecraft).flashback$getLocalPlayerPartialTick(originalPartialTick);
        }
        return originalPartialTick;
    }

    @WrapMethod(method = "rotation")
    public Quaternionf rotation(Operation<Quaternionf> original) {
        Quaternionf originalQuaternion = original.call();
        Quaternionf modifiedQuaternion = CameraRotation.modifyViewQuaternion(originalQuaternion);
        if (!Objects.equals(originalQuaternion, modifiedQuaternion)) {
            this.matrixPropertiesDirty |= 3;
        }
        return modifiedQuaternion;
    }

    @WrapMethod(method = "calculateFov")
    public float calculateFov(float partialTicks, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && editorState.replayVisuals.overrideFov) {
                return editorState.replayVisuals.overrideFovAmount;
            } else {
                return this.minecraft.options.fov().get().intValue();
            }
        }

        return original.call(partialTicks);
    }

}
