package com.moulberry.flashback.mixin;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = DebugScreenOverlay.class, priority = 1100)
public class MixinDebugScreenOverlay {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, CallbackInfo ci) {
        if (Flashback.isExporting()) {
            ci.cancel();
        }
    }
}
