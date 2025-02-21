package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.combo_options.AudioCodec;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVPixFmtDescriptor;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;

public class AsyncFFmpegVideoWriter implements AutoCloseable, VideoWriter {

    @Nullable
    private final ArrayBlockingQueue<ImageFrame> rescaleQueue;
    private final ArrayBlockingQueue<ImageFrame> encodeQueue;

    @Nullable
    private final ArrayBlockingQueue<Long> reusePictureData;

    private final AtomicBoolean finishRescaleThread = new AtomicBoolean(false);
    private final AtomicBoolean finishEncodeThread = new AtomicBoolean(false);
    private final AtomicBoolean finishedWriting = new AtomicBoolean(false);

    private final AtomicReference<Throwable> threadedError = new AtomicReference<>(null);

    private record ImageFrame(long pointer, int size, int width, int height, int channels, int imageDepth, int stride, int pixelFormat,
                              @Nullable FloatBuffer audioBuffer) implements AutoCloseable {
        public void close() {
            MemoryUtil.nmemFree(this.pointer);
        }
    }

    public AsyncFFmpegVideoWriter(ExportSettings settings, String filename) {
        int width = settings.resolutionX();
        int height = settings.resolutionY();

        final int maxResolutionArea = 3840 * 2160;
        if (width*height > maxResolutionArea) {
            double factor = (width*height) / (double) maxResolutionArea;
            factor = Math.sqrt(factor);
            width = (int) Math.floor(width / factor);
            height = (int) Math.floor(height / factor);
        }

        int maxBitrate = Math.min(288_000_000, 5000 + (int) Math.ceil(width * height * settings.framerate()));

        if (settings.encoder().equals("libsvtav1")) {
            maxBitrate = Math.min(100_000_000, maxBitrate);
        }

        int bitrate;
        if (settings.bitrate() <= 0) {
            bitrate = maxBitrate;
        } else {
            bitrate = Math.min(settings.bitrate(), maxBitrate);
        }
        double fps = settings.framerate();

        String extension = settings.container().extension();

        try {
            FFmpegLogCallback.set();

            boolean wantTransparency = settings.transparent();

            int dstPixelFormat = PixelFormatHelper.getBestPixelFormat(settings.encoder(), wantTransparency);
            Flashback.LOGGER.info("Encoding video with pixel format {}", PixelFormatHelper.pixelFormatToString(dstPixelFormat));
            boolean needsRescale = ExportJob.SRC_PIXEL_FORMAT != dstPixelFormat;

            int audioChannels = 0;
            if (settings.recordAudio()) {
                if (settings.audioCodec() == AudioCodec.VORBIS || settings.stereoAudio()) {
                    audioChannels = 2;
                } else {
                    audioChannels = 1;
                }
            }

            final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(filename, width, height, audioChannels);

            recorder.setVideoBitrate(bitrate);
            recorder.setVideoCodec(settings.codec().codecId());
            recorder.setVideoCodecName(settings.encoder());
            recorder.setFormat(extension);
            recorder.setFrameRate(fps);
            recorder.setPixelFormat(dstPixelFormat);
            recorder.setGopSize((int) Math.max(20, Math.min(240, Math.ceil(fps * 2))));

            if (settings.recordAudio()) {
                recorder.setAudioCodec(settings.audioCodec().codecId());
                recorder.setSampleFormat(avutil.AV_SAMPLE_FMT_FLTP);
                recorder.setSampleRate(48000);
                recorder.setAudioBitrate(256000);
            }

            recorder.start();

            this.encodeQueue = new ArrayBlockingQueue<>(needsRescale ? 24 : 32);
            this.rescaleQueue = needsRescale ? new ArrayBlockingQueue<>(8) : null;
            this.reusePictureData = needsRescale ? new ArrayBlockingQueue<>(32) : null;

            Thread encodeThread = createEncodeThread(recorder);
            if (needsRescale) {
                Thread rescaleThread = createRescaleThread(width, height, dstPixelFormat);
                rescaleThread.start();
            }
            encodeThread.start();
        } catch (IOException e) {
            throw SneakyThrow.sneakyThrow(e);
        }
    }

