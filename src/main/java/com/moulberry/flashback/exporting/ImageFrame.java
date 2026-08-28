package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import org.bytedeco.ffmpeg.global.avutil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class ImageFrame implements AutoCloseable {

    public void copyRect(ImageFrame target, int fromX, int fromY, int toX, int toY, int sizeX, int sizeY) {
        if (this.format != target.format) {
            throw new IllegalArgumentException("target must have same format: this.format = " + this.format + ", target.format = " + target.format);
        }
        if (fromX < 0) throw new IllegalArgumentException("fromX < 0");
        if (toX < 0) throw new IllegalArgumentException("toX < 0");
        if (fromY < 0) throw new IllegalArgumentException("fromY < 0");
        if (toY < 0) throw new IllegalArgumentException("toY < 0");
        if (fromX + sizeX > this.width) throw new IllegalArgumentException("fromX + sizeX > this.width");
        if (fromY + sizeY > this.height) throw new IllegalArgumentException("fromY + sizeY > this.height");
        if (toX + sizeX > target.width) throw new IllegalArgumentException("toX + sizeX > target.width");
        if (toY + sizeY > target.height) throw new IllegalArgumentException("toY + sizeY > target.height");

        for (int y = 0; y < sizeY; y++) {
            long fromOffset = (fromX + (fromY+y) * (long) this.width) * this.format.bytes();
            long toOffset = (toX + (toY+y) * (long) target.width) * target.format.bytes();
            MemoryUtil.memCopy(this.pixels + fromOffset, target.pixels + toOffset, (long) sizeX * this.format.bytes());
        }
    }

    public void copyPixel(ImageFrame target, int fromX, int fromY, int toX, int toY) {
        if (this.format != target.format) {
            throw new IllegalArgumentException("target must have same format: this.format = " + this.format + ", target.format = " + target.format);
        }
        long fromOffset = (fromX + fromY * (long) this.width) * this.format.bytes();
        long toOffset = (toX + toY * (long) target.width) * target.format.bytes();
        MemoryUtil.memCopy(this.pixels + fromOffset, target.pixels + toOffset, this.format.bytes());
    }

    public NativeImage toOpaqueRgbaU8NativeImage() {
        NativeImage copy = new NativeImage(NativeImage.Format.RGBA, this.width, this.height, true);
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                long fromOffset = (x + y * (long) this.width) * this.format.bytes();
                int rgba = this.format.toOpaqueRgbaU8(this.pixels + fromOffset);
                copy.setPixelABGR(x, y, rgba);
            }
        }
        return copy;
    }

    public void makeOpaque() {
        int bytes = this.format.bytes();
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                long fromOffset = (x + y * (long) this.width) * bytes;
                this.format.makeOpaque(this.pixels + fromOffset);
            }
        }
    }

    public enum Format {
        RGBA_U8,
        GRAY_F32,
        CUSTOM;

        public void makeOpaque(long ptr) {
            switch (this) {
                case RGBA_U8 -> MemoryUtil.memPutInt(ptr, MemoryUtil.memGetInt(ptr) | 0xFF000000);
                case GRAY_F32 -> {}
                case CUSTOM -> throw new UnsupportedOperationException();
            }
        }

        public int toOpaqueRgbaU8(long mem) {
            return switch (this) {
                case RGBA_U8 -> MemoryUtil.memGetInt(mem) | 0xFF000000;
                case GRAY_F32 -> {
                    int gray = (int)(Math.max(0.0f, Math.min(1.0f, MemoryUtil.memGetFloat(mem))) * 255);
                    yield 0x10101 * gray | 0xFF000000;
                }
                case CUSTOM -> throw new UnsupportedOperationException();
            };
        }

        public int channels() {
            return switch (this) {
                case RGBA_U8 -> 4;
                case GRAY_F32 -> 1;
                case CUSTOM -> throw new UnsupportedOperationException();
            };
        }

        public int bytesPerChannel() {
            return switch (this) {
                case RGBA_U8 -> 1;
                case GRAY_F32 -> 4;
                case CUSTOM -> throw new UnsupportedOperationException();
            };
        }

        public int bytes() {
            return this.bytesPerChannel() * this.channels();
        }

        private int ffmpegPixelFormat() {
            return switch (this) {
                case RGBA_U8 -> avutil.AV_PIX_FMT_RGBA;
                case GRAY_F32 -> avutil.AV_PIX_FMT_GRAYF32;
                case CUSTOM -> throw new UnsupportedOperationException();
            };
        }
    }

    public final int width;
    public final int height;
    public final long size;
    public long pixels;
    public final Format format;
    public @Nullable FloatBuffer audioBuffer;
    private final int customFFmpegPixelFormat;

    public int ffmpegPixelFormat() {
        if (this.format == Format.CUSTOM) {
            return this.customFFmpegPixelFormat;
        } else {
            return this.format.ffmpegPixelFormat();
        }
    }

    public ImageFrame(int width, int height, Format format, boolean zero) {
        this.width = width;
        this.height = height;
        this.size = (long) width * (long) height * format.bytes();
        if (zero) {
            this.pixels = MemoryUtil.nmemCalloc(this.size, 1);
        } else {
            this.pixels = MemoryUtil.nmemAlloc(this.size);
        }
        this.format = format;
        this.customFFmpegPixelFormat = 0;
    }

    public ImageFrame(long src, int width, int height, Format format) {
        this.width = width;
        this.height = height;
        this.size = (long) width * (long) height * format.bytes();
        this.pixels = MemoryUtil.nmemAlloc(this.size);
        this.format = format;
        this.customFFmpegPixelFormat = 0;
        MemoryUtil.memCopy(src, this.pixels, this.size);
    }

    public ImageFrame(long pixels, int width, int height, int size, int customFFmpegPixelFormat, @Nullable FloatBuffer audioBuffer) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.size = size;
        this.format = Format.CUSTOM;
        this.customFFmpegPixelFormat = customFFmpegPixelFormat;
        this.audioBuffer = audioBuffer;
    }

    @Override
    public void close() {
        if (this.pixels != 0L) {
            MemoryUtil.nmemFree(this.pixels);
        }
        this.pixels = 0L;
    }
}
