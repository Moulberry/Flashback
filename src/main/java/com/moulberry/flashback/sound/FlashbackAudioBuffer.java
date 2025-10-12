package com.moulberry.flashback.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.GlobalCleaner;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public class FlashbackAudioBuffer {

    public static FlashbackAudioBuffer EMPTY = new FlashbackAudioBuffer(48000, new byte[0], ByteBuffer.allocateDirect(0),
            new AudioFormat(48000, 8, 1, true, false));

    private static class InnerCleanableState implements Runnable {
        private SoundBuffer soundBuffer = null;

        @Override
        public void run() {
            if (this.soundBuffer == null) {
                return;
            }

            SoundBuffer soundBuffer = this.soundBuffer;

            Minecraft.getInstance().execute(() -> {
                Flashback.LOGGER.info("Releasing audio buffer data because it's no longer used");
                soundBuffer.discardAlBuffer();
            });

            this.soundBuffer = null;
        }
    }

    public final int sampleRate;
    private final ByteBuffer rawAudioData;
    private final AudioFormat format;
    private final byte[] waveform;
    private final InnerCleanableState innerState;

    private byte[] averagedWaveform = null;

    public FlashbackAudioBuffer(int sampleRate, byte[] waveform, ByteBuffer rawAudioData, AudioFormat audioFormat) {
        this.sampleRate = sampleRate;
        this.waveform = waveform;
        this.rawAudioData = rawAudioData;
        this.format = audioFormat;

        InnerCleanableState innerCleanableState = new InnerCleanableState();
        GlobalCleaner.INSTANCE.register(this, innerCleanableState);
        this.innerState = innerCleanableState;
    }

    public void invalidate(boolean discard) {
        SoundBuffer soundBuffer = this.innerState.soundBuffer;
        this.innerState.soundBuffer = null;

        if (discard && soundBuffer != null) {
            soundBuffer.discardAlBuffer();
        }
    }

    public SoundBuffer soundBuffer() {
        if (this.innerState.soundBuffer == null) {
            this.innerState.soundBuffer = new SoundBuffer(this.rawAudioData, this.format);
        }
        return this.innerState.soundBuffer;
    }

    public float durationInSeconds() {
        return this.waveform.length / (float) this.sampleRate;
    }

    public byte[] getAveragedWaveform(int length) {
        if (this.waveform.length == length) {
            return this.waveform;
        } else if (this.averagedWaveform != null && this.averagedWaveform.length == length) {
            return this.averagedWaveform;
        } else {
            this.averagedWaveform = new byte[length];

            if (length == 0) {
                return this.averagedWaveform;
            }
            if (length > this.waveform.length) {
                System.arraycopy(this.waveform, 0, this.averagedWaveform, 0, this.waveform.length);
                return this.averagedWaveform;
            }

            for (int i = 0; i < length; i++) {
                int from = (int)((long)i * this.waveform.length / length);
                int to = Math.min(this.waveform.length, (int)((long)(i+1) * this.waveform.length / length));

                if (to > from) {
                    long total = 0;
                    for (int j = from; j < to; j++) {
                        total += (int) this.waveform[j] - (int) Byte.MIN_VALUE;
                    }
                    this.averagedWaveform[i] = (byte)(total / (to - from) + Byte.MIN_VALUE);
                }
            }

            return this.averagedWaveform;
        }
    }

    @Nullable
    static FFmpegAudioReader.RawAudioData readRaw(Path path) {
        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            return FFmpegAudioReader.read(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    static FlashbackAudioBuffer load(Path path) {
        FFmpegAudioReader.RawAudioData rawData = readRaw(path);

        if (rawData == null) {
            return null;
        }

        ByteBuffer byteBuffer = rawData.byteBuffer();
        AudioFormat format = rawData.audioFormat();

        byteBuffer = byteBuffer.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

        int channels = format.getChannels();

        if (channels != 1 && channels != 2) {
            throw new RuntimeException("Unsupported channel count");
        }

        int bits = format.getSampleSizeInBits();
        if (bits != 8 && bits != 16) {
            throw new RuntimeException("Unsupported sample size");
        }

        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            throw new RuntimeException("Encoding must be PCM_SIGNED");
        }

        boolean stereo = channels == 2;
        boolean readShort = bits == 16;

        int sampleCount = byteBuffer.remaining() / format.getFrameSize();

        byte[] waveform = new byte[sampleCount];

        float maxAmplitude = 1;

        // Average over a 25ms window to avoid single outlier samples from
        // compressing the entire waveform
        int amplitudeSampleWindow = (int) Math.ceil(format.getSampleRate() / 40);

        while (byteBuffer.hasRemaining()) {
            long totalAmplitude = 0;
            int totalSamples = 0;
            for (int i = 0; i < amplitudeSampleWindow && byteBuffer.hasRemaining(); i++) {
                if (readShort) {
                    totalAmplitude += Math.abs(byteBuffer.getShort());
                } else {
                    totalAmplitude += Math.abs(byteBuffer.get());
                }
                totalSamples += 1;
            }
            float amplitudeOverWindow = (float)((double) totalAmplitude / totalSamples);
            maxAmplitude = Math.max(maxAmplitude, amplitudeOverWindow);
        }

        byteBuffer.position(0);

        for (int i = 0; i < sampleCount; i++) {
            if (readShort) {
                float v = Math.abs(byteBuffer.getShort()) / maxAmplitude;
                if (stereo) {
                    float other = Math.abs(byteBuffer.getShort()) / maxAmplitude;
                    v = (v + other)/2;
                }
                waveform[i] = (byte) (Math.max(0, Math.min(255, v * 255)) + Byte.MIN_VALUE);
            } else {
                float v = Math.abs(byteBuffer.get()) / maxAmplitude;
                if (stereo) {
                    float other = Math.abs(byteBuffer.get()) / maxAmplitude;
                    v = (v + other)/2;
                }
                waveform[i] = (byte) (Math.max(0, Math.min(255, v * 255)) + Byte.MIN_VALUE);
            }
        }

        byteBuffer.position(0);

        return new FlashbackAudioBuffer((int) format.getSampleRate(), waveform, byteBuffer, format);
    }

}
