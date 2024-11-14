package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.flashback.WindowSizeTracker;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.CoreShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(value = RenderTarget.class, priority = 800)
public abstract class MixinRenderTarget {

    @Shadow
    public int colorTextureId;

    @Inject(method = "blitToScreen(II)V", at = @At("HEAD"), cancellable = true)
    public void blitToScreen(int width, int height, CallbackInfo ci) {
        if ((Object)this == Minecraft.getInstance().getMainRenderTarget() && ReplayUI.isActive()) {
            var window = Minecraft.getInstance().getWindow();
            float frameLeft = (float) ReplayUI.frameX / ReplayUI.viewportSizeX;
            float frameTop = (float) ReplayUI.frameY / ReplayUI.viewportSizeY;
            float frameWidth = (float) Math.max(1, ReplayUI.frameWidth) / ReplayUI.viewportSizeX;
            float frameHeight = (float) Math.max(1, ReplayUI.frameHeight) / ReplayUI.viewportSizeY;

            int realWidth = WindowSizeTracker.getWidth(window);
            int realHeight = WindowSizeTracker.getHeight(window);

            RenderSystem.assertOnRenderThread();
            GlStateManager._colorMask(true, true, true, false);
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._viewport((int)(realWidth * frameLeft), (int)(realHeight * (1 - (frameTop+frameHeight))),
                Math.max(1, (int)(realWidth * frameWidth)), Math.max(1, (int)(realHeight * frameHeight)));
            GlStateManager._disableBlend();
            CompiledShaderProgram shaderInstance = Objects.requireNonNull(
                RenderSystem.setShader(CoreShaders.BLIT_SCREEN), "Blit shader not loaded"
            );
            shaderInstance.bindSampler("InSampler", this.colorTextureId);
            shaderInstance.apply();
            BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
            bufferBuilder.addVertex(0.0f, 0.0f, 0.0f);
            bufferBuilder.addVertex(1.0f, 0.0f, 0.0f);
            bufferBuilder.addVertex(1.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(0.0f, 1.0f, 0.0f);
            BufferUploader.draw(bufferBuilder.buildOrThrow());
            shaderInstance.clear();
            GlStateManager._depthMask(true);
            GlStateManager._colorMask(true, true, true, true);
            ci.cancel();
        }
    }

}
