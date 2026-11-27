package com.moulberry.flashback.compat;

import ca.spottedleaf.moonrise.patches.starlight.chunk.StarlightChunk;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import ca.spottedleaf.moonrise.patches.starlight.light.StarLightEngine;
import java.util.BitSet;
import java.util.Iterator;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;

public final class MoonriseLightCompat {
    private MoonriseLightCompat() {}

    public static void apply(LevelChunk chunk, ClientboundLightUpdatePacketData data, boolean fullChunk) {
        StarlightChunk lightChunk = (StarlightChunk) chunk;
        chunk.setLightCorrect(false);

        SWMRNibbleArray[] block = fullChunk ? StarLightEngine.getFilledEmptyLight(chunk)
            : lightChunk.starlight$getBlockNibbles().clone();
        SWMRNibbleArray[] sky = fullChunk ? StarLightEngine.getFilledEmptyLight(chunk)
            : lightChunk.starlight$getSkyNibbles().clone();
        read(block, data.blockYMask(), data.emptyBlockYMask(), data.blockUpdates().iterator());
        read(sky, data.skyYMask(), data.emptySkyYMask(), data.skyUpdates().iterator());

        // Sky-light readers require a non-null emptiness map. Packet replacement
        // can change these sections without running normal generation hooks.
        boolean[] empty = new boolean[chunk.getSectionsCount()];
        for (int i = 0; i < empty.length; ++i) empty[i] = chunk.getSection(i).hasOnlyAir();
        lightChunk.starlight$setBlockNibbles(block);
        lightChunk.starlight$setSkyNibbles(sky);
        lightChunk.starlight$setBlockEmptinessMap(empty.clone());
        lightChunk.starlight$setSkyEmptinessMap(empty);
        chunk.setLightCorrect(true);
    }

    private static void read(SWMRNibbleArray[] target, BitSet present, BitSet empty, Iterator<byte[]> updates) {
        for (int i = 0; i < target.length; ++i) {
            if (present.get(i)) {
                target[i] = SWMRNibbleArray.fromVanilla(new DataLayer(updates.next().clone()));
            } else if (empty.get(i)) {
                target[i] = SWMRNibbleArray.fromVanilla(new DataLayer());
            }
            // An omitted section is unchanged for incremental updates.
        }
    }
}
