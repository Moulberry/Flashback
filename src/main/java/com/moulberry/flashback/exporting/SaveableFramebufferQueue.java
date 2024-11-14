package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.moulberry.flashback.visuals.ShaderManager;
import net.minecraft.client.renderer.CompiledShaderProgram;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SaveableFramebufferQueue implements AutoCloseable {

    private final int width;
    private final int height;

    private static final int CAPACITY = 3;

    private final List<SaveableFramebuffer> available = new ArrayList<>();
    private final List<SaveableFramebuffer> waiting = new ArrayList<>();

    private final RenderTarget flipBuffer;

    public SaveableFramebufferQueue(int width, int height) {
        this.width = width;
        this.height = height;
        this.flipBuffer = new TextureTarget(width, height, false);

        for (int i = 0; i < CAPACITY; i++) {
            this.available.add(new SaveableFramebuffer());
        }
    }

    public SaveableFramebuffer take() {
        if (this.available.isEmpty()) {
            throw new IllegalStateException("No textures available!");
        }
        return this.available.removeFirst();
    }

    private void blitFlip(RenderTarget src, boolean supersampling) {
        int oldFilterMode = src.filterMode;
        if (supersampling) {
            src.setFilterMode(GL11.GL_LINEAR);
        }

        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._viewport(0, 0, src.width, src.height);
        GlStateManager._disableBlend();
        RenderSystem.disableCull();

        this.flipBuffer.bindWrite(true);
        CompiledShaderProgram shaderInstance = Objects.requireNonNull(
                RenderSystem.setShader(ShaderManager.blitScreenFlip), "Blit shader not loaded"
        );
        shaderInstance.bindSampler("InSampler", src.colorTextureId);
        shaderInstance.apply();
        BufferBuilder bufferBuilder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
        bufferBuilder.addVertex(0.0F, 1.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 1.0F, 0.0F);
        bufferBuilder.addVertex(1.0F, 0.0F, 0.0F);
        bufferBuilder.addVertex(0.0F, 0.0F, 0.0F);
        BufferUploader.draw(bufferBuilder.buildOrThrow());
        shaderInstance.clear();

        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        RenderSystem.enableCull();

        if (supersampling) {
            src.setFilterMode(oldFilterMode);
        }
    }

    public void startDownload(RenderTarget target, SaveableFramebuffer texture, boolean supersampling) {
        // Do an inline flip
        this.blitFlip(target, supersampling);

        texture.startDownload(this.flipBuffer, this.width, this.height);
        this.waiting.add(texture);
    }

    record DownloadedFrame(NativeImage image, @Nullable FloatBuffer audioBuffer) {}

    public @Nullable DownloadedFrame finishDownload(boolean drain) {
        if (this.waiting.isEmpty()) {
            return null;
        }

        if (!drain && !this.available.isEmpty()) {
            return null;
        }

        SaveableFramebuffer texture = this.waiting.removeFirst();

        NativeImage nativeImage = texture.finishDownload(this.width, this.height);
        FloatBuffer audioBuffer = texture.audioBuffer;
        texture.audioBuffer = null;

        this.available.add(texture);
        return new DownloadedFrame(nativeImage, audioBuffer);
    }

    @Override
    public void close() {
        for (SaveableFramebuffer texture : this.waiting) {
            texture.close();
        }
        for (SaveableFramebuffer texture : this.available) {
            texture.close();
        }
        this.waiting.clear();
        this.available.clear();
        this.flipBuffer.destroyBuffers();
    }


}
