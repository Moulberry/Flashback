package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.ReplayVisuals;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class MixinOptions {

    @Unique
    private OptionInstance<Integer> cachedOptionInstance = null;

    @Inject(method = "fov", at = @At("RETURN"), cancellable = true)
    public void fov(CallbackInfoReturnable<OptionInstance<Integer>> cir) {
        if (ReplayVisuals.overrideFov) {
            if (this.cachedOptionInstance == null || this.cachedOptionInstance.get() != Math.round(ReplayVisuals.overrideFovAmount)) {
                OptionInstance<Integer> delegate = cir.getReturnValue();
                this.cachedOptionInstance = new OptionInstance<>("options.fov", OptionInstance.noTooltip(), Options::genericValueLabel,
                        delegate.values(), delegate.codec(), Math.round(ReplayVisuals.overrideFovAmount), value -> {
                    ReplayVisuals.overrideFov = false;
                    delegate.set(value);
                });
            }

            cir.setReturnValue(this.cachedOptionInstance);
        }
    }

}
