package com.moulberry.flashback.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.moulberry.flashback.FramebufferUtils;
import com.moulberry.flashback.WindowSizeTracker;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderTarget.class, priority = 800)
public abstract class MixinRenderTarget {

    //#if MC>=12105
    @Inject(method = "blitToScreen()V", at = @At("HEAD"), cancellable = true)
    public void blitToScreen(CallbackInfo ci) {
        if ((Object)this == Minecraft.getInstance().getMainRenderTarget() && ReplayUI.isActive() && ReplayUI.frameWidth > 1 && ReplayUI.frameHeight > 1) {
            var window = Minecraft.getInstance().getWindow();
            float frameTop = (float) ReplayUI.frameY / ReplayUI.viewportSizeY;
            float frameLeft = (float) ReplayUI.frameX / ReplayUI.viewportSizeX;
            float frameWidth = (float) Math.max(1, ReplayUI.frameWidth) / ReplayUI.viewportSizeX;
            float frameHeight = (float) Math.max(1, ReplayUI.frameHeight) / ReplayUI.viewportSizeY;

            int framebufferWidth = WindowSizeTracker.getWidth(window);
            int framebufferHeight = WindowSizeTracker.getHeight(window);

            FramebufferUtils.blitToScreenPartial((RenderTarget) (Object) this, framebufferWidth, framebufferHeight,
                frameLeft, frameTop,
                frameLeft+frameWidth, frameTop+frameHeight);
            ci.cancel();
        }
    }

}
