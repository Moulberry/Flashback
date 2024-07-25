package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkSender.class)
public class MixinPlayerChunkSender {

    // Make chunks send faster on replay servers

    @Shadow
    private float desiredChunksPerTick;

    @Shadow
    private int unacknowledgedBatches;

    @Shadow
    private float batchQuota;

    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    public void sendNextChunks(ServerPlayer serverPlayer, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            this.unacknowledgedBatches = 0;
            this.batchQuota = 0.0f;
            this.desiredChunksPerTick = Math.max(this.desiredChunksPerTick, 256.0f);
        }
    }

}
