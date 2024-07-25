package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.ext.LevelChunkSectionExt;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
public class MixinLevelChunkSection implements LevelChunkSectionExt {

    @Shadow
    @Final
    private PalettedContainer<BlockState> states;

    @Shadow
    private short nonEmptyBlockCount;

    @Shadow
    private PalettedContainerRO<Holder<Biome>> biomes;

    @Unique
    private int cachedChunkId = -1;

    @Override
    public int flashback$getCachedChunkId() {
        return this.cachedChunkId;
    }

    @Override
    public void flashback$setCachedChunkId(int cachedChunkId) {
        this.cachedChunkId = cachedChunkId;
    }

    @Inject(method = "read", at = @At("RETURN"))
    public void read(FriendlyByteBuf friendlyByteBuf, CallbackInfo ci) {
        this.cachedChunkId = -1;
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    public void setBlockState(int i, int j, int k, BlockState blockState, boolean bl, CallbackInfoReturnable<BlockState> cir) {
        this.cachedChunkId = -1;
    }

}
