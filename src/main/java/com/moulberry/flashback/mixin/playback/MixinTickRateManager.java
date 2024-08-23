package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TickRateManager.class)
public abstract class MixinTickRateManager {

    @Shadow
    public abstract boolean runsNormally();

    @Shadow
    protected boolean runGameElements;

    /*
     * Freeze the replay server, preventing game elements from running normally
     */

    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(CallbackInfo ci) {
        if (Flashback.isInReplay() && this.isServerTickRateManager) {
            this.runGameElements = false;
        }
    }

    @Unique
    private final boolean isServerTickRateManager = ((Object)this instanceof ServerTickRateManager);

    @Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
    public void isEntityFrozen(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            if (this.isServerTickRateManager) {
                cir.setReturnValue(!(entity instanceof ReplayPlayer));
            } else {
                if (Flashback.isExporting()) {
                    cir.setReturnValue(false);
                } else {
                    cir.setReturnValue(!this.runsNormally() || entity == Minecraft.getInstance().player);
                }
            }
        }
    }

}
