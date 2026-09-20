package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public class MixinGlCommandEncoder {

    // _glReadBuffer(0) is invalid
    @WrapWithCondition(method = "copyTextureToBuffer(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_glReadBuffer(I)V"), require = 0)
    public boolean copyTextureToBuffer_glReadBuffer(int mode) {
        return mode != 0;
    }

}
