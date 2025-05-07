package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class SaveableFramebuffer implements AutoCloseable {
    private int pboId;
    public @Nullable FloatBuffer audioBuffer;

    private boolean isDownloading = false;

    public SaveableFramebuffer() {
        this.pboId = -1;
    }

    public void startDownload(RenderTarget framebuffer, int width, int height) {
        if (this.isDownloading) {
            throw new IllegalStateException("Can't start downloading while already downloading");
        }
        this.isDownloading = true;

        if (this.pboId == -1) {
            this.pboId = GL30C.glGenBuffers();

            GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, this.pboId);
            GL30C.glBufferData(GL30C.GL_PIXEL_PACK_BUFFER, (long) width * height * 4, GL30C.GL_STREAM_READ);
            GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);
        }

        framebuffer.bindWrite(true);

        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, this.pboId);
        GlStateManager._pixelStore(GL11.GL_PACK_ALIGNMENT, 1);
        GlStateManager._pixelStore(GL11.GL_PACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_PACK_SKIP_ROWS, 0);
        GL30C.glReadPixels(0, 0, width, height, GL30C.GL_RGBA, GL30C.GL_UNSIGNED_BYTE, 0);
        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);

        framebuffer.unbindWrite();
    }

    public NativeImage finishDownload(int width, int height) {
        if (!this.isDownloading) {
            throw new IllegalStateException("Can't finish downloading before download has started");
        }
        this.isDownloading = false;

        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, this.pboId);
        ByteBuffer buffer = GL30C.glMapBuffer(GL30C.GL_PIXEL_PACK_BUFFER, GL30C.GL_READ_ONLY);

        if (buffer == null) {
            throw new IllegalStateException("OpenGL error occurred while mapping buffer");
        }

        // Copy bytes
        MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), nativeImage.pixels, nativeImage.size);

        GL30C.glUnmapBuffer(GL30C.GL_PIXEL_PACK_BUFFER);
        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);

        return nativeImage;
    }

    public void close() {
        if (this.pboId != -1) {
            GL30C.glDeleteBuffers(this.pboId);
            this.pboId = -1;
        }
    }

}
