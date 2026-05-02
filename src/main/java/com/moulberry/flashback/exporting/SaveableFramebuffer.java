package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class SaveableFramebuffer implements AutoCloseable {
    public @Nullable FloatBuffer audioBuffer;
    private NativeImage downloaded = null;
    private final int width;
    private final int height;
    private GpuBuffer pbo;

    private boolean isDownloading = false;

    public SaveableFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.pbo = RenderSystem.getDevice().createBuffer(() -> "Flashback texture output buffer",
            GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 4L*width*height);
    }

    public void startDownload(GpuTexture gpuTexture) {
        if (this.isDownloading) {
            throw new IllegalStateException("Can't start downloading while already downloading");
        }
        this.isDownloading = true;

        if (this.pbo == null) {
            throw new IllegalStateException();
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        Runnable runnable = () -> {
            try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(this.pbo, true, false)) {
                NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
                MemoryUtil.memCopy(MemoryUtil.memAddress(mappedView.data()), nativeImage.pixels, nativeImage.size);
                this.downloaded = nativeImage;
            }
        };

        commandEncoder.copyTextureToBuffer(gpuTexture, this.pbo, 0, runnable, 0);
    }

    public boolean canFinishDownload() {
        if (!this.isDownloading) {
            throw new IllegalStateException("Can't finish downloading before download has started");
        }
        return this.downloaded != null;
    }

    public NativeImage finishDownload() {
        if (!this.isDownloading) {
            throw new IllegalStateException("Can't finish downloading before download has started");
        }
        if (this.downloaded == null) {
            return null;
        }

        this.isDownloading = false;
        NativeImage downloaded = this.downloaded;
        this.downloaded = null;
        return downloaded;
    }

    public void close() {
        if (this.pbo != null) {
            this.pbo.close();
            this.pbo = null;
        }
        if (this.downloaded != null) {
            this.downloaded.close();
            this.downloaded = null;
        }
    }

}
