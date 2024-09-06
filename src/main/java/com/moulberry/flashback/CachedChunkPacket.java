package com.moulberry.flashback;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class CachedChunkPacket {
    private final int x;
    private final int z;
    private final byte[] bigHash;
    private final int hashCode;
    public int index;

    public CachedChunkPacket(ClientboundLevelChunkWithLightPacket packet, int index) {
        this.x = packet.getX();
        this.z = packet.getZ();
        this.bigHash = computePacketBigHash(packet);
        this.hashCode = Arrays.hashCode(this.bigHash);
        this.index = index;
    }

    private static byte[] computePacketBigHash(ClientboundLevelChunkWithLightPacket packet) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e2) {
                throw new RuntimeException(e2);
            }
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

        // Sort to ensure stable ordering
        var copy = new ArrayList<>(packet.getChunkData().blockEntitiesData);
        copy.sort(Comparator.comparingInt(a -> (a.y << 8) | a.packedXZ));

        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntitiesData : copy) {
            digest.update((byte) blockEntitiesData.packedXZ);
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
        if (this.x != that.x || this.z != that.z) {
            return false;
        }

        return Arrays.compare(this.bigHash, that.bigHash) == 0;
    }
}
