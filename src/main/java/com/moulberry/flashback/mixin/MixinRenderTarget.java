package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderTarget.class, priority = 800)
public abstract class MixinRenderTarget {

    @Shadow
    public int frameBufferId;

    @Inject(method = "blitToScreen(IIZ)V", at = @At("HEAD"), cancellable = true)
    public void blitToScreenSodium(int width, int height, boolean noBlend, CallbackInfo ci) {
        if ((Object)this == Minecraft.getInstance().getMainRenderTarget() && ReplayUI.isActive()) {
            var window = Minecraft.getInstance().getWindow();
            int frameBottom = (ReplayUI.viewportSizeY - (ReplayUI.frameY + ReplayUI.frameHeight)) * window.framebufferHeight / ReplayUI.viewportSizeY;
            int frameLeft = ReplayUI.frameX * window.framebufferWidth / ReplayUI.viewportSizeX;
            int frameWidth = Math.max(1, ReplayUI.frameWidth) * window.framebufferWidth / ReplayUI.viewportSizeX;
            int frameHeight = Math.max(1, ReplayUI.frameHeight) * window.framebufferHeight / ReplayUI.viewportSizeY;

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, width, height, frameLeft, frameBottom, frameLeft+frameWidth, frameBottom+frameHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            ci.cancel();
        }
    }

}
