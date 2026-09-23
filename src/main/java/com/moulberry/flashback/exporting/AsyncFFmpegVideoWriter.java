package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.combo_options.AudioCodec;
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
import java.util.function.Consumer;

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

    private static final class ImageFrame implements AutoCloseable {
        private long pointer;
        private final int size;
        private final int width;
        private final int height;
        private final int channels;
        private final int imageDepth;
        private final int stride;
        private final int pixelFormat;
        private final @Nullable FloatBuffer audioBuffer;

        private ImageFrame(long pointer, int size, int width, int height, int channels, int imageDepth, int stride, int pixelFormat, @Nullable FloatBuffer audioBuffer) {
            this.pointer = pointer;
            this.size = size;
            this.width = width;
            this.height = height;
            this.channels = channels;
            this.imageDepth = imageDepth;
            this.stride = stride;
            this.pixelFormat = pixelFormat;
            this.audioBuffer = audioBuffer;
        }

        public void close() {
            if (this.pointer != 0L) {
                MemoryUtil.nmemFree(this.pointer);
            }
            this.pointer = 0L;
        }
    }

    public AsyncFFmpegVideoWriter(ExportSettings settings, String filename) {
        try {
            FFmpegLogCallback.set();

            boolean wantTransparency = settings.transparent();

            int dstPixelFormat;
            int[] hdrPixelFormats = {
                // libx265 / libsvtav1 report yuv420p10le while the NVENC encoders (hevc_nvenc / av1_nvenc)
                // report p010le, so every 10-bit format has to be accepted or NVENC looks like 8-bit only.
                avutil.AV_PIX_FMT_YUV420P10LE,
                avutil.AV_PIX_FMT_P010LE,
                avutil.AV_PIX_FMT_YUV420P12LE,
            };
            int hdrFormat = -1;
            if (HdrExportBridge.active()) {
                for (int candidate : hdrPixelFormats) {
                    if (PixelFormatHelper.supportsPixelFormat(settings.encoder(), candidate)) {
                        hdrFormat = candidate;
                        break;
                    }
                }
            }
            if (hdrFormat >= 0) {
                dstPixelFormat = hdrFormat;
                Flashback.LOGGER.info("HDR export: using pixel format {} for encoder {}", PixelFormatHelper.pixelFormatToString(hdrFormat), settings.encoder());
            } else {
                if (HdrExportBridge.active()) {
                    Flashback.LOGGER.warn("HDR export requested but encoder {} offers no 10-bit pixel format (tried yuv420p10le / p010le / yuv420p12le) - exporting 8-bit", settings.encoder());
                }
                dstPixelFormat = PixelFormatHelper.getBestPixelFormat(settings.encoder(), wantTransparency);
            }
            Flashback.LOGGER.info("Encoding video with pixel format {}", PixelFormatHelper.pixelFormatToString(dstPixelFormat));

            int width = settings.resolutionX();
            int height = settings.resolutionY();

            double scaleUpFactor = 1.0;
            double scaleDownFactor = 1.0;

            int minimumSize = EncoderQuirks.minimumFrameSize(settings.encoder());
            scaleUpFactor = Math.max(scaleUpFactor, (double) minimumSize / width);
            scaleUpFactor = Math.max(scaleUpFactor, (double) minimumSize / height);

            int maximumSize = EncoderQuirks.maximumFrameSize(settings.encoder());
            scaleDownFactor = Math.min(scaleDownFactor, (double) maximumSize / width);
            scaleDownFactor = Math.min(scaleDownFactor, (double) maximumSize / height);

            int maximumArea = EncoderQuirks.maximumFrameArea(settings.encoder());
            scaleDownFactor = Math.min(scaleDownFactor, Math.sqrt((double) maximumArea / (double) width / (double) height));

            if (scaleUpFactor != 1.0 && scaleDownFactor != 1.0) {
                width = Math.max(minimumSize, Math.min(maximumSize, width));
                height = Math.max(minimumSize, Math.min(maximumSize, height));
            } else if (scaleUpFactor != 1.0) {
                width = (int) Math.ceil(scaleUpFactor * width);
                height = (int) Math.ceil(scaleUpFactor * height);
            } else if (scaleDownFactor != 1.0) {
                width = (int) Math.floor(scaleDownFactor * width);
                height = (int) Math.floor(scaleDownFactor * height);
            }

            boolean needsRescale = ExportJob.SRC_PIXEL_FORMAT != dstPixelFormat || width != settings.resolutionX() || height != settings.resolutionY();

            // 288m is the hard cap of libopenh264. Some encoders e.g. h264_amf support up to 1.1b, but the quality is near identical
            int maxBitrate = (int) Math.min(288_000_000, 4096L + av_image_get_buffer_size(dstPixelFormat, width, height, 1) * 8L * settings.framerate());

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

            if (hdrFormat >= 0) {
                // Colour metadata for the HDR stream: BT.2020 primaries, PQ (ST 2084) transfer, non-constant
                // luminance matrix and full range. Passing them as codec options puts them on the stream
                // before the muxer writes the header, so the file is tagged as HDR without any post-processing.
                recorder.setVideoOption("color_primaries", "bt2020");
                recorder.setVideoOption("color_trc", "smpte2084");
                recorder.setVideoOption("colorspace", "bt2020nc");
                recorder.setVideoOption("color_range", "pc");
            }

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
                        boolean hdr = HdrExportBridge.active();
                        IntPointer coefficients = swscale.sws_getCoefficients(hdr ? swscale.SWS_CS_BT2020 : swscale.SWS_CS_ITU709);
                        swscale.sws_setColorspaceDetails(img_convert_ctx, coefficients, 1, coefficients, hdr ? 1 : 0, 0, 1 << 16, 1 << 16);
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

    /**
     * HDR path: {@code pointer} is a malloc'd buffer of 16-bit normalised RGBA (PQ-encoded) pixels,
     * width * height * 8 bytes. imageDepth 64 makes Flashback's own stride maths
     * (bytes = stride * depth / 8) come out as width * 8, which is what swscale needs for RGBA64.
     * Ownership of the buffer transfers to the encode pipeline (freed by ImageFrame.close()).
     */
    public void encodeHdr(long pointer, int width, int height, @Nullable FloatBuffer audioBuffer) {
        if (pointer == 0L) {
            return;
        }
        if (this.finishRescaleThread.get() || this.finishEncodeThread.get() || this.finishedWriting.get()) {
            MemoryUtil.nmemFree(pointer);
            throw new IllegalStateException("Cannot encode after finish()");
        }

        while (true) {
            ImageFrame imageFrame = new ImageFrame(pointer, width * height * 8, width, height,
                4, 64, width, avutil.AV_PIX_FMT_RGBA64LE, audioBuffer);
            try {
                if (this.rescaleQueue != null) {
                    this.rescaleQueue.put(imageFrame);
                } else {
                    this.encodeQueue.put(imageFrame);
                }
                break;
            } catch (InterruptedException ignored) {}
            checkEncodeError(imageFrame);
        }
    }

    public void encode(NativeImage src, @Nullable FloatBuffer audioBuffer) {
        checkEncodeError(src);

        if (this.finishRescaleThread.get() || this.finishEncodeThread.get() || this.finishedWriting.get()) {
            src.close();
            throw new IllegalStateException("Cannot encode after finish()");
        }

        while (true) {
            ImageFrame imageFrame = new ImageFrame(src.pixels, (int) src.size, src.getWidth(), src.getHeight(),
                4, Frame.DEPTH_INT, src.getWidth(), ExportJob.SRC_PIXEL_FORMAT, audioBuffer);
            try {
                if (this.rescaleQueue != null) {
                    this.rescaleQueue.put(imageFrame);
                } else {
                    this.encodeQueue.put(imageFrame);
                }
                break;
            } catch (InterruptedException ignored) {}
            checkEncodeError(imageFrame);
        }
    }

    public void finish(Consumer<String> wait) {
        checkEncodeError(null);

        if (this.rescaleQueue != null) {
            while (!this.rescaleQueue.isEmpty()) {
                checkEncodeError(null);
                LockSupport.parkNanos("waiting for rescale queue to empty", 100000L);
                wait.accept("rescale");
            }
        }

        while (!this.encodeQueue.isEmpty()) {
            checkEncodeError(null);
            LockSupport.parkNanos("waiting for encode queue to empty", 100000L);
            wait.accept("encode queue");
        }

        this.finishRescaleThread.set(true);
        if (this.rescaleQueue == null) {
            this.finishEncodeThread.set(true);
        }

        while (!this.finishedWriting.get()) {
            LockSupport.parkNanos("waiting for encoder thread to finish", 100000L);
            wait.accept("thread finish");
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
