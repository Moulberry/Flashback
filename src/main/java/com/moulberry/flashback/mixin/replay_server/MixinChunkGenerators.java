package com.moulberry.flashback.mixin.replay_server;

import com.mojang.serialization.MapCodec;
import com.moulberry.flashback.playback.EmptyLevelSource;
import net.minecraft.core.Registry;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGenerators;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerators.class)
public class MixinChunkGenerators {

    @Inject(method = "bootstrap", at = @At("HEAD"))
    private static void bootstrap(Registry<MapCodec<? extends ChunkGenerator>> registry, CallbackInfoReturnable<MapCodec<? extends ChunkGenerator>> cir) {
        Registry.register(registry, "flashback/empty", EmptyLevelSource.CODEC);
    }

}
