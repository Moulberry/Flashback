package com.moulberry.flashback.mixin;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = DebugScreenOverlay.class, priority = 1100)
public class MixinDebugScreenOverlay {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void render(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (Flashback.isExporting()) {
            ci.cancel();
        }
    }

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    public void getGameInformation(CallbackInfoReturnable<List<String>> info) {
        if (Flashback.RECORDER != null) {
            info.getReturnValue().add(Flashback.RECORDER.getDebugString());
        }
    }

}
