package com.moulberry.flashback.mixin.client.playback;

import com.moulberry.flashback.ChunkSectionHash;
import com.moulberry.flashback.ext.LevelChunkSectionExt;
import com.moulberry.flashback.ext.PaletteExt;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelChunkSection.class)
public class MixinLevelChunkSection implements LevelChunkSectionExt {

    @Shadow
    @Final
    private PalettedContainer<BlockState> states;

    @Shadow
    private short nonEmptyBlockCount;

    @Shadow
    private PalettedContainerRO<Holder<Biome>> biomes;

    @Override
    public ChunkSectionHash flashback$hashState() {
        long blockHash = hashPalettedContainerData(this.states.data);
        long biomeHash = hashPalettedContainerData(((PalettedContainer<?>) this.biomes).data);
        return new ChunkSectionHash(this.nonEmptyBlockCount, blockHash, biomeHash);
    }

    @Unique
    private static long hashPalettedContainerData(PalettedContainer.Data<?> data) {
        long hash;
        if (data.palette() instanceof PaletteExt paletteExt) {
            hash = paletteExt.flashback$hash();
        } else {
            throw new RuntimeException("Unknown palette type: " + data.palette().getClass());
        }
        for (long l : data.storage().getRaw()) {
            hash = l + hash*17;
        }
        return hash;
    }

    @Override
    public boolean flashback$doesHashedStateMatch(ChunkSectionHash hashedState) {
        if (hashedState.nonEmptyBlockCount() != this.nonEmptyBlockCount) {
            return false;
        }

        long biomeHash = hashPalettedContainerData(((PalettedContainer<?>) this.biomes).data);
        if (hashedState.biomeHash() != biomeHash) {
            return false;
        }

        long blockHash = hashPalettedContainerData(this.states.data);
        return hashedState.blockHash() == blockHash;
    }

}
