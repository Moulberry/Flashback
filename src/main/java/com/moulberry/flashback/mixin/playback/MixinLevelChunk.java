package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.LevelChunkExt;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LightEngine;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk extends ChunkAccess implements LevelChunkExt {

    @Shadow
    @Final
    private Level level;

    @Shadow
    public abstract void addAndRegisterBlockEntity(BlockEntity blockEntity);

    @Shadow
    protected abstract <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity);

    @Shadow
    public abstract @Nullable BlockEntity getBlockEntity(BlockPos blockPos, LevelChunk.EntityCreationType entityCreationType);

    @Shadow public abstract void markUnsaved();

    public MixinLevelChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> registry, long l, @Nullable LevelChunkSection[] levelChunkSections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, l, levelChunkSections, blendingData);
    }

    @Unique
    private int cachedChunkId = -1;

    @Override
    public int flashback$getCachedChunkId() {
        return this.cachedChunkId;
    }

    @Override
    public void flashback$setCachedChunkId(int id) {
        this.cachedChunkId = id;
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    public void setBlockState(BlockPos blockPos, BlockState blockState, boolean bl, CallbackInfoReturnable<BlockState> cir) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }

        BlockState old = cir.getReturnValue();
        if (old != null && old != blockState) {
            replayServer.blockChangeOccurred(blockPos, blockState);
            this.cachedChunkId = -1;
        }
    }

    @Override
    public BlockState flashback$setBlockStateWithoutUpdates(BlockPos blockPos, BlockState blockState) {
        int y = blockPos.getY();
        int sectionY = this.getSectionIndex(y);
        if (sectionY < 0 || sectionY >= this.getSectionsCount()) {
            return null;
        }

        LevelChunkSection levelChunkSection = this.getSection(sectionY);
        boolean oldHasOnlyAir = levelChunkSection.hasOnlyAir();
        if (oldHasOnlyAir && blockState.isAir()) {
            return null;
        }

        // Set block
        int localX = blockPos.getX() & 0xF;
        int localY = y & 0xF;
        int localZ = blockPos.getZ() & 0xF;
        BlockState oldBlockState = levelChunkSection.setBlockState(localX, localY, localZ, blockState);
        if (oldBlockState == blockState) {
            return null;
        }

        this.cachedChunkId = -1;

        // Update heightmaps
        this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING).update(localX, y, localZ, blockState);
        this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(localX, y, localZ, blockState);
        this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR).update(localX, y, localZ, blockState);
        this.heightmaps.get(Heightmap.Types.WORLD_SURFACE).update(localX, y, localZ, blockState);

        // Update air status
        boolean newHasOnlyAir = levelChunkSection.hasOnlyAir();
        if (oldHasOnlyAir != newHasOnlyAir) {
            this.level.getChunkSource().getLightEngine().updateSectionStatus(blockPos, newHasOnlyAir);
            this.level.getChunkSource().onSectionEmptinessChanged(this.chunkPos.x, SectionPos.blockToSectionCoord(y), this.chunkPos.z, newHasOnlyAir);
        }

        // Update light
        if (LightEngine.hasDifferentLightProperties(oldBlockState, blockState)) {
            if (this.skyLightSources != null) {
                this.skyLightSources.update(this, localX, y, localZ);
            }
            this.level.getChunkSource().getLightEngine().checkBlock(blockPos);
        }

        // Update block entity
        Block block = blockState.getBlock();
        if (!oldBlockState.is(block) && oldBlockState.hasBlockEntity()) {
            this.removeBlockEntity(blockPos);
        }
        if (!levelChunkSection.getBlockState(localX, localY, localZ).is(block)) {
            return oldBlockState;
        }
        if (blockState.hasBlockEntity()) {
            BlockEntity blockEntity = this.getBlockEntity(blockPos, LevelChunk.EntityCreationType.CHECK);
            if (blockEntity == null) {
                blockEntity = ((EntityBlock)block).newBlockEntity(blockPos, blockState);
                if (blockEntity != null) {
                    this.addAndRegisterBlockEntity(blockEntity);
                }
            } else {
                blockEntity.setBlockState(blockState);
                this.updateBlockEntityTicker(blockEntity);
            }
        }

        this.markUnsaved();
        return oldBlockState;
    }

}
