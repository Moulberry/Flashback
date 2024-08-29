package com.moulberry.flashback.mixin.replay_server;

import com.moulberry.flashback.ext.ServerLevelExt;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

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

}
