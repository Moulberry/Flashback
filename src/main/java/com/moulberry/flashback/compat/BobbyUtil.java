package com.moulberry.flashback.compat;

import de.johni0702.minecraft.bobby.FakeChunkManager;
import de.johni0702.minecraft.bobby.ext.ClientChunkCacheExt;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public class BobbyUtil {
    public static void addBobbyChunks(ClientChunkCache clientChunkCache, List<LevelChunk> chunks, LongOpenHashSet seenChunkPositions) {
        FakeChunkManager bobbyChunkCache = ((ClientChunkCacheExt)clientChunkCache).bobby_getFakeChunkManager();
        if (bobbyChunkCache == null) return;
        for (var chunk : bobbyChunkCache.getFakeChunks()) {
            if (seenChunkPositions.add(chunk.getPos().pack())) {
                chunks.add(chunk);
            }
        }
    }
}