    private @NotNull Thread createEncodeThread(FFmpegFrameRecorder recorder) {
        Thread encodeThread = new Thread(() -> {
            while (true) {
                ImageFrame src;

                try {
                    src = this.encodeQueue.poll(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw SneakyThrow.sneakyThrow(e);
                }

                try {
                    if (src == null) {
                        if (this.finishEncodeThread.get()) {
                            recorder.stop();
                            recorder.close();
                            this.finishedWriting.set(true);
                            return;
                        } else {
                            continue;
                        }
                    }

                    int size = src.height * src.stride * Frame.pixelSize(src.imageDepth);
                    ByteBuffer buffer = MemoryUtil.memByteBuffer(src.pointer, size);

                    recorder.recordImage(src.width, src.height, src.imageDepth, src.channels,
                            src.stride, src.pixelFormat, buffer);
                    if (src.audioBuffer != null) {
                        recorder.recordSamples(src.audioBuffer);
                    }

                    if (this.reusePictureData != null) {
                        if (this.reusePictureData.offer(src.pointer)) { // try adding to the reuse queue, ignore if full
                            src = null; // don't deallocate
                        }
                    }
                } catch (Throwable t) {
                    try {
                        recorder.release();
                    } catch (FFmpegFrameRecorder.Exception e) {
                        e.printStackTrace();
                    }
                    this.threadedError.set(t);
                    this.finishRescaleThread.set(true);
                    this.finishEncodeThread.set(true);
                    this.finishedWriting.set(true);
                    return;
                } finally {
                    if (src != null) {
                        src.close();
                    }
                }
            }
        });
        encodeThread.setName("Video Encode Thread");
        return encodeThread;
    }

    private Thread createRescaleThread(int dstWidth, int dstHeight, int dstPixelFormat) {
        int dstSize = av_image_get_buffer_size(dstPixelFormat, dstWidth, dstHeight, 1);
        int dstDepth = dstSize * 8 / dstWidth / dstHeight;
        int dstChannels;

        try (AVPixFmtDescriptor descriptor = av_pix_fmt_desc_get(dstPixelFormat)) {
            dstChannels = descriptor.nb_components();
        }

        AVFrame picture = avutil.av_frame_alloc();
        if (picture == null) {
            throw new RuntimeException("av_frame_alloc() error: Could not allocate picture.");
        }

        AVFrame tmp_picture = avutil.av_frame_alloc();
        if (tmp_picture == null) {
            throw new RuntimeException("av_frame_alloc() error: Could not allocate tmp_picture.");
        }

        PointerPointer<AVFrame> tmp_picture_ptr = new PointerPointer<>(tmp_picture);
        PointerPointer<AVFrame> picture_ptr = new PointerPointer<>(picture);

        Flashback.LOGGER.info("Rescaling to pixel format: {}", dstPixelFormat);

        boolean useItu709Colorspace = PixelFormatHelper.isYuvFormat(dstPixelFormat);

        Thread scaleThread = new Thread(() -> {
            SwsContext img_convert_ctx = null;

            while (true) {
                try (ImageFrame src = this.rescaleQueue.poll(10, TimeUnit.MILLISECONDS)) {
                    if (src == null) {
                        if (this.finishRescaleThread.get()) {
                            av_frame_free(picture);
                            av_frame_free(tmp_picture);
                            sws_freeContext(img_convert_ctx);
                            this.finishEncodeThread.set(true);
                            return;
                        } else {
                            continue;
                        }
                    }

                    img_convert_ctx = swscale.sws_getCachedContext(img_convert_ctx, src.width, src.height, src.pixelFormat,
                            dstWidth, dstHeight, dstPixelFormat, swscale.SWS_LANCZOS | swscale.SWS_ACCURATE_RND | swscale.SWS_FULL_CHR_H_INT,
                            null, null, (DoublePointer) null);
                    if (img_convert_ctx == null) {
                        throw new RuntimeException("sws_getCachedContext() error: Cannot initialize the conversion context.");
                    }

                    if (useItu709Colorspace) {
                        IntPointer coefficients = swscale.sws_getCoefficients(swscale.SWS_CS_ITU709);
                        swscale.sws_setColorspaceDetails(img_convert_ctx, coefficients, 1, coefficients, 0, 0, 1 << 16, 1 << 16);
                    }

                    BytePointer data = new BytePointer() {{
                        this.address = src.pointer;
                        this.position = 0;
                        this.limit = src.size;
                        this.capacity = src.size;
                    }};

                    Long tempPointerAddressLong = this.reusePictureData.poll();
                    if (tempPointerAddressLong == null) {
                        tempPointerAddressLong = MemoryUtil.nmemAlloc(dstSize);
                        if (tempPointerAddressLong == 0) {
                            throw new OutOfMemoryError();
                        }
                    }

                    // Bit of a hack to create a BytePointer for this library
                    long tempPointerAddress = tempPointerAddressLong;
                    BytePointer tempPointer = new BytePointer() {{
                        this.address = tempPointerAddress;
                        this.position = 0;
                        this.limit = dstSize;
                        this.capacity = dstSize;
                    }};

                    av_image_fill_arrays(tmp_picture_ptr, tmp_picture.linesize(), data, src.pixelFormat, src.width, src.height, 1);
                    av_image_fill_arrays(picture_ptr, picture.linesize(), tempPointer, dstPixelFormat, dstWidth, dstHeight, 1);

                    int step = src.stride * Math.abs(src.imageDepth) / 8;
                    tmp_picture.linesize(0, step);
                    tmp_picture.format(src.pixelFormat);
                    tmp_picture.width(src.width);
                    tmp_picture.height(src.height);

                    picture.format(dstPixelFormat);
                    picture.width(dstWidth);
                    picture.height(dstHeight);

                    swscale.sws_scale(img_convert_ctx, tmp_picture_ptr, tmp_picture.linesize(),
                            0, src.height, picture_ptr, picture.linesize());

                    this.encodeQueue.put(new ImageFrame(tempPointerAddress, dstSize, dstWidth, dstHeight, dstChannels, dstDepth,
                            dstWidth, dstPixelFormat, src.audioBuffer));
                } catch (Throwable t) {
                    try {
                        av_frame_free(picture);
                        av_frame_free(tmp_picture);
                        sws_freeContext(img_convert_ctx);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    this.threadedError.set(t);
                    this.finishRescaleThread.set(true);
                    this.finishEncodeThread.set(true);
                    this.finishedWriting.set(true);
                    return;
                }
            }
        });
        scaleThread.setName("Image Rescale Thread");
        return scaleThread;
    }

    private void checkEncodeError(@Nullable AutoCloseable closeable) {
        Throwable t = this.threadedError.get();
        if (t != null) {
            this.finishRescaleThread.set(true);
            this.finishEncodeThread.set(true);
            this.finishedWriting.set(true);

            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    Flashback.LOGGER.error("Error while trying to close passed AutoClosable", e);
                }
            }
            SneakyThrow.sneakyThrow(t);
        }
    }

