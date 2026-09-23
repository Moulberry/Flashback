package com.moulberry.flashback.combo_options;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecHWConfig;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Pointer;

import java.util.ArrayList;
import java.util.List;

public enum AudioCodec implements ComboOption {

    AAC("AAC", avcodec.AV_CODEC_ID_AAC),
    // FLAC("FLAC", avcodec.AV_CODEC_ID_FLAC), // Removed because it doesn't support fltp sample format
    MP3("MP3", avcodec.AV_CODEC_ID_MP3),
    OPUS("Opus", avcodec.AV_CODEC_ID_OPUS),
    VORBIS("Vorbis", avcodec.AV_CODEC_ID_VORBIS),
    // Lossless codecs. The four entries above keep their ordinals (appended at the end).
    // FLAC/ALAC/PCM encoders reject the fltp sample format the others use - which is why the
    // FLAC line above is commented out - so sampleFormat() below returns a format each of them
    // actually supports.
    FLAC("FLAC", avcodec.AV_CODEC_ID_FLAC),
    ALAC("ALAC", avcodec.AV_CODEC_ID_ALAC),
    PCM_S16LE("PCM 16-bit", avcodec.AV_CODEC_ID_PCM_S16LE),
    PCM_S24LE("PCM 24-bit", avcodec.AV_CODEC_ID_PCM_S24LE),
    PCM_S32LE("PCM 32-bit", avcodec.AV_CODEC_ID_PCM_S32LE),
    PCM_F32LE("PCM float32", avcodec.AV_CODEC_ID_PCM_F32LE);

    private final String text;
    private final int codecId;
    private String[] encoders;

    AudioCodec(String text, int codecId) {
        this.text = text;
        this.codecId = codecId;
    }

    @Override
    public String text() {
        return this.text;
    }

    public int codecId() {
        return this.codecId;
    }

    /**
     * Sample format handed to the ffmpeg encoder. Lossless encoders reject fltp, so each one gets
     * a format it accepts; ffmpeg resamples the captured float samples into it.
     */
    public int sampleFormat() {
        return switch (this) {
            case FLAC, PCM_S24LE, PCM_S32LE -> avutil.AV_SAMPLE_FMT_S32;
            case ALAC -> avutil.AV_SAMPLE_FMT_S32P;
            case PCM_S16LE -> avutil.AV_SAMPLE_FMT_S16;
            case PCM_F32LE -> avutil.AV_SAMPLE_FMT_FLT;
            default -> avutil.AV_SAMPLE_FMT_FLTP;
        };
    }

    public String[] getEncoders() {
        if (this.encoders == null) {
            List<String> encodersHardware = new ArrayList<>();
            List<String> encodersHybrid = new ArrayList<>();
            List<String> encodersSoftware = new ArrayList<>();
            List<String> encodersAvoid = new ArrayList<>();

            try (Pointer pointer = new Pointer()) {
                while (true) {
                    try (AVCodec codec = avcodec.av_codec_iterate(pointer)) {
                        if (codec == null) {
                            break;
                        } else if (codec.id() == this.codecId && avcodec.av_codec_is_encoder(codec) != 0) {
                            int capabilities = codec.capabilities();
                            String name = codec.name().getString();

                            if ((capabilities & avcodec.AV_CODEC_CAP_HARDWARE) != 0) {
                                encodersHardware.add(name);
                            } else if ((capabilities & avcodec.AV_CODEC_CAP_HYBRID) != 0 || codecHasHwConfig(codec)) {
                                encodersHybrid.add(name);
                            } else if ((capabilities & avcodec.AV_CODEC_CAP_EXPERIMENTAL) != 0) {
                                encodersAvoid.add(name);
                            } else {
                                encodersSoftware.add(name);
                            }
                        }
                    }
                }
            }

            List<String> encoders = new ArrayList<>();
            encoders.addAll(encodersHardware);
            encoders.addAll(encodersHybrid);
            encoders.addAll(encodersSoftware);
            encoders.addAll(encodersAvoid);

            this.encoders = encoders.toArray(new String[0]);
        }

        return this.encoders;
    }

    private static boolean codecHasHwConfig(AVCodec codec) {
        try (AVCodecHWConfig config = avcodec.avcodec_get_hw_config(codec, 0)) {
            return config != null;
        } catch (Throwable t) {
            return false;
        }
    }

}
