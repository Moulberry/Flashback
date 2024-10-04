package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class MixinClientLevel {

    /*
     * Some entities have weird behaviour when rotating in a replay
     * This code here ensures that the interpolation of yRotO -> yRot will always be the shortest path
     */

    @Inject(method = "tickNonPassenger", at = @At("RETURN"), require = 0)
    public void tickNonPassengerEnd(Entity entity, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            entity.xRotO = Mth.wrapDegrees(entity.xRotO - entity.getXRot()) + entity.getXRot();
            entity.yRotO = Mth.wrapDegrees(entity.yRotO - entity.getYRot()) + entity.getYRot();
        }
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"), require = 0)
    public void tickPassengerEnd(Entity vehicle, Entity entity, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            entity.xRotO = Mth.wrapDegrees(entity.xRotO - entity.getXRot()) + entity.getXRot();
            entity.yRotO = Mth.wrapDegrees(entity.yRotO - entity.getYRot()) + entity.getYRot();
        }
    }

}
