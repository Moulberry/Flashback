package com.moulberry.flashback.mixin.client.replay_server;

import com.moulberry.flashback.FlashbackClient;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public class MixinChunkStatusTasks {

    @Inject(method = "initializeLight", at = @At("HEAD"), cancellable = true)
    private static void initializeLight(ThreadedLevelLightEngine threadedLevelLightEngine, ChunkAccess chunkAccess, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (FlashbackClient.isInReplay()) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunkAccess));
        }
    }

    @Inject(method = "lightChunk", at = @At("HEAD"), cancellable = true)
    private static void lightChunk(ThreadedLevelLightEngine threadedLevelLightEngine, ChunkAccess chunkAccess, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (FlashbackClient.isInReplay()) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunkAccess));
        }
    }

}
