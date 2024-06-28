package com.moulberry.flashback.mixin.client.replay_server;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerEntity.class)
public class MixinServerEntity {

    /*
     * Force update interval to be 1 on a replay server
     */

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static int init_modifyUpdateInterval(int updateInterval, @Local(argsOnly = true) ServerLevel level) {
        if (level.getServer() instanceof ReplayServer) {
            return 1;
        }
        return updateInterval;
    }

}
