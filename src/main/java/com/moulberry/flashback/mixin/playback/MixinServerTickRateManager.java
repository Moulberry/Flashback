package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.ext.ServerTickRateManagerExt;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.TickRateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerTickRateManager.class)
public abstract class MixinServerTickRateManager extends TickRateManager implements ServerTickRateManagerExt {

    @Shadow
    protected abstract void updateStateToClients();

    @Unique
    private boolean suppressClientUpdates = false;
    @Unique
    private boolean previousFrozen = false;
    @Unique
    private float previousTickrate = 20.0f;

    @Override
    public void flashback$setSuppressClientUpdates(boolean suppress) {
        if (this.suppressClientUpdates == suppress) {
            return;
        }
        this.suppressClientUpdates = suppress;
        if (suppress) {
            this.previousFrozen = this.isFrozen;
            this.previousTickrate = this.tickrate;
        } else {
            if (this.previousFrozen != this.isFrozen || this.previousTickrate != this.tickrate) {
                this.updateStateToClients();
            }
        }
    }

    @Inject(method = "updateStateToClients", at = @At("HEAD"), cancellable = true)
    public void updateStateToClients(CallbackInfo ci) {
        if (this.suppressClientUpdates) {
            ci.cancel();
        }
    }
}
