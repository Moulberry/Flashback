package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.Utils;
import com.moulberry.flashback.ext.OptionsExt;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    // Reduces sensitivity when overriding fov to a lower value
    // This makes the camera much easier to control with low fov values

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;sensitivity()Lnet/minecraft/client/OptionInstance;"))
    public OptionInstance<Double> turnPlayer_sensitivity(Options instance, Operation<OptionInstance<Double>> original) {
        OptionInstance<Double> sensitivity = original.call(instance);
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFov) {
            float newFov = editorState.replayVisuals.overrideFovAmount;
            if (newFov > 20.0) {
                return original.call(instance);
            }
            float oldFov = ((OptionsExt)instance).flashback$getOriginalFov();

            if (newFov < oldFov) {
                float newFocalLength = Utils.fovToFocalLength(newFov);
                float oldFocalLength = Utils.fovToFocalLength(oldFov);

                float sensitivityMultiplier = oldFocalLength / newFocalLength;
                double newSensitivity = sensitivity.get() * sensitivityMultiplier;

                sensitivity = new OptionInstance<>("options.sensitivity", OptionInstance.noTooltip(), (component, double_) -> {
                    if (double_ == 0.0) {
                        return Options.genericValueLabel(component, Component.translatable("options.sensitivity.min"));
                    }
                    if (double_ == 1.0) {
                        return Options.genericValueLabel(component, Component.translatable("options.sensitivity.max"));
                    }
                    return Component.translatable("options.percent_value", component, (int)(double_ * 200.0));
                }, OptionInstance.UnitDouble.INSTANCE, newSensitivity, double_ -> {});
            }
        }
        return sensitivity;
    }

}
