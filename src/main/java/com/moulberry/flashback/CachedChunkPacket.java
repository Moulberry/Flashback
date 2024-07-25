package com.moulberry.flashback;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Arrays;
import java.util.Objects;

public class CachedChunkPacket {

    private final ClientboundLevelChunkWithLightPacket packet;
    private boolean calculatedHashCode = false;
    private int hashCode = 0;
    public int index;

    public CachedChunkPacket(ClientboundLevelChunkWithLightPacket packet, int index) {
        this.packet = packet;
        this.index = index;
    }

    @Override
    public int hashCode() {
        if (!this.calculatedHashCode) {
            this.hashCode = this.calculateHashCode();
            this.calculatedHashCode = true;
        }
        return this.hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CachedChunkPacket that)) return false;
        if (this.hashCode() != that.hashCode()) {
            return false;
        }

        ClientboundLevelChunkPacketData thisChunkData = this.packet.getChunkData();
        ClientboundLightUpdatePacketData thisLightData = this.packet.getLightData();
        ClientboundLevelChunkPacketData thatChunkData = that.packet.getChunkData();
        ClientboundLightUpdatePacketData thatLightData = that.packet.getLightData();

        if (this.packet.getX() != that.packet.getX()) {
            return false;
        }
        if (this.packet.getZ() != that.packet.getZ()) {
            return false;
        }

        if (!thisChunkData.getHeightmaps().equals(thatChunkData.getHeightmaps())) {
            return false;
        }
        if (!thisLightData.getSkyYMask().equals(thatLightData.getSkyYMask())) {
            return false;
        }
        if (!thisLightData.getBlockYMask().equals(thatLightData.getBlockYMask())) {
            return false;
        }
        if (!thisLightData.getEmptySkyYMask().equals(thatLightData.getEmptySkyYMask())) {
            return false;
        }
        if (!thisLightData.getEmptyBlockYMask().equals(thatLightData.getEmptyBlockYMask())) {
            return false;
        }

        if (thisLightData.getSkyUpdates().size() != thatLightData.getSkyUpdates().size()) {
            return false;
        }
        if (thisLightData.getBlockUpdates().size() != thatLightData.getBlockUpdates().size()) {
            return false;
        }

        int skySize = thisLightData.getSkyUpdates().size();
        for (int i = 0; i < skySize; i++) {
            byte[] thisSkyLight = thisLightData.getSkyUpdates().get(i);
            byte[] thatSkyLight = thatLightData.getSkyUpdates().get(i);
            if (!Arrays.equals(thisSkyLight, thatSkyLight)) {
                return false;
            }
        }

        int blockSize = thisLightData.getBlockUpdates().size();
        for (int i = 0; i < blockSize; i++) {
            byte[] thisBlockLight = thisLightData.getBlockUpdates().get(i);
            byte[] thatBlockLight = thatLightData.getBlockUpdates().get(i);
            if (!Arrays.equals(thisBlockLight, thatBlockLight)) {
                return false;
            }
        }

        Long2ObjectOpenHashMap<CompoundTag> thisBlockEntityMap = new Long2ObjectOpenHashMap<>();

        if (thisChunkData.blockEntitiesData.size() != thatChunkData.blockEntitiesData.size()) {
            return false;
        }

        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntitiesDatum : thisChunkData.blockEntitiesData) {
            long key = ((long)blockEntitiesDatum.y) << 32 | blockEntitiesDatum.packedXZ;
            thisBlockEntityMap.put(key, blockEntitiesDatum.tag);
        }
        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntitiesDatum : thatChunkData.blockEntitiesData) {
            long key = ((long)blockEntitiesDatum.y) << 32 | blockEntitiesDatum.packedXZ;

            if (!thisBlockEntityMap.containsKey(key)) {
                return false;
            }

            CompoundTag thisBlockEntity = thisBlockEntityMap.get(key);
            if (!Objects.equals(thisBlockEntity, blockEntitiesDatum.tag)) {
                return false;
            }
        }

        return Arrays.equals(thisChunkData.buffer, thatChunkData.buffer);
    }

    private int calculateHashCode() {
        ClientboundLevelChunkPacketData chunkData = this.packet.getChunkData();
        ClientboundLightUpdatePacketData lightData = this.packet.getLightData();

        int hashCode = 1;
        hashCode = hashCode*31 + this.packet.getX();
        hashCode = hashCode*31 + this.packet.getZ();

        hashCode = hashCode*31 + chunkData.getHeightmaps().hashCode();

        hashCode = hashCode*31 + lightData.getSkyYMask().hashCode();
        hashCode = hashCode*31 + lightData.getBlockYMask().hashCode();
        hashCode = hashCode*31 + lightData.getEmptySkyYMask().hashCode();
        hashCode = hashCode*31 + lightData.getEmptyBlockYMask().hashCode();

        for (byte[] skyUpdate : lightData.getSkyUpdates()) {
            hashCode = hashCode*31 + fixedSizeHashForLightArray(skyUpdate);
        }
        for (byte[] blockUpdate : lightData.getBlockUpdates()) {
            hashCode = hashCode*31 + fixedSizeHashForLightArray(blockUpdate);
        }

        for (ClientboundLevelChunkPacketData.BlockEntityInfo blockEntitiesDatum : chunkData.blockEntitiesData) {
            hashCode = hashCode*31 + blockEntitiesDatum.packedXZ;
            hashCode = hashCode*31 + blockEntitiesDatum.y;
            hashCode = hashCode*31 + BlockEntityType.getKey(blockEntitiesDatum.type).hashCode();
            hashCode = hashCode*31 + (blockEntitiesDatum.tag == null ? 0 : blockEntitiesDatum.tag.hashCode());
        }

        hashCode = hashCode*31 + chunkData.getReadBuffer().hashCode();

        return hashCode;
    }

    private static final int[] fixedSizeHashCoefficients = new int[2048];
    static {
        int coefficient = 1;
        for (int i = fixedSizeHashCoefficients.length - 1; i >= 0; i--) {
            fixedSizeHashCoefficients[i] = coefficient;
            coefficient *= 31;
        }
    }

    private static int fixedSizeHashForLightArray(byte[] array) {
        if (array.length != 2048) {
            throw new IllegalArgumentException("Array must be of length 2048");
        }

        int hash = 0;
        for (int i = 0; i < array.length; i += 4) {
            hash += fixedSizeHashCoefficients[i] * array[i] +
                    fixedSizeHashCoefficients[i+1] * array[i+1] +
                    fixedSizeHashCoefficients[i+2] * array[i+2] +
                    fixedSizeHashCoefficients[i+3] * array[i+3];
        }
        return hash;
    }

}
