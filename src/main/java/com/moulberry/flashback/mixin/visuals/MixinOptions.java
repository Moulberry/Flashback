package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.ext.OptionsExt;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class MixinOptions implements OptionsExt {

    @Shadow
    @Final
    private OptionInstance<Integer> fov;
    @Unique
    private OptionInstance<Integer> cachedFovOptionInstance = null;

    @Shadow
    @Final
    private OptionInstance<Double> gamma;
    @Unique
    private OptionInstance<Double> cachedGammaOptionInstance = null;

    @Inject(method = "fov", at = @At("RETURN"), cancellable = true)
    public void fov(CallbackInfoReturnable<OptionInstance<Integer>> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideFov) {
            if (this.cachedFovOptionInstance == null || this.cachedFovOptionInstance.get() != Math.round(editorState.replayVisuals.overrideFovAmount)) {
                OptionInstance<Integer> delegate = cir.getReturnValue();
                this.cachedFovOptionInstance = new OptionInstance<>("options.fov", OptionInstance.noTooltip(), Options::genericValueLabel,
                    delegate.values(), delegate.codec(), Math.round(editorState.replayVisuals.overrideFovAmount), value -> {
                    editorState.replayVisuals.overrideFov = false;
                    delegate.set(value);
                });
            }

            cir.setReturnValue(this.cachedFovOptionInstance);
        }
    }

    @Override
    public int flashback$getOriginalFov() {
        return this.fov.get();
    }

    @Inject(method = "gamma", at = @At("RETURN"), cancellable = true)
    public void gamma(CallbackInfoReturnable<OptionInstance<Double>> cir) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState != null && editorState.replayVisuals.overrideGamma) {
            if (this.cachedGammaOptionInstance == null || this.cachedGammaOptionInstance.get()
                    != (double) editorState.replayVisuals.overrideGammaAmount) {
                OptionInstance<Double> delegate = cir.getReturnValue();
                this.cachedGammaOptionInstance = new OptionInstance<>(
                        "options.gamma", OptionInstance.noTooltip(), (component, double_) -> {
                            return Component.translatable("options.percent_value",
                                                          component, (int) (double_ * 100.0));
                        },
                    delegate.values(), delegate.codec(),
                    (double) editorState.replayVisuals.overrideGammaAmount,
                    value -> {
                        editorState.replayVisuals.overrideGamma = false;
                        delegate.set((double) value);
                    });
            }

            cir.setReturnValue(this.cachedGammaOptionInstance);
        }
    }

    @Override
    public double flashback$getOriginalGamma() {
        return this.gamma.get();
    }
}
