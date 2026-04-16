package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.moulberry.flashback.visuals.ShaderManager;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.OptionalInt;

public class SaveableFramebufferQueue implements AutoCloseable {

    private final int width;
    private final int height;

    private final ArrayDeque<SaveableFramebuffer> available = new ArrayDeque<>();
    private final ArrayDeque<SaveableFramebuffer> waiting = new ArrayDeque<>();

    private final GpuTexture flipBuffer;
    private final GpuTextureView flipBufferView;

    public SaveableFramebufferQueue(int width, int height) {
        this.width = width;
        this.height = height;

        this.flipBuffer = RenderSystem.getDevice().createTexture(() -> "flip buffer", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.RGBA8, width, height, 1, 1);
        this.flipBufferView = RenderSystem.getDevice().createTextureView(this.flipBuffer);
    }

    public SaveableFramebuffer take() {
        if (this.available.isEmpty()) {
            return new SaveableFramebuffer(this.width, this.height);
        } else {
            return this.available.removeFirst();
        }
    }

    private void blitFlip(RenderTarget src, boolean supersampling) {
        FilterMode filterMode = supersampling ? FilterMode.LINEAR : FilterMode.NEAREST;

        try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "flashback flip pass", this.flipBufferView, OptionalInt.empty())) {
            renderPass.setPipeline(ShaderManager.BLIT_SCREEN_FLIP);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("InSampler", src.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(filterMode));
            renderPass.draw(0, 3);
        }
    }

    public void startDownload(RenderTarget target, SaveableFramebuffer texture, boolean supersampling) {
        // Do an inline flip
        this.blitFlip(target, supersampling);

        texture.startDownload(this.flipBuffer);
        this.waiting.add(texture);
    }

    public record DownloadedFrame(NativeImage image, @Nullable FloatBuffer audioBuffer) {}

    public @Nullable DownloadedFrame finishDownload() {
        SaveableFramebuffer first = this.waiting.peekFirst();
        if (first == null) {
            return null;
        }

        NativeImage downloaded = first.finishDownload();

        if (downloaded == null) {
            return null;
        }

        FloatBuffer audioBuffer = first.audioBuffer;
        DownloadedFrame frame = new DownloadedFrame(downloaded, audioBuffer);

        SaveableFramebuffer popped = this.waiting.removeFirst();
        popped.audioBuffer = null;
        this.available.add(popped);

        return frame;
    }

    public boolean isEmpty() {
        return this.waiting.isEmpty();
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
        this.flipBuffer.close();
    }


}
