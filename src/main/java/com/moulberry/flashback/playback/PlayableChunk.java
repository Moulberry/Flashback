package com.moulberry.flashback.playback;

import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlayableChunk {

    public final FlashbackChunkMeta chunkMeta;
    public final Path path;
    private SoftReference<ReplayReader> replayReader;

    public PlayableChunk(FlashbackChunkMeta chunkMeta, Path path) {
        this.chunkMeta = chunkMeta;
        this.path = path;
    }

    public ReplayReader getOrLoadReplayReader(RegistryAccess registryAccess) {
        ReplayReader replayReader = this.replayReader == null ? null : this.replayReader.get();

        if (replayReader == null) {
            try {
                byte[] bytes = Files.readAllBytes(this.path);
                replayReader = new ReplayReader(Unpooled.wrappedBuffer(bytes), registryAccess);
                this.replayReader = new SoftReference<>(replayReader);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        replayReader.changeRegistryAccess(registryAccess);
        return replayReader;
    }



}
