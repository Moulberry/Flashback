package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.platform.NativeImage;
import io.humble.ferry.Buffer;
import io.humble.ferry.JNIReference;
import io.humble.video.Codec;
import io.humble.video.Encoder;
import io.humble.video.MediaPacket;
import io.humble.video.MediaPicture;
import io.humble.video.MediaPictureResampler;
import io.humble.video.Muxer;
import io.humble.video.MuxerFormat;
import io.humble.video.PixelFormat;
import io.humble.video.Rational;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class AsyncVideoEncoder {

    private final Muxer muxer;
    private final Encoder encoder;
    private final MediaPictureResampler resampler;
    private final MediaPacket packet;
    private final ArrayBlockingQueue<NativeImage> inputQueue;
    private final AtomicBoolean shouldEnd = new AtomicBoolean(false);
    private final AtomicBoolean finishedWriting = new AtomicBoolean(false);

    public AsyncVideoEncoder(int fps) {
        this.inputQueue = new ArrayBlockingQueue<>(32);
        final Rational framerate = Rational.make(1, fps);
        this.muxer = Muxer.make("new_output.mp4", null, "mp4");
        final MuxerFormat format = this.muxer.getFormat();
        Codec codec = Codec.findEncodingCodec(format.getDefaultVideoCodecId());
        this.encoder = Encoder.make(codec);

        this.encoder.setWidth(1920);
        this.encoder.setHeight(1060);
        final PixelFormat.Type pixelformat = PixelFormat.Type.PIX_FMT_YUV420P;
        this.encoder.setPixelFormat(pixelformat);
        this.encoder.setTimeBase(framerate);

        if (format.getFlag(MuxerFormat.Flag.GLOBAL_HEADER)) {
            this.encoder.setFlag(Encoder.Flag.FLAG_GLOBAL_HEADER, true);
        }

        this.encoder.open(null, null);
        this.muxer.addNewStream(this.encoder);
        try {
            this.muxer.open(null, null);
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        this.resampler = MediaPictureResampler.make(
            this.encoder.getWidth(), this.encoder.getHeight(), pixelformat,
            this.encoder.getWidth(), this.encoder.getHeight(), PixelFormat.Type.PIX_FMT_RGBA, 0);
        this.resampler.open();

        this.packet = MediaPacket.make();

        record Pictures(MediaPicture picture, MediaPicture pictureRgba) {}

        ArrayBlockingQueue<Pictures> pictureInputQueue = new ArrayBlockingQueue<>(32);
        ArrayBlockingQueue<Pictures> pictureOutputQueue = new ArrayBlockingQueue<>(32);

        for (int i = 0; i < 32; i++) {
            MediaPicture picture = MediaPicture.make(
                this.encoder.getWidth(),
                this.encoder.getHeight(),
                pixelformat
            );
            picture.setTimeBase(framerate);

            MediaPicture pictureRgba = MediaPicture.make(
                this.encoder.getWidth(),
                this.encoder.getHeight(),
                PixelFormat.Type.PIX_FMT_RGBA
            );

            pictureInputQueue.add(new Pictures(picture, pictureRgba));
        }

        AtomicBoolean shouldEndEncoderThread = new AtomicBoolean(false);

        new Thread(() -> {
            int currentFrame = 0;

            while (true) {
                try {
                    Pictures pictures = pictureInputQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (pictures == null) {
                        if (this.shouldEnd.get()) {
                            shouldEndEncoderThread.set(true);
                            return;
                        } else {
                            continue;
                        }
                    }

                    NativeImage src = this.inputQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (src == null) {
                        if (this.shouldEnd.get()) {
                            shouldEndEncoderThread.set(true);
                            return;
                        } else {
                            pictureInputQueue.put(pictures);
                            continue;
                        }
                    }

                    final AtomicReference<JNIReference> ref = new AtomicReference<JNIReference>(
                        null);
                    Buffer buffer = pictures.pictureRgba.getData(0);
                    int size = pictures.pictureRgba.getDataPlaneSize(0);
                    ByteBuffer pictureByteBuffer = buffer.getByteBuffer(0,
                        size, ref);
                    buffer.delete();

                    pictureByteBuffer.order(ByteOrder.nativeOrder());
                    for (int pixel : src.getPixelsRGBA()) {
                        pictureByteBuffer.putInt(pixel);
                    }
                    src.close();

                    pictures.pictureRgba.setTimeStamp(currentFrame++);
                    pictures.pictureRgba.setComplete(true);

                    this.resampler.resample(pictures.picture, pictures.pictureRgba);

                    if (ref.get() != null) {
                        ref.get().delete();
                    }

                    pictureOutputQueue.put(pictures);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        new Thread(() -> {
            while (true) {
                try {
                    Pictures pictures = pictureOutputQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (pictures == null) {
                        if (shouldEndEncoderThread.get()) {
                            do {
                                this.encoder.encode(this.packet, null);
                                if (this.packet.isComplete()) {
                                    this.muxer.write(this.packet,  false);
                                }
                            } while (this.packet.isComplete());

                            this.muxer.close();

                            this.muxer.delete();
                            this.encoder.delete();
                            this.resampler.delete();
                            this.packet.delete();
                            this.finishedWriting.set(true);

                            pictures = pictureInputQueue.poll();
                            while (pictures != null) {
                                pictures.picture.delete();
                                pictures.pictureRgba.delete();
                                pictures = pictureInputQueue.poll();
                            }
                            return;
                        } else {
                            continue;
                        }
                    }

                    do {
                        this.encoder.encode(this.packet, pictures.picture);
                        if (this.packet.isComplete()) {
                            this.muxer.write(this.packet, false);
                        }
                    } while (this.packet.isComplete());

                    if (shouldEndEncoderThread.get()) {
                        pictures.picture.delete();
                        pictures.pictureRgba.delete();
                    } else {
                        pictureInputQueue.put(pictures);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void encode(NativeImage src) {
        while (true) {
            try {
                this.inputQueue.put(src);
                return;
            } catch (InterruptedException ignored) {}
        }
    }

    public void finish() {
        while (!this.inputQueue.isEmpty()) {
            LockSupport.parkNanos("waiting for image queue to empty", 100000L);
        }

        this.shouldEnd.set(true);

        while (!this.finishedWriting.get()) {
            LockSupport.parkNanos("waiting for encoder thread to finish", 100000L);
        }
    }

}
