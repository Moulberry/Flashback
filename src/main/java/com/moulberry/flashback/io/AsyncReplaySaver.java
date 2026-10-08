package com.moulberry.flashback.io;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.CachedChunkPacket;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.TempFolderProvider;
import com.moulberry.flashback.action.ActionGamePacket;
import com.moulberry.flashback.action.ActionLevelChunkCached;
import com.moulberry.flashback.compat.valkyrienskies.ValkyrienSkiesSupport;
import com.moulberry.flashback.playback.ReplayServer;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class AsyncReplaySaver {

    private final ArrayBlockingQueue<Consumer<ReplayWriter>> tasks = new ArrayBlockingQueue<>(1024);
    private final AtomicReference<Throwable> error = new AtomicReference<>(null);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);
    private final AtomicBoolean hasStopped = new AtomicBoolean(false);

    private final Path recordFolder;

    public AsyncReplaySaver() {
        this.recordFolder = TempFolderProvider.createTemp(TempFolderProvider.TempFolderType.RECORDING,
                UUID.randomUUID());

        ReplayWriter replayWriter = new ReplayWriter();
        new Thread(() -> {
            while (true) {
                try {
                    Consumer<ReplayWriter> task = this.tasks.poll(10, TimeUnit.MILLISECONDS);

                    if (task == null) {
                        if (this.shouldStop.get()) {
                            this.hasStopped.set(true);
                            return;
                        } else {
                            continue;
                        }
                    }

                    task.accept(replayWriter);
                } catch (Throwable t) {
                    this.error.set(t);
                    this.hasStopped.set(true);
                    return;
                }
            }
        }).start();
    }

    
    public void submit(Consumer<ReplayWriter> consumer) {
        this.checkForError();

        if (this.hasStopped.get()) {
            throw new IllegalStateException("Cannot submit task to AsyncReplayWriter that has already stopped");
        }

        while (true) {
            try {
                this.tasks.put(consumer);
                break;
            } catch (InterruptedException ignored) {
            }
        }
    }

    private final Int2ObjectMap<List<CachedChunkPacket>> cachedChunkPackets = new Int2ObjectOpenHashMap<>();
    private int totalWrittenChunkPackets = 0;

    public void writeGamePackets(List<Packet<? super ClientGamePacketListener>> packets) {
        List<Packet<? super ClientGamePacketListener>> packetCopy = new ArrayList<>(packets);
        this.submit(writer -> {
            FriendlyByteBuf chunkCacheOutput = null;
            int lastChunkCacheIndex = -1;

            FriendlyByteBuf customPayloadTempBuffer = null;

            for (Packet<? super ClientGamePacketListener> packet : packetCopy) {
                if (packet instanceof ClientboundLevelChunkWithLightPacket levelChunkPacket) {
                    int index = -1;

                    CachedChunkPacket cachedChunkPacket = new CachedChunkPacket(levelChunkPacket, -1);
                    int hashCode = cachedChunkPacket.hashCode();

                    boolean add = true;

                    List<CachedChunkPacket> cached = this.cachedChunkPackets.get(hashCode);
                    if (cached == null) {
                        cached = new ArrayList<>();
                        this.cachedChunkPackets.put(hashCode, cached);
                    } else {
                        for (CachedChunkPacket existingChunkPacket : cached) {
                            if (existingChunkPacket.equals(cachedChunkPacket)) {
                                add = false;
                                index = existingChunkPacket.index;
                                break;
                            }
                        }
                    }

                    if (add) {
                        index = this.totalWrittenChunkPackets;
                        this.totalWrittenChunkPackets += 1;

                        // Write chunk cache file if necessary
                        int cacheIndex = index / ReplayServer.CHUNK_CACHE_SIZE;
                        if (lastChunkCacheIndex >= 0 && cacheIndex != lastChunkCacheIndex) {
                            this.writeChunkCacheFile(chunkCacheOutput, lastChunkCacheIndex);
                            chunkCacheOutput = null;
                        }
                        lastChunkCacheIndex = cacheIndex;

                        // Create new chunk cache output buffer if necessary
                        if (chunkCacheOutput == null) {
                            chunkCacheOutput = new FriendlyByteBuf(Unpooled.buffer());
                        }

                        // Write placeholder value for size
                        int startWriterIndex = chunkCacheOutput.writerIndex();
                        chunkCacheOutput.writeInt(-1);

                        // Write chunk packet
                        chunkCacheOutput
                                .writeVarInt(ConnectionProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND, packet));
                        packet.write(chunkCacheOutput);
                        int endWriterIndex = chunkCacheOutput.writerIndex();

                        // Write real size value
                        int size = endWriterIndex - startWriterIndex - 4;
                        chunkCacheOutput.writerIndex(startWriterIndex);
                        chunkCacheOutput.writeInt(size);
                        chunkCacheOutput.writerIndex(endWriterIndex);

                        // Add to list so that this chunk can be reused
                        cachedChunkPacket.index = index;
                        cached.add(cachedChunkPacket);
                    }

                    writer.startAction(ActionLevelChunkCached.INSTANCE);
                    writer.friendlyByteBuf().writeVarInt(index);
                    writer.finishAction(ActionLevelChunkCached.INSTANCE);

                    continue;
                }

                customPayloadTempBuffer = writeGamePacket(writer, packet, customPayloadTempBuffer);
            }

            if (lastChunkCacheIndex >= 0) {
                writeChunkCacheFile(chunkCacheOutput, lastChunkCacheIndex);
            }
        });
    }

    private static FriendlyByteBuf writeGamePacket(ReplayWriter writer, Packet<? super ClientGamePacketListener> packet,
            FriendlyByteBuf customPayloadTempBuffer) {
        if (packet instanceof ClientboundBundlePacket bp) {
            for (Packet<ClientGamePacketListener> child : bp.subPackets()) {
                customPayloadTempBuffer = writeGamePacket(writer, child, customPayloadTempBuffer);
            }
            return customPayloadTempBuffer;
        }

        int packetId = ConnectionProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND, packet);
        if (packetId == -1) {
            Flashback.LOGGER.error("Could not get packet id of packet {}!", packet.getClass());
            return customPayloadTempBuffer;
        }

        if (packet instanceof ClientboundCustomPayloadPacket cp) {
            if (FabricLoader.getInstance().isModLoaded("valkyrienskies") && ValkyrienSkiesSupport.tryHandleCustomPayload(cp, writer)) {
                return customPayloadTempBuffer;
            }
        }

        writer.startAction(ActionGamePacket.INSTANCE);
        var buf = writer.friendlyByteBuf();
        buf.writeVarInt(packetId);
        packet.write(buf);
        writer.finishAction(ActionGamePacket.INSTANCE);
        return customPayloadTempBuffer;
    }

    private void writeChunkCacheFile(FriendlyByteBuf chunkCacheOutput, int index) {
        if (chunkCacheOutput == null || chunkCacheOutput.writerIndex() == 0) {
            return;
        }

        try {
            byte[] bytes = new byte[chunkCacheOutput.writerIndex()];
            chunkCacheOutput.getBytes(0, bytes);

            Path levelChunkCachePath = this.recordFolder.resolve("level_chunk_caches").resolve("" + index);
            Files.createDirectories(levelChunkCachePath.getParent());
            Files.write(levelChunkCachePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND,
                    StandardOpenOption.SYNC);
        } catch (IOException e) {
            SneakyThrow.sneakyThrow(e);
        }
    }

    public void writeIcon(NativeImage nativeImage) {
        int width = nativeImage.getWidth();
        int height = nativeImage.getHeight();
        int x = 0;
        int y = 0;

        if (width > height) {
            x = (width - height) / 2;
            width = height;
        } else {
            y = (height - width) / 2;
            height = width;
        }

        try (NativeImage scaledImage = new NativeImage(64, 64, false);) {
            nativeImage.resizeSubRectTo(x, y, width, height, scaledImage);
            scaledImage.writeToFile(this.recordFolder.resolve("icon.png"));
        } catch (IOException e) {
            Flashback.LOGGER.warn("Couldn't save screenshot", e);
        } finally {
            nativeImage.close();
        }
    }

    public void writeReplayChunk(String chunkName, String metadata) {
        this.submit(writer -> {
            try {
                Path chunkFile = this.recordFolder.resolve(chunkName);
                Files.write(chunkFile, writer.popBytes());

                Path metaFile = this.recordFolder.resolve("metadata.json");
                if (Files.exists(metaFile)) {
                    Files.move(metaFile, this.recordFolder.resolve("metadata.json.old"), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }

                Files.writeString(metaFile, metadata);
            } catch (IOException e) {
                SneakyThrow.sneakyThrow(e);
            }
        });
    }

    private void waitForTasks() {
        checkForError();

        if (this.hasStopped.get()) {
            throw new IllegalStateException("Cannot wait for tasks on AsyncReplayWriter that has already stopped");
        }

        while (!this.tasks.isEmpty()) {
            checkForError();
            LockSupport.parkNanos("waiting for async replay writer to finish tasks", 100000L);
        }
    }

    public Path finish() {
        this.waitForTasks();

        this.shouldStop.set(true);

        while (!this.hasStopped.get()) {
            checkForError();
            LockSupport.parkNanos("waiting for async replay writer to stop", 100000L);
        }

        checkForError();

        return this.recordFolder;
    }

    private void checkForError() {
        Throwable t = error.get();
        if (t != null) {
            SneakyThrow.sneakyThrow(t);
        }
    }
}
