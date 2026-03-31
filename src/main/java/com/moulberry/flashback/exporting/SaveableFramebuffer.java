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

//        if (this.pboId == -1) {
//            this.pboId = GL30C.glGenBuffers();
//
//            GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, this.pboId);
//            GL30C.glBufferData(GL30C.GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL30C.GL_STREAM_READ);
//            GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);
//        }
//
//        int fbo = ((GlTexture)gpuTexture).getFbo(((GlDevice)RenderSystem.getDevice()).directStateAccess(), null);
//        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
//
//        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, this.pboId);
//        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
//        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
//        GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
//        GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
//        GL30C.glReadPixels(0, 0, width, height, GL30C.GL_RGBA, GL30C.GL_UNSIGNED_BYTE, 0);
//        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);
//
//        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
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