    public void encode(NativeImage src, @Nullable FloatBuffer audioBuffer) {
        checkEncodeError(src);

        if (this.finishRescaleThread.get() || this.finishEncodeThread.get() || this.finishedWriting.get()) {
            src.close();
            throw new IllegalStateException("Cannot encode after finish()");
        }

        while (true) {
            try {
                ImageFrame imageFrame = new ImageFrame(src.pixels, (int) src.size, src.getWidth(), src.getHeight(),
                        4, Frame.DEPTH_INT, src.getWidth(), ExportJob.SRC_PIXEL_FORMAT, audioBuffer);
                if (this.rescaleQueue != null) {
                    this.rescaleQueue.put(imageFrame);
                } else {
                    this.encodeQueue.put(imageFrame);
                }
                break;
            } catch (InterruptedException ignored) {}
            checkEncodeError(src);
        }
    }

    public void finish() {
        checkEncodeError(null);

        if (this.rescaleQueue != null) {
            while (!this.rescaleQueue.isEmpty()) {
                checkEncodeError(null);
                LockSupport.parkNanos("waiting for rescale queue to empty", 100000L);
            }
        }
        while (!this.encodeQueue.isEmpty()) {
            checkEncodeError(null);
            LockSupport.parkNanos("waiting for encode queue to empty", 100000L);
        }

        this.finishRescaleThread.set(true);
        if (this.rescaleQueue == null) {
            this.finishEncodeThread.set(true);
        }

        while (!this.finishedWriting.get()) {
            LockSupport.parkNanos("waiting for encoder thread to finish", 100000L);
        }

        checkEncodeError(null);
    }

    @Override
    public void close() {
        if (this.rescaleQueue != null) {
            for (ImageFrame src : this.rescaleQueue) {
                src.close();
            }
        }
        for (ImageFrame src : this.encodeQueue) {
            src.close();
        }

        this.finishRescaleThread.set(true);
        this.finishEncodeThread.set(true);

        while (!this.finishedWriting.get()) {
            LockSupport.parkNanos("waiting for encoder thread to finish", 100000L);
        }

        if (this.reusePictureData != null) {
            for (Long address : this.reusePictureData) {
                if (address != null && address != 0) {
                    MemoryUtil.nmemFree(address);
                }
            }
        }
    }
}
