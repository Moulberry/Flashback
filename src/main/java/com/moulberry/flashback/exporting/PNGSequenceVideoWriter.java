package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.SneakyThrow;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class PNGSequenceVideoWriter implements VideoWriter {

    private final ExportSettings settings;
    private int sequenceNumber = 0;

    private final AtomicBoolean finishEncodeThread = new AtomicBoolean(false);
    private final AtomicBoolean finishedWriting = new AtomicBoolean(false);

    private final AtomicReference<Throwable> threadedError = new AtomicReference<>(null);

    private final ArrayBlockingQueue<NativeImage> encodeQueue;

    public PNGSequenceVideoWriter(ExportSettings settings) {
        this.settings = settings;
        this.encodeQueue = new ArrayBlockingQueue<>(32);

        createEncodeThread().start();
    }

    private Thread createEncodeThread() {
        boolean outputIsDirectory = Files.isDirectory(this.settings.output());
        boolean encodeMultiple = outputIsDirectory || this.settings.startTick() != this.settings.endTick();

        Thread encodeThread = new Thread(() -> {
            while (true) {
                NativeImage src;

                try {
                    src = this.encodeQueue.poll(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw SneakyThrow.sneakyThrow(e);
                }

                try {
                    if (src == null) {
                        if (this.finishEncodeThread.get()) {
                            this.finishedWriting.set(true);
                            return;
                        } else {
                            continue;
                        }
                    }

                    // Ensure pixels are fully opaque if transparency is disabled
                    if (!this.settings.transparent() && src.format().hasAlpha()) {
                        int alpha = 255 << src.format().alphaOffset();
                        for (int y = 0; y < src.getHeight(); y++) {
                            for (int x = 0; x < src.getWidth(); x++) {
                                src.setPixel(x, y, src.getPixel(x, y) | alpha);
                            }
                        }
                    }

                    this.sequenceNumber += 1;

                    Path output = this.settings.output();
                    if (encodeMultiple) {
                        String format = this.settings.pngSequenceFormat();
                        if (format == null) {
                            format = "%04d";
                        }
                        if (outputIsDirectory) {
                            String filename;
                            try {
                                filename = String.format(format, this.sequenceNumber);
                            } catch (Exception e) {
                                filename = String.format("%04d", this.sequenceNumber);
                            }
                            if (!filename.endsWith(".png")) {
                                filename += ".png";
                            }
                            src.writeToFile(output.resolve(filename));
                        } else {
                            String filename = output.getFileName().toString() + "-";
                            try {
                                filename += String.format(format, this.sequenceNumber);
                            } catch (Exception e) {
                                filename += String.format("%04d", this.sequenceNumber);
                            }
                            if (!filename.endsWith(".png")) {
                                filename += ".png";
                            }
                            src.writeToFile(output.getParent().resolve(filename));
                        }
                    } else {
                        src.writeToFile(this.settings.output());
                    }
                } catch (Throwable t) {
                    this.threadedError.set(t);
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

    private void checkEncodeError(@Nullable AutoCloseable closeable) {
        Throwable t = this.threadedError.get();
        if (t != null) {
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
        if (audioBuffer != null) {
            throw new RuntimeException("PNG Sequence does not support encoding audio");
        }

        checkEncodeError(src);

        if (this.finishEncodeThread.get() || this.finishedWriting.get()) {
            src.close();
            throw new IllegalStateException("Cannot encode after finish()");
        }

        while (true) {
            try {
                this.encodeQueue.put(src);
                break;
            } catch (InterruptedException ignored) {}
            checkEncodeError(src);
        }
    }

    public void finish() {
        checkEncodeError(null);

        while (!this.encodeQueue.isEmpty()) {
            checkEncodeError(null);
            LockSupport.parkNanos("waiting for encode queue to empty", 100000L);
        }

        this.finishEncodeThread.set(true);

        while (!this.finishedWriting.get()) {
            LockSupport.parkNanos("waiting for encoder thread to finish", 100000L);
        }

        checkEncodeError(null);
    }

    @Override
    public void close() {
        for (NativeImage src : this.encodeQueue) {
            src.close();
        }

        this.finishEncodeThread.set(true);

        while (!this.finishedWriting.get()) {
            LockSupport.parkNanos("waiting for encoder thread to finish", 100000L);
        }
    }

}
