package com.moulberry.flashback.mixin.replay_server;

import com.moulberry.flashback.ext.ServerLevelExt;
import com.moulberry.flashback.playback.ReplayPlayer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class MixinServerLevel implements ServerLevelExt {

    @Unique
    private long seedHash = 0;

    @Override
    public void flashback$setSeedHash(long seedHash) {
        this.seedHash = seedHash;
    }

    @Override
    public long flashback$getSeedHash() {
        return this.seedHash;
    }

    @Unique
    private final LongSet validChunks = new LongOpenHashSet();

    @Override
    public boolean flashback$shouldSendChunk(long pos) {
        return this.validChunks.contains(pos);
    }

    @Override
    public void flashback$markChunkAsSendable(long pos) {
        this.validChunks.add(pos);
    }

    @Unique
    private boolean canSpawnEntities = true;

    @Override
    public void flashback$setCanSpawnEntities(boolean canSpawnEntities) {
        this.canSpawnEntities = canSpawnEntities;
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    public void addFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player) && !this.canSpawnEntities) {
            cir.setReturnValue(false);
        }
    }

}
