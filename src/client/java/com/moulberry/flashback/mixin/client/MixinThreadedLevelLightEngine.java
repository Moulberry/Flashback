package com.moulberry.flashback.mixin.client;

import com.moulberry.flashback.ext.ThreadedLevelLightEngineExt;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ThreadedLevelLightEngine.class)
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
}
