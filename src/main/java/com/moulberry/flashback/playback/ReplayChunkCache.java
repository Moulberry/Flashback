package com.moulberry.flashback.playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ReplayChunkCache {

    public static final int CHUNK_CACHE_SIZE = 10000;

    private final Int2ObjectMap<SoftReference<List<ClientboundLevelChunkWithLightPacket>>> levelChunkCachedPackets = new Int2ObjectOpenHashMap<>();
    private final IntSet missingChunkCacheFiles = new IntOpenHashSet();
    private final FileSystem playbackFileSystem;

    public ReplayChunkCache(FileSystem playbackFileSystem) {
        this.playbackFileSystem = playbackFileSystem;
    }

    public void clear() {
        this.levelChunkCachedPackets.clear();
    }

    @Nullable
    public ClientboundLevelChunkWithLightPacket getOrLoad(int index, RegistryAccess registryAccess, StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec) {
        int cacheIndex = index / CHUNK_CACHE_SIZE;
        SoftReference<List<ClientboundLevelChunkWithLightPacket>> packetListReference = this.levelChunkCachedPackets.get(cacheIndex);
        List<ClientboundLevelChunkWithLightPacket> packets = packetListReference == null ? null : packetListReference.get();

        if (packets == null) {
            if (this.missingChunkCacheFiles.contains(cacheIndex)) {
                return null;
            }
            String pathString = "/level_chunk_caches/" + cacheIndex;
            Path levelChunkCachePath = this.playbackFileSystem.getPath(pathString);
            if (Files.exists(levelChunkCachePath)) {
                try {
                    packets = loadLevelChunkCache(levelChunkCachePath, registryAccess, gamePacketCodec);
                    this.levelChunkCachedPackets.put(cacheIndex, new SoftReference<>(packets));
                    Flashback.LOGGER.info("Loaded {} with {} entries", pathString, packets.size());
                } catch (IOException e) {
                    SneakyThrow.sneakyThrow(e);
                }
            } else {
                Flashback.LOGGER.error("Chunk index {} was requested, but cache file {} was missing", index, pathString);
                this.missingChunkCacheFiles.add(cacheIndex);
                return null;
            }
        }

        return index < packets.size() ? packets.get(index) : null;
    }

    private static List<ClientboundLevelChunkWithLightPacket> loadLevelChunkCache(Path levelChunkCachePath, RegistryAccess registryAccess, StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> gamePacketCodec) throws IOException {
        List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>();

        try (InputStream is = Files.newInputStream(levelChunkCachePath)) {
            while (true) {
                byte[] sizeBuffer = is.readNBytes(4);
                if (sizeBuffer.length < 4) {
                    break;
                }

                int size = (sizeBuffer[0] & 0xff) << 24 |
                        (sizeBuffer[1] & 0xff) << 16 |
                        (sizeBuffer[2] & 0xff) <<  8 |
                        sizeBuffer[3] & 0xff;

                byte[] chunk = is.readNBytes(size);
                if (chunk.length < size) {
                    Flashback.LOGGER.error("Ran out of bytes while reading level_chunk_cache, needed {}, had {}",
                            size, chunk.length);
                    break;
                }

                RegistryFriendlyByteBuf registryFriendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(chunk), registryAccess);

                try {
                    Packet<?> packet = gamePacketCodec.decode(registryFriendlyByteBuf);
                    if (packet instanceof ClientboundLevelChunkWithLightPacket levelChunkWithLightPacket) {
                        packets.add(levelChunkWithLightPacket);
                    } else {
                        throw new IllegalStateException("Level chunk cache contains wrong packet: " + packet);
                    }
                } catch (Exception e) {
                    Flashback.LOGGER.error("Encountered error while reading level_chunk_cache", e);
                }
            }
        }

        return packets;
    }

}
