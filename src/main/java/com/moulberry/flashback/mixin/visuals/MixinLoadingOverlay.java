package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public class MixinLoadingOverlay {

    @Shadow private long fadeOutStart;

    @Shadow @Final private Minecraft minecraft;

    // Make overlay disappear instantly if inside replay
    @Inject(method = "render", at = @At("RETURN"), require = 0)
    public void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        if (this.fadeOutStart != -1 && Flashback.isInReplay()) {
            this.minecraft.setOverlay(null);
        }
    }


}
