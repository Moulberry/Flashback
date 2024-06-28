package com.moulberry.flashback.mixin.client.replay_server;

import com.moulberry.flashback.FlashbackClient;
import net.minecraft.client.Minecraft;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TickRateManager.class)
public abstract class MixinTickRateManager {

    @Shadow
    protected boolean runGameElements;

    /*
     * Freeze the replay server, preventing game elements from running normally
     */

    @Inject(method = "tick", at = @At("RETURN"))
    public void tick(CallbackInfo ci) {
        if (FlashbackClient.isInReplay() && (Object)this instanceof ServerTickRateManager) {
            this.runGameElements = false;
        }
    }

}
