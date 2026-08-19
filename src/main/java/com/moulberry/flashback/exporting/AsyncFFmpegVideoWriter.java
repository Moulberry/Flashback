package com.moulberry.flashback.exporting;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import com.moulberry.flashback.combo_options.AudioCodec;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVPixFmtDescriptor;
import org.bytedeco.ffmpeg.global.avcodec;
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

    private ExportSettings settings;
    private String filename;
    private boolean started = false;

    @Nullable
    private ArrayBlockingQueue<ImageFrame> rescaleQueue;
    private ArrayBlockingQueue<ImageFrame> encodeQueue;

    @Nullable
    private ArrayBlockingQueue<Long> reusePictureData;

    private final AtomicBoolean finishRescaleThread = new AtomicBoolean(false);
    private final AtomicBoolean finishEncodeThread = new AtomicBoolean(false);
    private final AtomicBoolean finishedWriting = new AtomicBoolean(false);

    private final AtomicReference<Throwable> threadedError = new AtomicReference<>(null);

    public AsyncFFmpegVideoWriter(ExportSettings settings, String filename) {
        this.settings = settings;
        this.filename = filename;
    }

    public void tryStart(int srcPixelFormat) {
        if (this.started) {
            return;
        }
        this.started = true;

        try {
            FFmpegLogCallback.set();

            boolean wantTransparency = settings.transparent();

            int dstPixelFormat = PixelFormatHelper.getBestPixelFormat(settings.encoder(), srcPixelFormat, wantTransparency);
            Flashback.LOGGER.info("Starting export. Container={}. Codec={}. Encoder={}, Format={}",
                settings.container().text(), settings.codec().text(),
                settings.encoder(), PixelFormatHelper.pixelFormatToString(dstPixelFormat));

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

            boolean needsRescale = srcPixelFormat != dstPixelFormat || width != settings.resolutionX() || height != settings.resolutionY();

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

            final FlashbackFFmpegFrameRecorder recorder = new FlashbackFFmpegFrameRecorder(this.filename, width, height, audioChannels);

            recorder.setVideoBitrate(bitrate);
            recorder.setVideoCodec(settings.codec().codecId());
            recorder.setVideoCodecName(settings.encoder());
            recorder.setFormat(extension);
            recorder.setFrameRate(fps);
            recorder.setPixelFormat(dstPixelFormat);
            recorder.setGopSize((int) Math.max(20, Math.min(240, Math.ceil(fps * 2))));

            if (settings.container().isImageSequence() && settings.pngSequenceFormat() == null) {
                recorder.setMuxerOption("update", "1");
            }
            if (settings.encoder().equals("exr")) {
                recorder.setVideoOption("compression", "zip1");
            }
            if (settings.bitrate() == 0) {
                if (settings.encoder().endsWith("_nvenc")) {
                    recorder.setVideoOption("preset", "p7");
                } else if (settings.encoder().endsWith("_amf")) {
                    recorder.setVideoOption("usage", "high_quality");
                    recorder.setVideoOption("quality", "quality");
                } else if (settings.encoder().equals("libx264")) {
                    recorder.setVideoOption("preset", "slower");
                }
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

    private @NotNull Thread createEncodeThread(FlashbackFFmpegFrameRecorder recorder) {
        Thread encodeThread = new Thread(() -> {
            while (true) {
                ImageFrame src;

                try {
                    src = this.encodeQueue.poll(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    continue;
                }

                try {
                    if (src == null) {
                        if (this.finishEncodeThread.get()) {
                            recorder.stop();
                            recorder.release();
                            this.finishedWriting.set(true);
                            return;
                        } else {
                            continue;
                        }
                    }

                    ByteBuffer buffer = MemoryUtil.memByteBuffer(src.pixels, (int) src.size);

                    recorder.recordImage(src.width, src.height, src.ffmpegPixelFormat(), buffer);
                    if (src.audioBuffer != null) {
                        recorder.recordSamples(src.audioBuffer);
                    }

                    if (this.reusePictureData != null) {
                        if (this.reusePictureData.offer(src.pixels)) { // try adding to the reuse queue, ignore if full
                            src = null; // don't deallocate
                        }
                    }
                } catch (Throwable t) {
                    try {
                        recorder.release();
                    } catch (FlashbackFFmpegFrameRecorder.Exception e) {
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

        Flashback.LOGGER.info("Rescaling to pixel format: {}", PixelFormatHelper.pixelFormatToString(dstPixelFormat));

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

                    img_convert_ctx = swscale.sws_getCachedContext(img_convert_ctx, src.width, src.height, src.ffmpegPixelFormat(),
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
                        this.address = src.pixels;
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

                    av_image_fill_arrays(tmp_picture_ptr, tmp_picture.linesize(), data, src.ffmpegPixelFormat(), src.width, src.height, 1);
                    av_image_fill_arrays(picture_ptr, picture.linesize(), tempPointer, dstPixelFormat, dstWidth, dstHeight, 1);

                    tmp_picture.format(src.ffmpegPixelFormat());
                    tmp_picture.width(src.width);
                    tmp_picture.height(src.height);

                    picture.format(dstPixelFormat);
                    picture.width(dstWidth);
                    picture.height(dstHeight);

                    swscale.sws_scale(img_convert_ctx, tmp_picture_ptr, tmp_picture.linesize(),
                            0, src.height, picture_ptr, picture.linesize());

                    this.encodeQueue.put(new ImageFrame(tempPointerAddress, dstWidth, dstHeight, dstSize, dstPixelFormat, src.audioBuffer));
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

    public void encode(ImageFrame src) {
        this.tryStart(src.ffmpegPixelFormat());

        checkEncodeError(src);

        if (this.finishRescaleThread.get() || this.finishEncodeThread.get() || this.finishedWriting.get()) {
            src.close();
            throw new IllegalStateException("Cannot encode after finish()");
        }

        while (true) {
            try {
                if (this.rescaleQueue != null) {
                    this.rescaleQueue.put(src);
                } else {
                    this.encodeQueue.put(src);
                }
                break;
            } catch (InterruptedException ignored) {}
            checkEncodeError(src);
        }
    }

    public void finish(Consumer<String> wait) {
        if (!this.started) {
            return;
        }

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
        if (!this.started) {
            return;
        }

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
