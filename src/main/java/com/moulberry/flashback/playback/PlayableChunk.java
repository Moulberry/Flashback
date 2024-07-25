package com.moulberry.flashback.playback;

import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlayableChunk {

    public final FlashbackChunkMeta chunkMeta;
    public final Path path;
    private ReplayReader replayReader;

    public PlayableChunk(FlashbackChunkMeta chunkMeta, Path path) {
        this.chunkMeta = chunkMeta;
        this.path = path;
    }

    public ReplayReader getOrLoadReplayReader(RegistryAccess registryAccess) {
        if (this.replayReader == null) {
            try {
                byte[] bytes = Files.readAllBytes(this.path);
                this.replayReader = new ReplayReader(Unpooled.wrappedBuffer(bytes), registryAccess);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        this.replayReader.changeRegistryAccess(registryAccess);
        return this.replayReader;
    }



}
