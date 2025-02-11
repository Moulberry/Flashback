package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.ext.ThreadedLevelLightEngineExt;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ThreadedLevelLightEngine.class, priority = 1010) // needs a higher priority so that it doesn't clash with Moonrise's @Overwrite
public abstract class MixinThreadedLevelLightEngine extends LevelLightEngine implements ThreadedLevelLightEngineExt {

    @Shadow
    protected abstract void addTask(int i, int j, ThreadedLevelLightEngine.TaskType taskType, Runnable runnable);

    public MixinThreadedLevelLightEngine(LightChunkGetter lightChunkGetter, boolean bl, boolean bl2) {
        super(lightChunkGetter, bl, bl2);
    }

    @Override
    public void flashback$submitPost(int x, int z, Runnable runnable) {
        this.addTask(x, z, ThreadedLevelLightEngine.TaskType.POST_UPDATE, runnable);
    }

    @WrapOperation(method = "runUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"), require = 0)
    public int runUpdate_runLightUpdates(ThreadedLevelLightEngine instance, Operation<Integer> original) {
        if (Flashback.isInReplay()) {
            try {
                return original.call(instance);
            } catch (Exception e) {
                Flashback.LOGGER.error("Exception occurred while processing light updates", e);
                return 0;
            }
        } else {
            return original.call(instance);
        }
    }

}
