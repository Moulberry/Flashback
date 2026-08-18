package com.moulberry.flashback.mixin.millis;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public class MixinSectionRenderDispatcherRenderSection {

    @WrapMethod(method = "getVisibility")
    public float getVisibility(long now, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            return 1.0f;
        }
        return original.call(now);
    }

}
