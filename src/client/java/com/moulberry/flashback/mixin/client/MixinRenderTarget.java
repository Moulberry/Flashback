package com.moulberry.flashback.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.moulberry.flashback.ui.ReplayUI;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderTarget.class)
public class MixinRenderTarget {

    @Redirect(method = "_blitToScreen", at=@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_viewport(IIII)V", remap = false))
    public void blitToScreenViewport(int left, int bottom, int width, int height) {
        if ((Object)this == Minecraft.getInstance().getMainRenderTarget() && ReplayUI.isActive()) {
            ReplayUI.setupMainViewport();
        } else {
            GlStateManager._viewport(left, bottom, width, height);
        }
    }


}
