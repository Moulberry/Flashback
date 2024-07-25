package com.moulberry.flashback.mixin.replay_server;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    public void save(boolean bl, CallbackInfo ci) {
        if (this.level.getServer() instanceof ReplayServer) {
            ci.cancel();
        }
    }

}
