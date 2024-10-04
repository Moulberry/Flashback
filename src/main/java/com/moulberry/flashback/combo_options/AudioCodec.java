package com.moulberry.flashback.combo_options;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecHWConfig;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacpp.Pointer;

import java.util.ArrayList;
import java.util.List;

public enum AudioCodec implements ComboOption {

    AAC("AAC", avcodec.AV_CODEC_ID_AAC),
    // FLAC("FLAC", avcodec.AV_CODEC_ID_FLAC), // Removed because it doesn't support fltp sample format
    MP3("MP3", avcodec.AV_CODEC_ID_MP3),
    OPUS("Opus", avcodec.AV_CODEC_ID_OPUS),
    VORBIS("Vorbis", avcodec.AV_CODEC_ID_VORBIS);

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
