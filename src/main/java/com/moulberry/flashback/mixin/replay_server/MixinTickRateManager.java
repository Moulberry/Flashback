package com.moulberry.flashback.mixin.replay_server;

import com.moulberry.flashback.Flashback;
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



    @Inject(method = "isEntityFrozen", at = @At("RETURN"))
    public void isEntityFrozen(Entity entity, CallbackInfoReturnable<Boolean> cir) {

    }

}
