package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class SaveableFramebuffer implements AutoCloseable {

    private MainTarget framebuffer;
    private int pboId;
    public @Nullable FloatBuffer audioBuffer;

    private boolean isDownloading = false;

    public SaveableFramebuffer() {
        this.framebuffer = null;
        this.pboId = -1;
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public MainTarget getFramebuffer(int width, int height) {
        if (this.isDownloading) {
            throw new IllegalStateException("Can't get the framebuffer for rendering while still downloading");
        }

        if (this.framebuffer == null) {
            this.framebuffer = new MainTarget(width, height);
        }

        return this.framebuffer;
    }

    public void startDownload(int width, int height) {
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

        this.framebuffer.bindWrite(true);

        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, this.pboId);
        GL30C.glReadPixels(0, 0, width, height, GL30C.GL_RGBA, GL30C.GL_UNSIGNED_BYTE, 0);
        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);

        this.framebuffer.unbindWrite();
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

//        for (int y = 0; y < nativeImage.getHeight(); ++y) {
//            for (int x = 0; x < nativeImage.getWidth(); ++x) {
//                int rgba = nativeImage.getPixelRGBA(x, y);
//                nativeImage.setPixelRGBA(x, y, (rgba & 0xFFFFFF) | (80 << NativeImage.Format.RGBA.alphaOffset()));
//            }
//        }

        nativeImage.flipY();

        GL30C.glUnmapBuffer(GL30C.GL_PIXEL_PACK_BUFFER);
        GL30C.glBindBuffer(GL30C.GL_PIXEL_PACK_BUFFER, 0);

        return nativeImage;
    }

    public void close() {
        if (this.framebuffer != null) {
            this.framebuffer.destroyBuffers();
            this.framebuffer = null;
        }
        if (this.pboId != -1) {
            GL30C.glDeleteBuffers(this.pboId);
            this.pboId = -1;
        }
    }

}
