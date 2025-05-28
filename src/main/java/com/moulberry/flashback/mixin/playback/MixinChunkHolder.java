package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.LevelChunkExt;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(value = ChunkHolder.class, priority = 1100)
public class MixinChunkHolder {

    @Shadow
    private boolean hasChangedSections;

    @Shadow
    @Final
    private BitSet skyChangedLightSectionFilter;

    @Shadow
    @Final
    private BitSet blockChangedLightSectionFilter;

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    public void broadcastChanges(LevelChunk levelChunk, CallbackInfo ci) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null && levelChunk instanceof LevelChunkExt levelChunkExt) {
            if (this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
                levelChunkExt.flashback$setCachedChunkId(-1);
            }
        }
    }

}
