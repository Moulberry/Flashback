package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.moulberry.flashback.ReplayVisuals;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderTarget.class, priority = 1100)
public class MixinRenderTarget {

    @WrapOperation(method = "_blitToScreen", at=@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_viewport(IIII)V", remap = false))
    public void blitToScreenViewport(int left, int bottom, int width, int height, Operation<Void> original) {
        if ((Object)this == Minecraft.getInstance().getMainRenderTarget() && ReplayUI.isActive() && ReplayVisuals.sizing != Sizing.UNDERLAY) {
            ReplayUI.setupMainViewport();
        } else {
            original.call(left, bottom, width, height);
        }
    }


}
