package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerEntity.class)
public class MixinServerEntity {

    /*
     * Force update interval to be 1 on a replay server, sending updates as soon as possible
     */

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static int init_modifyUpdateInterval(int updateInterval, @Local(argsOnly = true) ServerLevel level) {
        if (updateInterval < 20 && level != null && level.getServer() instanceof ReplayServer) {
            return 1;
        }
        return updateInterval;
    }

}
