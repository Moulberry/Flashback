package com.moulberry.flashback.mixin.norandom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.ExportJob;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public class MixinClientLevelNoRandom {

    @WrapOperation(method = "doAnimateTick", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    public void animateBlock(Block instance, BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource, Operation<Void> original) {
        ExportJob exportJob = Flashback.EXPORT_JOB;
        if (exportJob != null && exportJob.getSettings().resetRng()) {
            randomSource = RandomSource.create(Mth.getSeed(blockPos) ^ exportJob.getSeedForCurrentClientTick());
        }
        original.call(instance, blockState, level, blockPos, randomSource);
    }

    @WrapOperation(method = "doAnimateTick", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;animateTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    public void animateFluid(FluidState instance, Level level, BlockPos blockPos, RandomSource randomSource, Operation<Void> original) {
        ExportJob exportJob = Flashback.EXPORT_JOB;
        if (exportJob != null && exportJob.getSettings().resetRng()) {
            randomSource = RandomSource.create(Mth.getSeed(blockPos) ^ exportJob.getSeedForCurrentClientTick());
        }
        original.call(instance, level, blockPos, randomSource);
    }

}
