package com.moulberry.flashback;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledUnsafeHeapByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

public class CachedChunkPacket {

    private final byte[] bigHash;
    private final int hashCode;
    public int index;

    public CachedChunkPacket(ClientboundLevelChunkWithLightPacket packet, int index) {
        this.index = index;
        this.bigHash = computePacketBigHash(packet);
        this.hashCode = Arrays.hashCode(this.bigHash);
    }

    private static byte[] computePacketBigHash(ClientboundLevelChunkWithLightPacket packet) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        digest.update(intToByteArray(packet.getX()));
        digest.update(intToByteArray(packet.getZ()));
        digest.update(packet.getChunkData().buffer);

        FriendlyByteBuf frenBuffer = new FriendlyByteBuf(Unpooled.buffer());
        packet.lightData.write(frenBuffer);
        digest.update(frenBuffer.array(), 0, frenBuffer.writerIndex());
        frenBuffer.resetWriterIndex();

        frenBuffer.writeNbt(packet.getChunkData().getHeightmaps());
        digest.update(frenBuffer.array(), 0, frenBuffer.writerIndex());
        frenBuffer.resetWriterIndex();

        //Ensure stable ordering
        var copy = new ArrayList<>(packet.getChunkData().blockEntitiesData);
        copy.sort(Comparator.comparing(a->a.y^a.packedXZ));
        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntitiesData : copy) {
            digest.update(intToByteArray(blockEntitiesData.packedXZ));
            digest.update(intToByteArray(blockEntitiesData.y));
            digest.update(BlockEntityType.getKey(blockEntitiesData.type).toString().getBytes(StandardCharsets.UTF_8));
            if (blockEntitiesData.tag != null) {
                frenBuffer.writeNbt(blockEntitiesData.tag);
                digest.update(frenBuffer.array(), 0, frenBuffer.writerIndex());
                frenBuffer.resetWriterIndex();
            } else {
                digest.update("NO_TAG".getBytes(StandardCharsets.UTF_8));
            }
        }

        return digest.digest();
    }

    private static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CachedChunkPacket that)) return false;
        if (this.hashCode() != that.hashCode()) {
            return false;
        }

        return Arrays.compare(this.bigHash, that.bigHash) == 0;
    }
}
